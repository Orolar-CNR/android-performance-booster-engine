package com.example.systembooster

import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.engine.OptimizationOutcome
import com.example.systembooster.engine.SystemOptimizationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityBoosterTest {

    // Helper fake boosters
    private class FakeAppLaunchBooster(private val behavior: () -> Boolean) : AppLaunchSpeedBooster(listOf()) {
        override suspend fun boost(): Boolean = behavior()
    }

    private class FakeNetworkBooster(private val behavior: () -> Boolean) : NetworkSpeedBooster(listOf()) {
        override suspend fun boost(): Boolean = behavior()
    }

    @Test
    fun testFullSuccessScenario() = runTest {
        val appLaunch = FakeAppLaunchBooster { true }
        val network = FakeNetworkBooster { true }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.FullSuccess, outcome)

        val mainActivity = MainActivity(coordinator, this)
        assertEquals("Idle", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)

        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullSuccess, mainActivity.lastOutcome)
    }

    @Test
    fun testPartialSuccessScenario_AppLaunchSucceeds_NetworkFails() = runTest {
        val appLaunch = FakeAppLaunchBooster { true }
        val network = FakeNetworkBooster { false }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.PartialSuccess, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Partial Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.PartialSuccess, mainActivity.lastOutcome)
    }

    @Test
    fun testPartialSuccessScenario_AppLaunchFails_NetworkSucceeds() = runTest {
        val appLaunch = FakeAppLaunchBooster { false }
        val network = FakeNetworkBooster { true }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.PartialSuccess, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Partial Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
    }

    @Test
    fun testPartialSuccessScenario_NetworkThrowsException() = runTest {
        val appLaunch = FakeAppLaunchBooster { true }
        val network = FakeNetworkBooster { throw RuntimeException("Network tuning crashed") }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.PartialSuccess, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Partial Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
    }

    @Test
    fun testFullFailureScenario_BothFail() = runTest {
        val appLaunch = FakeAppLaunchBooster { false }
        val network = FakeNetworkBooster { false }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.FullFailure, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Failure", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullFailure, mainActivity.lastOutcome)
    }

    @Test
    fun testFullFailureScenario_BothThrow() = runTest {
        val appLaunch = FakeAppLaunchBooster { throw RuntimeException("Launch boost crash") }
        val network = FakeNetworkBooster { throw RuntimeException("Network boost crash") }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.FullFailure, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Failure", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullFailure, mainActivity.lastOutcome)
    }

    @Test
    fun testFullFailureScenario_OneFails_OneThrows() = runTest {
        val appLaunch = FakeAppLaunchBooster { false }
        val network = FakeNetworkBooster { throw RuntimeException("Network boost crash") }
        val coordinator = SystemOptimizationCoordinator(appLaunch, network)

        val outcome = coordinator.optimize()
        assertEquals(OptimizationOutcome.FullFailure, outcome)

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Failure", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
    }

    @Test
    fun testCoordinatorFailureDoesNotCrashUi() = runTest {
        val appLaunch = FakeAppLaunchBooster { true }
        val network = FakeNetworkBooster { true }
        val coordinator = object : SystemOptimizationCoordinator(appLaunch, network) {
            override suspend fun optimize(): OptimizationOutcome {
                throw IllegalStateException("Orchestration error")
            }
        }

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertTrue(mainActivity.currentUiState.startsWith("Error:"))
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullFailure, mainActivity.lastOutcome)
    }
}
