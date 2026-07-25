package com.example.systembooster.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

open class AppLaunchSpeedBooster(
    private val command: List<String>? = null
) {
    open suspend fun optimizeAppLaunch(targetPackageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[AppLaunchSpeedBooster] Starting application compilation optimization for $targetPackageName...")
            val actualCommand = command ?: listOf("cmd", "package", "compile", "-m", "speed-profile", "-f", targetPackageName)
            val process = ProcessBuilder(actualCommand).apply {
                redirectErrorStream(true)
            }.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                println("[AppLaunchSpeedBooster Output] $line")
            }

            val exitCode = process.waitFor()
            println("[AppLaunchSpeedBooster] Finished with exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            println("[AppLaunchSpeedBooster] Failed to execute optimization: ${e.message}")
            false
        }
    }
}
