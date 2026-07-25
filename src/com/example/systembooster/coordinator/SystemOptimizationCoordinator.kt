package com.example.systembooster.coordinator

import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.engine.OptimizationEngine
import com.example.systembooster.model.EngineMetrics
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
    val appLaunchBooster: AppLaunchSpeedBooster,
    val networkBooster: NetworkSpeedBooster,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentPrivilege: PrivilegeLevel = PrivilegeLevel.ROOT_SU,
    private val telemetryService: TelemetryService? = null,
    private val logger: Logger = ConsoleLogger()
) {

    suspend fun runFullOptimization(
        packageName: String
    ): OptimizationReport = withContext(dispatcher) {
        val executionId = java.util.UUID.randomUUID().toString()
        logger.i("Coordinator", "Starting optimization session $executionId for package $packageName")

        supervisorScope {
            val appDeferred = async {
                appLaunchBooster.executionId = executionId
                var attemptsUsed = 0
                try {
                    if (packageName.isBlank()) {
                        val ex = IllegalArgumentException("targetPackageName must not be blank")
                        logger.e("Coordinator", "Blank package name provided", ex)
                        return@async OptimizationResult.Failure.InternalError(ex)
                    }
                    if (currentPrivilege.ordinal < appLaunchBooster.requiredPrivilege.ordinal) {
                        logger.w("Coordinator", "AppLaunchSpeedBooster required privilege ${appLaunchBooster.requiredPrivilege} not met by $currentPrivilege")
                        return@async OptimizationResult.Failure.PermissionDenied("Required privilege ${appLaunchBooster.requiredPrivilege} is not met")
                    }
                    logger.i("Coordinator", "Preparing AppLaunchSpeedBooster...")
                    appLaunchBooster.prepare()

                    logger.i("Coordinator", "Executing AppLaunchSpeedBooster...")
                    val startTime = System.currentTimeMillis()
                    val result = runWithRetries(appLaunchBooster) { att ->
                        attemptsUsed = att
                        appLaunchBooster.optimizeAppLaunch(packageName)
                    }
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime

                    val metrics = EngineMetrics(
                        executionTimeMs = duration,
                        retryCount = attemptsUsed - 1,
                        privilegeUsed = currentPrivilege,
                        memoryDeltaKb = 0L
                    )
                    telemetryService?.record(appLaunchBooster.id, executionId, metrics)

                    result
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        logger.w("Coordinator", "AppLaunchSpeedBooster cancelled")
                        OptimizationResult.Failure.Cancelled("Cancelled")
                    } else {
                        logger.e("Coordinator", "AppLaunchSpeedBooster failed with exception", e)
                        OptimizationResult.Failure.InternalError(e)
                    }
                } finally {
                    logger.i("Coordinator", "Cleaning up AppLaunchSpeedBooster...")
                    appLaunchBooster.cleanup()
                }
            }

            val networkDeferred = async {
                networkBooster.executionId = executionId
                var attemptsUsed = 0
                try {
                    if (currentPrivilege.ordinal < networkBooster.requiredPrivilege.ordinal) {
                        logger.w("Coordinator", "NetworkSpeedBooster required privilege ${networkBooster.requiredPrivilege} not met by $currentPrivilege")
                        return@async OptimizationResult.Failure.PermissionDenied("Required privilege ${networkBooster.requiredPrivilege} is not met")
                    }
                    logger.i("Coordinator", "Preparing NetworkSpeedBooster...")
                    networkBooster.prepare()

                    logger.i("Coordinator", "Executing NetworkSpeedBooster...")
                    val startTime = System.currentTimeMillis()
                    val result = runWithRetries(networkBooster) { att ->
                        attemptsUsed = att
                        networkBooster.applyNetworkOptimization()
                    }
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime

                    val metrics = EngineMetrics(
                        executionTimeMs = duration,
                        retryCount = attemptsUsed - 1,
                        privilegeUsed = currentPrivilege,
                        memoryDeltaKb = 0L
                    )
                    telemetryService?.record(networkBooster.id, executionId, metrics)

                    result
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        logger.w("Coordinator", "NetworkSpeedBooster cancelled")
                        OptimizationResult.Failure.Cancelled("Cancelled")
                    } else {
                        logger.e("Coordinator", "NetworkSpeedBooster failed with exception", e)
                        OptimizationResult.Failure.InternalError(e)
                    }
                } finally {
                    logger.i("Coordinator", "Cleaning up NetworkSpeedBooster...")
                    networkBooster.cleanup()
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

    private suspend fun runWithRetries(
        engine: OptimizationEngine,
        block: suspend (Int) -> OptimizationResult
    ): OptimizationResult {
        var attempt = 0
        val maxAttempts = 3
        var delayMs = 100L
        while (true) {
            attempt++
            val result = try {
                block(attempt)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                OptimizationResult.Failure.InternalError(e)
            }

            if (attempt >= maxAttempts) {
                return result
            }

            val shouldRetry = when (engine.retryPolicy) {
                RetryPolicy.ALWAYS -> true
                RetryPolicy.ON_TIMEOUT -> result is OptimizationResult.Failure.Timeout
                RetryPolicy.NEVER -> false
            }

            if (!shouldRetry) {
                return result
            }

            logger.w("Coordinator", "Engine ${engine.id} failed, retrying (attempt $attempt/$maxAttempts) after ${delayMs}ms...")
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
    }
}
