package com.example.systembooster.model

enum class EngineCapability {
    APP_COMPILATION,
    NETWORK,
    MEMORY,
    STORAGE,
    GRAPHICS,
    POWER,
    FILESYSTEM
}

enum class PrivilegeLevel {
    UNPRIVILEGED,
    ADB_SHIZUKU,
    ROOT_SU
}

enum class RetryPolicy {
    NEVER,
    ON_TIMEOUT,
    ALWAYS
}

enum class FailureType {
    PERMISSION_DENIED,
    UNSUPPORTED,
    TIMEOUT,
    CANCELLED,
    EXECUTION_ERROR,
    UNKNOWN
}

data class EngineMetrics(
    val executionTimeMs: Long,
    val retryCount: Int,
    val privilegeUsed: PrivilegeLevel,
    val memoryDeltaKb: Long
)

sealed interface EngineResult {
    data class Success(
        val metrics: EngineMetrics,
        val outputLog: String
    ) : EngineResult

    data class Failure(
        val metrics: EngineMetrics,
        val type: FailureType,
        val reason: String
    ) : EngineResult
}
