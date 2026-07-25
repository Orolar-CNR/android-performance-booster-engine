RFC-0001 — Core Runtime

Status: Draft
Category: Normative Specification
Scope: UI orchestration, concurrency coordination, engine execution, error isolation, and testable runtime boundaries

1. Abstract

This RFC defines the core runtime architecture for the Android Performance Booster Engine.

The system SHALL follow a strict three-layer structure:

- UI Layer — presentation and user interaction
- Coordinator Layer — concurrency orchestration and fault isolation
- Engine Layer — system command execution and platform interaction

The runtime is designed to be testable, deterministic at the orchestration boundary, and resilient to partial failures.

This document defines architecture and responsibilities only. It does not guarantee specific performance gains such as “+30% launch speed” or “+80% file transfer speed”; such figures MAY be treated as test targets, benchmarks, or demo claims, but not as normative guarantees.

---

2. Architectural Principles

The runtime SHALL enforce the following principles:

1. Separation of Concerns
   UI code MUST NOT execute shell commands directly.

2. Concurrency Isolation
   Independent optimization tasks SHOULD run in parallel when safe to do so.

3. Failure Containment
   Failure in one optimization task MUST NOT automatically crash the entire runtime.

4. Deterministic UI Recovery
   The UI MUST always return to an interactive state after optimization completes or fails.

5. Testability
   The coordinator layer MUST be independently unit-testable with mocked engine dependencies.

---

3. Layer Model

3.1 UI Layer

"MainActivity" is responsible only for:

- receiving user events
- updating visible state
- disabling/enabling controls during execution
- presenting success, partial success, and failure states

"MainActivity" MUST NOT:

- build shell commands
- manage process execution
- contain platform-specific optimization logic

3.2 Coordinator Layer

"SystemOptimizationCoordinator" is responsible for:

- coordinating multiple optimization jobs
- running jobs in parallel when appropriate
- isolating failures with "supervisorScope"
- returning a structured result to the UI layer

The coordinator MUST be the orchestration boundary between UI and engine.

3.3 Engine Layer

The engine layer contains platform execution units such as:

- "AppLaunchSpeedBooster"
- "NetworkSpeedBooster"

These components are responsible for:

- executing platform commands
- handling process boundaries
- interpreting command success/failure
- reporting result status to the coordinator

Engine components MUST NOT manipulate UI state.

---

4. Target Runtime Flow

The canonical flow is:

UI Layer
  ↓
Coordinator Layer
  ↓
Engine Layer
  ↓
System Command Execution
  ↓
Result Propagation
  ↓
UI Update

More specifically:

- "MainActivity" receives a user action
- "SystemOptimizationCoordinator" launches optimization jobs
- "AppLaunchSpeedBooster" performs application compilation optimization
- "NetworkSpeedBooster" performs network tuning operations
- results are aggregated
- UI state is updated once all jobs complete

---

5. Directory Structure

The repository SHOULD follow this structure:

rfcs/
└── rfc-0001-core-runtime.md

src/com/example/systembooster/
├── MainActivity.kt
└── engine/
    ├── SystemOptimizationCoordinator.kt
    ├── AppLaunchSpeedBooster.kt
    └── NetworkSpeedBooster.kt

test/com/example/systembooster/
└── MainActivityBoosterTest.kt

5.1 Notes on Naming

If the repository uses "RSC-0002" internally, that identifier SHOULD be normalized to "RFC-0002" for consistency with the rest of the specification tree unless there is a separate, documented naming system.

---

6. Concurrency Model

The coordinator SHALL support parallel execution of independent jobs.

Recommended model:

- "lifecycleScope.launch" for UI-bound invocation
- "supervisorScope" for task isolation
- "async" for parallel engine execution
- "await()" for deterministic result aggregation

A failure in one job MUST NOT cancel the sibling job unless explicitly configured.

---

7. Error Handling Contract

The runtime SHALL support three outcome classes:

7.1 Full Success

Both optimization jobs complete successfully.

7.2 Partial Success

Exactly one optimization job succeeds and the other fails or returns false.

7.3 Full Failure

Both optimization jobs fail, or the orchestration layer cannot complete execution.

The UI layer MUST reflect these states clearly and MUST re-enable user interaction in all cases.

---

8. Process Execution Contract

Engine components MAY use "ProcessBuilder" or equivalent process APIs.

The execution contract SHOULD follow these rules:

- Prefer bounded, explicit commands
- Log command output for traceability
- Return boolean or structured results from engine execution
- Avoid blocking the UI thread
- Handle missing permissions gracefully

If a privileged shell is unavailable, the engine MUST treat the operation as unsupported or failed, not as a fatal application crash.

---

9. Unit Testing Contract

The coordinator MUST be unit-testable without invoking real shell commands.

A proper test suite SHOULD cover at least these scenarios:

1. Full Success
   Both engine jobs return success.

2. Partial Success
   One engine job succeeds while the other throws or returns failure.

3. Full Failure
   Both engine jobs fail or throw exceptions.

Recommended test patterns:

- mock engine dependencies
- run coroutine tests with a test dispatcher
- verify coordinator return values
- verify UI state transitions separately from orchestration logic

The specification DOES NOT require “100% test success” as a runtime guarantee. It requires that the architecture be designed for complete unit coverage of the orchestration boundary.

---

10. Non-Goals

This RFC does NOT define:

- Vulkan rendering architecture
- kernel-level tuning guarantees
- Shizuku integration details
- memory leak detection tooling
- benchmark methodology
- iOS parity claims
- exact performance percentage claims

Those topics MAY appear in later RFCs or non-normative implementation notes.

---

11. Future Direction

RFC-0002 — Resource System Specification

The next phase MAY explore:

- advanced resource orchestration
- rendering pipeline optimization
- privilege mediation models
- automated integration testing
- memory footprint observability

Any future rendering, privilege, or system-access feature MUST be introduced as a separate RFC and MUST NOT be merged into the core runtime contract unless its boundary behavior is fully specified.

---

12. Implementation Summary

The intended implementation path is:

- "MainActivity" handles UI state only
- "SystemOptimizationCoordinator" handles parallel orchestration
- "AppLaunchSpeedBooster" handles app compilation optimization
- "NetworkSpeedBooster" handles network tuning commands
- tests validate success, partial failure, and total failure behavior

This is the canonical runtime boundary for RFC-0001.
