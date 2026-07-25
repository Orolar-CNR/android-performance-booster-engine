package com.example.systembooster

import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.engine.OptimizationOutcome
import com.example.systembooster.engine.SystemOptimizationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityBoosterTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `optimize returns success when both coroutines succeed`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } returns true

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testDispatcher
        )

        val result = coordinator.optimize("com.instagram.android")

        assertTrue(result.launchResult)
        assertTrue(result.networkResult)
    }

    @Test
    fun `optimize returns partial success when network coroutine fails`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("permission denied")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testDispatcher
        )

        val result = coordinator.optimize("com.instagram.android")

        assertTrue(result.launchResult)
        assertFalse(result.networkResult)
    }

    @Test
    fun `optimize returns failure when both coroutines fail`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } throws RuntimeException("launch failed")
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("network failed")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testDispatcher
        )

        val result = coordinator.optimize("com.instagram.android")

        assertFalse(result.launchResult)
        assertFalse(result.networkResult)
    }

    // Now let's verify integration with MainActivity using fake/stub boosters to advance scheduler/etc.,
    // or we can use mockk to mock coordinator/boosters. Let's test MainActivity logic using mockk and the testDispatcher!
    @Test
    fun `MainActivity success flow`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } returns true

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testScheduler
        )

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
    fun `MainActivity partial success flow`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } returns false

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testScheduler
        )

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Partial Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.PartialSuccess, mainActivity.lastOutcome)
    }

    @Test
    fun `MainActivity partial success flow when network throws`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("network crash")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testScheduler
        )

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Partial Success", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.PartialSuccess, mainActivity.lastOutcome)
    }

    @Test
    fun `MainActivity full failure flow`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns false
        coEvery { networkBooster.applyNetworkOptimization() } returns false

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testScheduler
        )

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Failure", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullFailure, mainActivity.lastOutcome)
    }

    @Test
    fun `MainActivity full failure flow when both throw`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } throws RuntimeException("launch error")
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("network error")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = testScheduler
        )

        val mainActivity = MainActivity(coordinator, this)
        mainActivity.onOptimizeButtonClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Optimization Complete: Full Failure", mainActivity.currentUiState)
        assertTrue(mainActivity.isControlsEnabled)
        assertEquals(OptimizationOutcome.FullFailure, mainActivity.lastOutcome)
    }

    @Test
    fun `MainActivity handling of unexpected coordinator failure`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()

        val coordinator = object : SystemOptimizationCoordinator(launchBooster, networkBooster, testScheduler) {
            override suspend fun optimize(targetPackageName: String): com.example.systembooster.engine.OptimizationResult {
                throw IllegalStateException("Unexpected crash")
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
