package com.jeerovan.comfer

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashFileName = "crash_logs.txt"

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 1. Get the stack trace as a string
        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))

        // 2. Prepare the crash log entry with human-readable date
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val crashLog = """
            
            =====================================
            CRASH DETECTED
            Time: $timestamp
            Thread: ${thread.name}
            Exception: ${throwable.localizedMessage}
            
            Stack Trace:
            $stackTrace
            =====================================
            
        """.trimIndent()

        // 3. Save to File
        saveCrashLogToFile(crashLog)

        // 4. Let the default handler take over to terminate the app
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLogToFile(crashLog: String) {
        try {
            val file = File(context.filesDir, crashFileName)

            // FileWriter(file, true) enables append mode
            FileWriter(file, true).use { writer ->
                writer.append(crashLog)
            }
        } catch (e: IOException) {
            // Fallback: Print to standard error if file write fails
            e.printStackTrace()
        }
    }
}
