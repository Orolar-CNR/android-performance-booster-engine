package com.example.systembooster

import com.example.systembooster.coordinator.SystemOptimizationCoordinator
import com.example.systembooster.model.OptimizationResult
import com.example.systembooster.model.OptimizationReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class OptimizationOutcome {
    object FullSuccess : OptimizationOutcome()
    object PartialSuccess : OptimizationOutcome()
    object FullFailure : OptimizationOutcome()
}

class MainActivity(
    private val coordinator: SystemOptimizationCoordinator,
    private val lifecycleScope: CoroutineScope
) {
    var isControlsEnabled: Boolean = true
        private set

    var currentUiState: String = "Idle"
        private set

    var lastOutcome: OptimizationOutcome? = null
        private set

    fun onOptimizeButtonClicked() {
        if (!isControlsEnabled) {
            println("[MainActivity] Click ignored: controls are currently disabled.")
            return
        }

        isControlsEnabled = false
        currentUiState = "Optimizing..."
        println("[MainActivity] User clicked optimize. State: $currentUiState, Controls Enabled: $isControlsEnabled")

        lifecycleScope.launch {
            try {
                val report = coordinator.runFullOptimization("com.instagram.android")
                val isAppSuccess = report.appLaunch is OptimizationResult.Success
                val isNetworkSuccess = report.network is OptimizationResult.Success

                val outcome = when {
                    isAppSuccess && isNetworkSuccess -> OptimizationOutcome.FullSuccess
                    isAppSuccess || isNetworkSuccess -> OptimizationOutcome.PartialSuccess
                    else -> OptimizationOutcome.FullFailure
                }
                lastOutcome = outcome

                currentUiState = when (outcome) {
                    is OptimizationOutcome.FullSuccess -> "Optimization Complete: Full Success"
                    is OptimizationOutcome.PartialSuccess -> "Optimization Complete: Partial Success"
                    is OptimizationOutcome.FullFailure -> "Optimization Complete: Full Failure"
                }
            } catch (e: Exception) {
                currentUiState = "Error: ${e.message}"
                lastOutcome = OptimizationOutcome.FullFailure
            } finally {
                isControlsEnabled = true
                println("[MainActivity] Flow finished. State: $currentUiState, Controls Enabled: $isControlsEnabled")
            }
        }
    }
}
