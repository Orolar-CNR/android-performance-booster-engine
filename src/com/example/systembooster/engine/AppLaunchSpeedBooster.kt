package com.example.systembooster.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

open class AppLaunchSpeedBooster(

    private val command: List<String>? = null

) {

    companion object {

        private const val TAG = "AppLaunchSpeedBooster"

        private const val PROCESS_TIMEOUT_SECONDS = 15L

    }

    open suspend fun optimizeAppLaunch(

        targetPackageName: String

    ): Boolean = withContext(Dispatchers.IO) {

        var process: Process? = null

        try {

            val actualCommand = command ?: listOf(

                "cmd",
                "package",
                "compile",
                "-m",
                "speed-profile",
                "-f",
                targetPackageName

            )

            Log.i(
                TAG,
                "Starting compilation optimization for $targetPackageName"
            )

            process = ProcessBuilder(actualCommand)
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

                Log.e(TAG, "Compilation timeout")

                process.destroyForcibly()

                return@withContext false

            }

            val exitCode = process.exitValue()

            Log.i(TAG, "Compilation finished with exit code = $exitCode")

            exitCode == 0

        } catch (e: Exception) {

            Log.e(TAG, "Compilation failed", e)

            false

        } finally {

            process?.destroy()

        }

    }

}
