package com.example.systembooster.coordinator

import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.engine.OptimizationEngine
import com.example.systembooster.engine.EngineRegistry
import com.example.systembooster.engine.SimpleEngineRegistry
import com.example.systembooster.model.EngineCapability
import com.example.systembooster.model.EngineMetrics
import com.example.systembooster.model.EngineResult
import com.example.systembooster.model.FailureType
import com.example.systembooster.model.OptimizationResult
import com.example.systembooster.model.OptimizationReport
import com.example.systembooster.model.PrivilegeLevel
import com.example.systembooster.model.RetryPolicy
import com.example.systembooster.telemetry.TelemetryService
import com.example.systembooster.util.Logger
import com.example.systembooster.util.ConsoleLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

open class SystemOptimizationCoordinator(
    val registry: EngineRegistry,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentPrivilege: PrivilegeLevel = PrivilegeLevel.ROOT_SU,
    private val telemetryService: TelemetryService? = null,
    private val logger: Logger = ConsoleLogger()
) {

    val appLaunchBooster: AppLaunchSpeedBooster = (registry.getEnginesByCapability(EngineCapability.APP_COMPILATION).firstOrNull() as? AppLaunchSpeedBooster)
        ?: AppLaunchSpeedBooster()

    val networkBooster: NetworkSpeedBooster = (registry.getEnginesByCapability(EngineCapability.NETWORK).firstOrNull() as? NetworkSpeedBooster)
        ?: NetworkSpeedBooster()

    constructor(
        appLaunchBooster: AppLaunchSpeedBooster,
        networkBooster: NetworkSpeedBooster,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        currentPrivilege: PrivilegeLevel = PrivilegeLevel.ROOT_SU,
        telemetryService: TelemetryService? = null,
        logger: Logger = ConsoleLogger()
    ) : this(
        registry = SimpleEngineRegistry(listOf(appLaunchBooster, networkBooster)),
        dispatcher = dispatcher,
        currentPrivilege = currentPrivilege,
        telemetryService = telemetryService,
        logger = logger
    )

    suspend fun runFullOptimization(
        packageName: String
    ): OptimizationReport = withContext(dispatcher) {
        val executionId = java.util.UUID.randomUUID().toString()
        logger.i("Coordinator", "Starting optimization session $executionId for package $packageName")

        supervisorScope {
            val appEngine = registry.getEnginesByCapability(EngineCapability.APP_COMPILATION).firstOrNull()
            val networkEngine = registry.getEnginesByCapability(EngineCapability.NETWORK).firstOrNull()

            val appDeferred = async {
                if (appEngine != null) {
                    executeEngine(appEngine, packageName, executionId)
                } else {
                    OptimizationResult.Failure.Unsupported("No engine available for APP_COMPILATION")
                }
            }

            val networkDeferred = async {
                if (networkEngine != null) {
                    executeEngine(networkEngine, packageName, executionId)
                } else {
                    OptimizationResult.Failure.Unsupported("No engine available for NETWORK")
                }
            }

            val appResult = try {
                appDeferred.await()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    OptimizationResult.Failure.Cancelled("App launch execution cancelled")
                } else {
                    OptimizationResult.Failure.InternalError(e)
                }
            }

            val networkResult = try {
                networkDeferred.await()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    OptimizationResult.Failure.Cancelled("Network execution cancelled")
                } else {
                    OptimizationResult.Failure.InternalError(e)
                }
            }

            OptimizationReport(appLaunch = appResult, network = networkResult)
        }
    }

    private suspend fun executeEngine(
        engine: OptimizationEngine,
        packageName: String,
        executionId: String
    ): OptimizationResult {
        engine.executionId = executionId
        var attemptsUsed = 0

        // Verification of privilege requirements
        if (currentPrivilege.ordinal < engine.requiredPrivilege.ordinal) {
            val timestamp = System.currentTimeMillis()
            logger.w(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: $timestamp, Result type: PERMISSION_DENIED")
            return OptimizationResult.Failure.PermissionDenied("Required privilege ${engine.requiredPrivilege} is not met")
        }

        try {
            if (packageName.isBlank() && engine.capability == EngineCapability.APP_COMPILATION) {
                val ex = IllegalArgumentException("targetPackageName must not be blank")
                logger.e(engine.id, "[$executionId] Blank package name provided", ex)
                return OptimizationResult.Failure.InternalError(ex)
            }

            logger.i(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: ${System.currentTimeMillis()}, Result type: PREPARING")
            engine.prepare()

            logger.i(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: ${System.currentTimeMillis()}, Result type: EXECUTING")
            val startTime = System.currentTimeMillis()

            val engineResult = runWithRetries(engine, packageName) { att ->
                attemptsUsed = att
            }

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Record metrics via TelemetryService
            val finalMetrics = if (engineResult is EngineResult.Success) engineResult.metrics else (engineResult as EngineResult.Failure).metrics
            telemetryService?.record(engine.id, executionId, finalMetrics)

            val resultType = when (engineResult) {
                is EngineResult.Success -> "SUCCESS"
                is EngineResult.Failure -> engineResult.type.name
            }
            logger.i(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: ${System.currentTimeMillis()}, Result type: $resultType")

            return when (engineResult) {
                is EngineResult.Success -> OptimizationResult.Success(engineResult.metrics.executionTimeMs)
                is EngineResult.Failure -> {
                    when (engineResult.type) {
                        FailureType.PERMISSION_DENIED -> OptimizationResult.Failure.PermissionDenied(engineResult.reason)
                        FailureType.TIMEOUT -> OptimizationResult.Failure.Timeout(engineResult.metrics.executionTimeMs)
                        FailureType.UNSUPPORTED -> OptimizationResult.Failure.Unsupported(engineResult.reason)
                        FailureType.CANCELLED -> OptimizationResult.Failure.Cancelled(engineResult.reason)
                        FailureType.EXECUTION_ERROR -> OptimizationResult.Failure.ExecutionFailed(-1, engineResult.reason)
                        FailureType.UNKNOWN -> OptimizationResult.Failure.InternalError(Exception(engineResult.reason))
                    }
                }
            }
        } catch (e: Exception) {
            val resultType = if (e is CancellationException) "CANCELLED" else "UNKNOWN"
            logger.i(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: ${System.currentTimeMillis()}, Result type: $resultType")
            if (e is CancellationException) {
                logger.w(engine.id, "Engine execution cancelled")
                return OptimizationResult.Failure.Cancelled("Cancelled")
            } else {
                logger.e(engine.id, "Engine failed with exception", e)
                return OptimizationResult.Failure.InternalError(e)
            }
        } finally {
            logger.i(engine.id, "Engine ID: ${engine.id}, Execution ID: $executionId, Timestamp: ${System.currentTimeMillis()}, Result type: CLEANUP")
            engine.cleanup()
        }
    }

    private suspend fun runWithRetries(
        engine: OptimizationEngine,
        packageName: String,
        onAttempt: (Int) -> Unit
    ): EngineResult {
        var attempt = 0
        val maxAttempts = 3
        var delayMs = 100L
        while (true) {
            attempt++
            onAttempt(attempt)
            val result = try {
                engine.execute(packageName)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val metrics = EngineMetrics(
                    executionTimeMs = 0L,
                    retryCount = attempt - 1,
                    privilegeUsed = currentPrivilege,
                    memoryDeltaKb = 0L
                )
                EngineResult.Failure(metrics, FailureType.UNKNOWN, e.message ?: "Unknown error")
            }

            if (attempt >= maxAttempts) {
                return result
            }

            val shouldRetry = when (engine.retryPolicy) {
                RetryPolicy.ALWAYS -> true
                RetryPolicy.ON_TIMEOUT -> result is EngineResult.Failure && result.type == FailureType.TIMEOUT
                RetryPolicy.NEVER -> false
            }

            if (!shouldRetry) {
                return result
            }

            logger.w(engine.id, "Engine ${engine.id} failed with type ${if (result is EngineResult.Failure) result.type else "NONE"}, retrying (attempt $attempt/$maxAttempts) after ${delayMs}ms...")
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
    }
}
