package com.example.systembooster

import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.engine.SystemOptimizationCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityBoosterTest {

    @Test
    fun `optimize returns success when both coroutines succeed`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } returns true

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.optimize("com.instagram.android")
        advanceUntilIdle()

        assertTrue(result.launchResult)
        assertTrue(result.networkResult)
    }

    @Test
    fun `optimize returns partial success when network coroutine fails`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } returns true
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("permission denied")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.optimize("com.instagram.android")
        advanceUntilIdle()

        assertTrue(result.launchResult)
        assertFalse(result.networkResult)
    }

    @Test
    fun `optimize returns failure when both coroutines fail`() = runTest {
        val launchBooster = mockk<AppLaunchSpeedBooster>()
        val networkBooster = mockk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.optimizeAppLaunch("com.instagram.android") } throws RuntimeException("launch failed")
        coEvery { networkBooster.applyNetworkOptimization() } throws RuntimeException("network failed")

        val coordinator = SystemOptimizationCoordinator(
            launchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.optimize("com.instagram.android")
        advanceUntilIdle()

        assertFalse(result.launchResult)
        assertFalse(result.networkResult)
    }
}
