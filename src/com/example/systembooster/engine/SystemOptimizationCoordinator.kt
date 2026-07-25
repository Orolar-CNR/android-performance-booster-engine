package com.example.systembooster.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

sealed class OptimizationOutcome {
    object FullSuccess : OptimizationOutcome()
    object PartialSuccess : OptimizationOutcome()
    object FullFailure : OptimizationOutcome()
}

data class OptimizationResult(
    val launchResult: Boolean,
    val networkResult: Boolean
)

open class SystemOptimizationCoordinator(
    private val launchBooster: AppLaunchSpeedBooster,
    private val networkBooster: NetworkSpeedBooster,
    private val dispatcher: CoroutineDispatcher
) {
    open suspend fun optimize(targetPackageName: String): OptimizationResult = withContext(dispatcher) {
        supervisorScope {
            val launchDeferred = async {
                runCatching { launchBooster.optimizeAppLaunch(targetPackageName) }
                    .getOrDefault(false)
            }

            val networkDeferred = async {
                runCatching { networkBooster.applyNetworkOptimization() }
                    .getOrDefault(false)
            }

            OptimizationResult(
                launchResult = launchDeferred.await(),
                networkResult = networkDeferred.await()
            )
        }
    }
}
