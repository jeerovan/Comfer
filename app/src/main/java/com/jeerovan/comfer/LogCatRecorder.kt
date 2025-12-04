package com.jeerovan.comfer

import android.content.Context
import android.os.Process
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class LogcatRecorder(private val context: Context) {

    private var job: Job? = null
    private val logFile by lazy { File(context.filesDir, "app_error_logs.txt") }

    fun startLogging() {
        if (job?.isActive == true) return

        // Clear old logs on start (optional, depends on your needs)
        // if (logFile.exists()) logFile.delete()

        job = CoroutineScope(Dispatchers.IO).launch {
            val processId = Process.myPid().toString()
            // Command:
            // logcat -v threadtime (timestamps)
            // *:D sets the minimum level to Debug (Includes Debug, Info, Warning, Error)
            val command = "logcat -v threadtime *:D"

            var process: java.lang.Process? = null
            try {
                process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                // FileWriter in append mode
                val writer = FileWriter(logFile, true)

                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        // Filter: ensure the log belongs to OUR process ID
                        if (logLine.contains(processId)) {
                            writer.append(logLine).append("\n")

                            // Flush periodically to ensure data is saved
                            // In production, flush less often for performance
                            writer.flush()
                        }
                    }
                }
                writer.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                process?.destroy()
            }
        }
    }

    fun stopLogging() {
        job?.cancel()
        job = null
    }

    fun getLogs(): String {
        return if (logFile.exists()) logFile.readText() else "No logs found"
    }
}
