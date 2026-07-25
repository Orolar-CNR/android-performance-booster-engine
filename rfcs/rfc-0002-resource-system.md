RFC-0002
Resource System Specification
Status: Approved
Category: Normative Technical Standard
Version: 2.0
Depends on: RFC-0001 — Core Runtime

1. Abstract
This specification defines the architectural contracts for the Resource System built on top of RFC-0001.
Its purpose is to standardize:
Engine contracts
Engine lifecycle
Capability modeling
Dynamic engine discovery
Privilege mediation
Structured telemetry
Retry and cancellation policies
Coordinator behavior

This document intentionally separates optimization logic from diagnostics to preserve modularity, maintainability, and long-term extensibility.

2. Design Goals
The Resource System SHALL satisfy the following objectives.
Extensible without Coordinator modifications
Strongly typed
Coroutine-safe
Testable
Observable
Privilege-aware
Platform-independent whenever possible

The architecture SHALL follow the Open/Closed Principle.
New optimization engines MUST be installable without modifying Coordinator logic.

3. Core Architecture
                UI Layer
                   │
                   ▼
         SystemOptimizationCoordinator
                   │
        ┌──────────┴───────────┐
        ▼                      ▼
  Engine Registry        Telemetry Service
        │
        ▼
  Optimization Engines

Responsibilities are strictly separated.
Layer
Responsibility
UI
Presentation only
Coordinator
Scheduling, lifecycle, retry
Registry
Engine discovery
Engine
System mutation
Telemetry
Metrics collection
Diagnostic
Read-only inspection

4. Capability Model
Every optimization engine MUST declare its capability.
enum class EngineCapability {
    APP_COMPILATION,
    NETWORK,
    MEMORY,
    STORAGE,
    GRAPHICS,
    POWER,
    FILESYSTEM
}
Coordinator MUST use capabilities instead of concrete engine types.

5. Engine Metadata
Every engine SHALL expose immutable metadata.
interface OptimizationEngine {
    val id: String
    val version: String
    val displayName: String
    val capability: EngineCapability
    val requiredPrivilege: PrivilegeLevel
    val retryPolicy: RetryPolicy
    suspend fun prepare()
    suspend fun execute(
        targetPackage: String
    ): EngineResult
    suspend fun cleanup()
}
Metadata SHALL NOT change during execution.

6. Engine Lifecycle
Every execution MUST follow the lifecycle below.
prepare()
↓
execute()
↓
cleanup()

Coordinator MUST invoke cleanup() regardless of execution outcome.
prepare()
↓
Success
↓
cleanup()

prepare()
↓
Failure
↓
cleanup()

prepare()
↓
Cancellation
↓
cleanup()

Cleanup MUST execute inside finally.

7. Execution Contracts
Coordinator SHALL guarantee:
Lifecycle ordering
Structured error propagation
Cancellation propagation
Retry handling
Telemetry recording

Coordinator MUST NOT perform optimization work itself.
Business logic SHALL remain inside engines.

8. Retry Policy
enum class RetryPolicy {
    NEVER,
    ON_TIMEOUT,
    ALWAYS
}
Coordinator SHALL interpret retry policy.
Retry count SHALL be configurable.
Retry delays SHOULD support exponential backoff.

9. Cancellation Contract
Every engine MUST support cooperative cancellation.
Examples include
ensureActive()
yield()
suspend functions
Long-running loops MUST periodically check cancellation status.

10. Privilege Model
enum class PrivilegeLevel {
    UNPRIVILEGED,
    ADB_SHIZUKU,
    ROOT_SU
}
Before execution, Coordinator SHALL verify privilege requirements.
If insufficient,
Engine MUST immediately return
Failure.PermissionDenied
instead of executing system commands.

11. Engine Result Model
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
Boolean return values SHALL NOT be used.

12. Failure Taxonomy
enum class FailureType {
    PERMISSION_DENIED,
    UNSUPPORTED,
    TIMEOUT,
    CANCELLED,
    EXECUTION_ERROR,
    UNKNOWN
}
Failure categories MUST remain stable across releases.

13. Engine Metrics
data class EngineMetrics(
    val executionTimeMs: Long,
    val retryCount: Int,
    val privilegeUsed: PrivilegeLevel,
    val memoryDeltaKb: Long
)
Metrics SHALL describe execution only.
They SHALL NOT contain private user information.

14. Telemetry Contract
Telemetry SHALL remain independent from logging.
Engine
↓
Telemetry
↓
Coordinator
↓
UI

Telemetry SHOULD support future exporters including
JSON
ProtoBuf
OpenTelemetry

15. Logging Contract
Each optimization session SHALL generate a unique execution identifier.
Execution ID
↓
Coordinator
↓
Engine
↓
Logs

Logs MUST contain
Engine ID
Execution ID
Timestamp
Result type

Logs MUST NOT contain
Personal data
File contents
User credentials
Package internals

16. Dynamic Engine Discovery
Coordinator MUST obtain engines through a registry.
interface EngineRegistry {
    fun registeredEngines():
        List<OptimizationEngine>
    fun getEnginesByCapability(
        capability: EngineCapability
    ): List<OptimizationEngine>
}
Coordinator SHALL NOT instantiate engines directly.

17. Parallel Execution Policy
Independent engines SHOULD execute concurrently.
Dependent engines MUST execute sequentially.
Coordinator SHALL use
supervisorScope
with
async
to isolate failures.
One engine failure MUST NOT cancel unrelated engines.

18. Optimization Boundary
Optimization engines are responsible only for
state mutation
system tuning
configuration changes
They MUST NOT collect diagnostics.

19. Diagnostic Boundary
Diagnostic components are read-only.
Examples include
FPS monitoring
Memory inspection
Battery statistics
Kernel information
Diagnostic modules SHALL never modify system state.

20. Testability Contract
Every engine MUST be mockable.
Coordinator MUST support dependency injection.
Unit tests SHALL verify
lifecycle order
retry
cancellation
cleanup
privilege handling
telemetry
failure mapping

Integration tests SHALL verify
concurrency
report aggregation
execution ordering

21. Security Considerations
The framework SHALL follow the principle of least privilege.
Optimization SHALL degrade gracefully when required privileges are unavailable.
No engine SHALL attempt privilege escalation.

22. Future Work
RFC-0003 will specify
Binder abstraction layer
Shizuku Provider
IPC contracts
Vulkan execution model
Scheduler extensions
Engine dependency graph
Service communication protocol
