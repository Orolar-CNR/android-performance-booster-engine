package com.example.systembooster.model

sealed interface OptimizationResult {
    data class Success(val executionTimeMs: Long) : OptimizationResult

    sealed interface Failure : OptimizationResult {
        data class PermissionDenied(val reason: String) : Failure
        data class Timeout(val elapsedMs: Long) : Failure
        data class Unsupported(val reason: String) : Failure
        data class ExecutionFailed(val exitCode: Int, val errorLog: String?) : Failure
        data class Cancelled(val message: String) : Failure
        data class InternalError(val exception: Throwable) : Failure
    }
}

data class OptimizationReport(
    val appLaunch: OptimizationResult,
    val network: OptimizationResult
)
