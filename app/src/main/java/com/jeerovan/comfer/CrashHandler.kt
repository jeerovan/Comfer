package com.jeerovan.comfer

import android.content.Context
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import androidx.core.content.edit

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 1. Get the stack trace as a string
        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))

        // 2. Prepare the crash log entry
        val crashLog = """
            Time: ${System.currentTimeMillis()}
            Thread: ${thread.name}
            Exception: ${throwable.message}
            
            Stack Trace:
            $stackTrace
            -------------------------------------
            
        """.trimIndent()

        // 3. Save to SharedPreferences
        saveCrashLog(crashLog)

        // 4. Let the default handler take over to terminate the app
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashLog(crashLog: String) {
        val prefs = context.getSharedPreferences("CrashLogs", Context.MODE_PRIVATE)
        val existingLogs = prefs.getString("logs", "") ?: ""
        prefs.edit { putString("logs", existingLogs + crashLog) }
    }
}
