package com.jeerovan.comfer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import androidx.core.content.edit

const val saveLogs: Boolean = false
@Serializable
data class LogEntry(
    val timestamp: Long,
    val module: String,
    val error: String
)

class LoggerManager(context: Context) {

    // Use a separate preference file to avoid conflicts.
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_FILENAME = "com.jeerovan.comfer.AppLogs"
        private const val KEY_LOGS = "error_logs"
    }

    /**
     * Adds a new log entry to SharedPreferences.
     *
     * @param module The name of the module/class where the error occurred.
     * @param error The detailed error message.
     */
    fun setLog(module: String, error: String) {
        if(!saveLogs){
            Log.i(module,error)
            return
        }
        val currentLogs = getLogsInternal().toMutableList()
        val newLog = LogEntry(
            timestamp = System.currentTimeMillis(),
            module = module,
            error = error
        )
        currentLogs.add(newLog)

        // Serialize the updated list to a JSON string
        val logsJson = Json.encodeToString(currentLogs)

        // Save the JSON string
        prefs.edit { putString(KEY_LOGS, logsJson) }
    }

    /**
     * Retrieves all log entries, sorted from most recent to oldest.
     *
     * @return A list of LogEntry objects.
     */
    fun getLogs(): List<LogEntry> {
        return getLogsInternal().reversed()
    }

    /**
     * Internal function to retrieve and deserialize logs from SharedPreferences.
     */
    private fun getLogsInternal(): List<LogEntry> {
        val logsJson = prefs.getString(KEY_LOGS, null)
        if (logsJson.isNullOrEmpty()) {
            return emptyList()
        }

        // Deserialize the JSON string back to a list of LogEntry
        return try {
            Json.decodeFromString<List<LogEntry>>(logsJson)
        } catch (e: Exception) {
            // Handle potential deserialization errors
            emptyList()
        }
    }

    /**
     * Clears all log entries from SharedPreferences.
     */
    fun clearLogs() {
        prefs.edit { clear() }
    }
}
