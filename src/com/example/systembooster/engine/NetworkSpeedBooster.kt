package com.example.systembooster.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

open class NetworkSpeedBooster(
    private val command: List<String> = listOf("ping", "-c", "3", "8.8.8.8")
) {
    open suspend fun applyNetworkOptimization(): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[NetworkSpeedBooster] Starting network tuning/verification...")
            val process = ProcessBuilder(command).apply {
                redirectErrorStream(true)
            }.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println("[NetworkSpeedBooster Output] $line")
            }

            val exitCode = process.waitFor()
            println("[NetworkSpeedBooster] Finished with exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            println("[NetworkSpeedBooster] Failed to execute network optimization: ${e.message}")
            false
        }
    }
}
