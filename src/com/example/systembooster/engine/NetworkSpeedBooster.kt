package com.example.systembooster.engine

import com.example.systembooster.model.EngineCapability
import com.example.systembooster.model.EngineMetrics
import com.example.systembooster.model.EngineResult
import com.example.systembooster.model.FailureType
import com.example.systembooster.model.OptimizationResult
import com.example.systembooster.model.PrivilegeLevel
import com.example.systembooster.model.RetryPolicy
import com.example.systembooster.util.Logger
import com.example.systembooster.util.ConsoleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

open class NetworkSpeedBooster(
    private val command: List<String> = listOf(
        "ping",
        "-c",
        "3",
        "8.8.8.8"
    ),
    private val logger: Logger = ConsoleLogger()
) : OptimizationEngine {

    override val id = "network_speed_booster"
    override val version = "2.0"
    override val displayName = "Network Speed Booster"
    override val capability = EngineCapability.NETWORK
    override val requiredPrivilege = PrivilegeLevel.UNPRIVILEGED
    override val retryPolicy = RetryPolicy.NEVER
    override var executionId: String? = null

    companion object {
        private const val TAG = "NetworkSpeedBooster"
        private const val PROCESS_TIMEOUT_SECONDS = 10L
    }

    override suspend fun prepare() {
        logger.i(TAG, "[$executionId] Preparing network speed booster")
    }

    override suspend fun cleanup() {
        logger.i(TAG, "[$executionId] Cleaning up network speed booster resources")
    }

    override suspend fun execute(targetPackage: String): EngineResult {
        val startTime = System.currentTimeMillis()
        val optResult = applyNetworkOptimization()
        val duration = System.currentTimeMillis() - startTime
        val metrics = EngineMetrics(
            executionTimeMs = duration,
            retryCount = 0,
            privilegeUsed = requiredPrivilege,
            memoryDeltaKb = 0L
        )
        return when (optResult) {
            is OptimizationResult.Success -> EngineResult.Success(metrics, "Success")
            is OptimizationResult.Failure.PermissionDenied -> EngineResult.Failure(metrics, FailureType.PERMISSION_DENIED, optResult.reason)
            is OptimizationResult.Failure.Timeout -> EngineResult.Failure(metrics, FailureType.TIMEOUT, "Timeout after ${optResult.elapsedMs}ms")
            is OptimizationResult.Failure.Unsupported -> EngineResult.Failure(metrics, FailureType.UNSUPPORTED, optResult.reason)
            is OptimizationResult.Failure.ExecutionFailed -> EngineResult.Failure(metrics, FailureType.EXECUTION_ERROR, optResult.errorLog ?: "Exit code ${optResult.exitCode}")
            is OptimizationResult.Failure.Cancelled -> EngineResult.Failure(metrics, FailureType.CANCELLED, optResult.message)
            is OptimizationResult.Failure.InternalError -> EngineResult.Failure(metrics, FailureType.UNKNOWN, optResult.exception.message ?: "Unknown error")
        }
    }

    open suspend fun applyNetworkOptimization(): OptimizationResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        val startTime = System.currentTimeMillis()

        try {
            logger.i(TAG, "[$executionId] Starting network tuning/verification...")
            logger.d(TAG, "[$executionId] Command: ${command.joinToString(" ")}")

            process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val stdoutDeferred = async(Dispatchers.IO) {
                try {
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
            }

            val stderrDeferred = async(Dispatchers.IO) {
                try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
            }

            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                logger.e(TAG, "[$executionId] Network process timeout")
                process.destroyForcibly()
                return@withContext OptimizationResult.Failure.Timeout(System.currentTimeMillis() - startTime)
            }

            val exitCode = process.exitValue()
            val stdout = stdoutDeferred.await().trim()
            val stderr = stderrDeferred.await().trim()

            if (stdout.isNotBlank()) {
                logger.d(TAG, "[$executionId] STDOUT:\n$stdout")
            }
            if (stderr.isNotBlank()) {
                logger.e(TAG, "[$executionId] STDERR:\n$stderr")
            }

            logger.i(TAG, "[$executionId] Finished with exit code = $exitCode")

            if (exitCode == 0) {
                OptimizationResult.Success(System.currentTimeMillis() - startTime)
            } else {
                OptimizationResult.Failure.ExecutionFailed(
                    exitCode = exitCode,
                    errorLog = buildString {
                        if (stderr.isNotBlank()) appendLine(stderr)
                        if (stdout.isNotBlank()) append(stdout)
                    }.trim().ifBlank { null }
                )
            }
        } catch (t: Throwable) {
            logger.e(TAG, "[$executionId] Network optimization failed", t)
            if (t is kotlinx.coroutines.CancellationException) {
                OptimizationResult.Failure.Cancelled("Cancelled")
            } else {
                OptimizationResult.Failure.InternalError(t)
            }
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
