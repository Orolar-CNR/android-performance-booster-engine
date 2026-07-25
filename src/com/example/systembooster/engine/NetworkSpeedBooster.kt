package com.example.systembooster.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

open class NetworkSpeedBooster(
    private val command: List<String> = listOf(
        "ping",
        "-c",
        "3",
        "8.8.8.8"
    )
) {

    companion object {
        private const val TAG = "NetworkSpeedBooster"
        private const val PROCESS_TIMEOUT_SECONDS = 10L
    }

    open suspend fun applyNetworkOptimization(): Boolean = withContext(Dispatchers.IO) {

        var process: Process? = null

        try {
            Log.i(TAG, "Starting network optimization...")

            process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream
                .bufferedReader()
                .use { reader ->

                    reader.forEachLine { line ->
                        Log.d(TAG, line)
                    }
                }

            val completed = process.waitFor(
                PROCESS_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )

            if (!completed) {
                Log.e(TAG, "Process timeout")
                process.destroyForcibly()
                return@withContext false
            }

            val exitCode = process.exitValue()

            Log.i(TAG, "Finished with exit code = $exitCode")

            exitCode == 0

        } catch (e: Exception) {

            Log.e(TAG, "Network optimization failed", e)

            false

        } finally {

            process?.destroy()

        }
    }
}
