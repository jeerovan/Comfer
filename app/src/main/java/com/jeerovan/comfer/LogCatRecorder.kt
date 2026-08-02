package com.jeerovan.comfer

import android.content.Context
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

/**
 * Continuously tails `logcat` on a background coroutine and persists this
 * process's lines to a bounded file.
 *
 * Health guards (ANR/perf safety):
 *  - Disk flushes are batched every [FLUSH_LINE_BATCH] lines instead of per
 *    line, avoiding sustained disk I/O on low-end devices.
 *  - The file is capped at [MAX_LOG_BYTES] and trimmed to the tail
 *    [MAX_KEPT_LINES] lines, so readers (and the debug log viewer) never have
 *    to load/sweep an unbounded multi-MB file.
 */
class LogcatRecorder(private val context: Context) {

    private var job: Job? = null
    private val logFile by lazy { File(context.filesDir, "app_error_logs.txt") }

    private val maxLogBytes = 2 * 1024 * 1024L   // 2 MB cap
    private val maxKeptLines = 2000
    private val flushLineBatch = 100

    fun startLogging() {
        if (job?.isActive == true) return

        job = CoroutineScope(Dispatchers.IO).launch {
            val processId = Process.myPid().toString()
            val command = "logcat -v threadtime *:D"

            var process: java.lang.Process? = null
            // Batch buffer to cut per-line flushes.
            val pending = StringBuilder()
            var pendingCount = 0

            try {
                process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                FileWriter(logFile, true).use { writer ->
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            // Filter: ensure the log belongs to OUR process ID
                            if (logLine.contains(processId)) {
                                pending.append(logLine).append('\n')
                                pendingCount++
                                if (pendingCount >= flushLineBatch) {
                                    writer.append(pending)
                                    writer.flush()
                                    pending.setLength(0)
                                    pendingCount = 0
                                    trimToSize()
                                }
                            }
                        }
                    }
                    // Flush any remainder at EOF.
                    if (pendingCount > 0) {
                        writer.append(pending)
                        writer.flush()
                        trimToSize()
                    }
                }
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
        return runCatching { if (logFile.exists()) logFile.readText() else "No logs found" }
            .getOrElse { "Error reading log file: ${it.localizedMessage}" }
    }

    /** Keep the file under the size cap by keeping only the most recent lines. */
    private fun trimToSize() {
        // Cheap existence/length check first; the expensive rewrite only runs
        // when the cap is actually exceeded (rare once per ~2 MB of logs).
        if (!logFile.exists() || logFile.length() <= maxLogBytes) return
        try {
            val lines = logFile.readLines()
            if (lines.size <= maxKeptLines) return
            logFile.writeText(lines.takeLast(maxKeptLines).joinToString("\n"))
        } catch (e: Exception) {
            // Worst case: drop the file so it can never grow without bound.
            try { logFile.delete() } catch (_: Exception) {}
        }
    }
}
