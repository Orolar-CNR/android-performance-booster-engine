package com.example.systembooster

import com.example.systembooster.coordinator.SystemOptimizationCoordinator
import com.example.systembooster.engine.AppLaunchSpeedBooster
import com.example.systembooster.engine.NetworkSpeedBooster
import com.example.systembooster.model.OptimizationResult
import com.example.systembooster.model.OptimizationReport
import com.example.systembooster.model.PrivilegeLevel
import com.example.systembooster.model.RetryPolicy
import com.example.systembooster.telemetry.TelemetryService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityBoosterTest {

    @Test
    fun `both boosters succeed`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Success(120L)
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Success(80L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Success)
        assertTrue(result.network is OptimizationResult.Success)

        coVerify(exactly = 1) { launchBooster.optimizeAppLaunch("com.instagram.android") }
        coVerify(exactly = 1) { networkBooster.applyNetworkOptimization() }
        coVerify(exactly = 1) { launchBooster.prepare() }
        coVerify(exactly = 1) { networkBooster.prepare() }
        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `launch success network timeout`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Success(50L)
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Failure.Timeout(1000L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Success)
        assertTrue(result.network is OptimizationResult.Failure.Timeout)

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `launch failure network success`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Failure.ExecutionFailed(
            exitCode = 1,
            errorLog = "permission denied"
        )
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Success(30L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Failure.ExecutionFailed)
        val failure = result.appLaunch as OptimizationResult.Failure.ExecutionFailed
        assertEquals(-1, failure.exitCode) // mapped via execution engine error format in execute()
        assertEquals("permission denied", failure.errorLog)

        assertTrue(result.network is OptimizationResult.Success)

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `both timeout`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Failure.Timeout(15000L)
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Failure.Timeout(10000L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Failure.Timeout)
        assertTrue(result.network is OptimizationResult.Failure.Timeout)

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `launch throws internal error`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } throws IllegalStateException("internal crash")
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Success(10L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Failure.InternalError)
        val error = result.appLaunch as OptimizationResult.Failure.InternalError
        assertEquals("internal crash", error.exception.message)

        assertTrue(result.network is OptimizationResult.Success)

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `both engines return failure`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Failure.ExecutionFailed(255, "compile failed")
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Failure.ExecutionFailed(127, "network command not found")

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val result = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(result.appLaunch is OptimizationResult.Failure.ExecutionFailed)
        assertTrue(result.network is OptimizationResult.Failure.ExecutionFailed)

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `blank package name returns error`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val report = coordinator.runFullOptimization("")

        assertTrue(report.appLaunch is OptimizationResult.Failure.InternalError)
        val failure = report.appLaunch as OptimizationResult.Failure.InternalError
        assertTrue(failure.exception is IllegalArgumentException)
    }

    @Test
    fun `insufficient privilege level immediately returns permission denied`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.ROOT_SU
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Success(30L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher,
            currentPrivilege = PrivilegeLevel.UNPRIVILEGED
        )

        val report = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(report.appLaunch is OptimizationResult.Failure.PermissionDenied)
        assertTrue(report.network is OptimizationResult.Success)

        coVerify(exactly = 0) { launchBooster.optimizeAppLaunch(any()) }
    }

    @Test
    fun `retry policy is respected on failure`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { launchBooster.retryPolicy } returns RetryPolicy.ON_TIMEOUT
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.retryPolicy } returns RetryPolicy.NEVER

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Failure.Timeout(100L)
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Failure.Timeout(100L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        coordinator.runFullOptimization("com.instagram.android")

        coVerify(exactly = 3) { launchBooster.optimizeAppLaunch(any()) }
        coVerify(exactly = 1) { networkBooster.applyNetworkOptimization() }
    }

    @Test
    fun `cooperative cancellation cleanup`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            OptimizationResult.Success(100)
        }
        coEvery { networkBooster.applyNetworkOptimization() } coAnswers {
            kotlinx.coroutines.delay(5000)
            OptimizationResult.Success(100)
        }

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val job = launch {
            coordinator.runFullOptimization("com.instagram.android")
        }

        testScheduler.advanceTimeBy(1000)
        job.cancel()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { launchBooster.cleanup() }
        coVerify(exactly = 1) { networkBooster.cleanup() }
    }

    @Test
    fun `telemetry is recorded on execution`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val telemetryService = mockk<TelemetryService>(relaxed = true)

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Success(100L)
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Success(50L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher,
            telemetryService = telemetryService
        )

        coordinator.runFullOptimization("com.instagram.android")

        coVerify(exactly = 1) { telemetryService.record("app_launch_booster", any(), any()) }
        coVerify(exactly = 1) { telemetryService.record("network_speed_booster", any(), any()) }
    }

    @Test
    fun `failure mapping converts EngineResult failures correctly`() = runTest {
        val launchBooster = spyk<AppLaunchSpeedBooster>()
        val networkBooster = spyk<NetworkSpeedBooster>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        coEvery { launchBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED
        coEvery { networkBooster.requiredPrivilege } returns PrivilegeLevel.UNPRIVILEGED

        coEvery { launchBooster.optimizeAppLaunch(any()) } returns OptimizationResult.Failure.Unsupported("not supported")
        coEvery { networkBooster.applyNetworkOptimization() } returns OptimizationResult.Failure.Timeout(500L)

        val coordinator = SystemOptimizationCoordinator(
            appLaunchBooster = launchBooster,
            networkBooster = networkBooster,
            dispatcher = dispatcher
        )

        val report = coordinator.runFullOptimization("com.instagram.android")

        assertTrue(report.appLaunch is OptimizationResult.Failure.Unsupported)
        assertEquals("not supported", (report.appLaunch as OptimizationResult.Failure.Unsupported).reason)

        assertTrue(report.network is OptimizationResult.Failure.Timeout)
    }
}
