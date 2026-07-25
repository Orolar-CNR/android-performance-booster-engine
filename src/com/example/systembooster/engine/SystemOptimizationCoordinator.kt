package com.example.systembooster.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

sealed class OptimizationOutcome {
    object FullSuccess : OptimizationOutcome()
    object PartialSuccess : OptimizationOutcome()
    object FullFailure : OptimizationOutcome()
}

open class SystemOptimizationCoordinator(
    private val appLaunchSpeedBooster: AppLaunchSpeedBooster,
    private val networkSpeedBooster: NetworkSpeedBooster
) {
    open suspend fun optimize(): OptimizationOutcome = supervisorScope {
        println("[SystemOptimizationCoordinator] Initiating parallel system optimization...")

        val appLaunchJob = async {
            try {
                appLaunchSpeedBooster.boost()
            } catch (e: Exception) {
                println("[SystemOptimizationCoordinator] AppLaunchSpeedBooster threw exception: ${e.message}")
                false
            }
        }

        val networkJob = async {
            try {
                networkSpeedBooster.boost()
            } catch (e: Exception) {
                println("[SystemOptimizationCoordinator] NetworkSpeedBooster threw exception: ${e.message}")
                false
            }
        }

        val appLaunchSuccess = appLaunchJob.await()
        val networkSuccess = networkJob.await()

        println("[SystemOptimizationCoordinator] Optimization tasks completed. AppLaunch success: $appLaunchSuccess, Network success: $networkSuccess")

        when {
            appLaunchSuccess && networkSuccess -> OptimizationOutcome.FullSuccess
            appLaunchSuccess || networkSuccess -> OptimizationOutcome.PartialSuccess
            else -> OptimizationOutcome.FullFailure
        }
    }
}
