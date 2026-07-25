package com.example.systembooster.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

open class AppLaunchSpeedBooster(
    private val command: List<String> = listOf("cmd", "package", "compile", "-m", "speed-profile", "-f", "com.example.systembooster")
) {
    open suspend fun boost(): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[AppLaunchSpeedBooster] Starting application compilation optimization...")
            val process = ProcessBuilder(command).apply {
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
