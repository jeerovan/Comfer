package com.jeerovan.comfer.journals

import android.app.KeyguardManager
import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Journal has its own authenticated Keystore keys, independent of the portable backup password. */
object JournalProtection {
    fun authorize() = com.jeerovan.comfer.ProtectionSession.authorize()
    fun lock() = com.jeerovan.comfer.ProtectionSession.lock()
    fun authorized() = com.jeerovan.comfer.ProtectionSession.authorized()
    fun requireAuthorization() { check(authorized()) { "Unlock Journal before this operation" } }
    // Cached for UI; persisted database state is authoritative for protection.
    fun enabled(context: Context) = context.getSharedPreferences("journal-privacy", Context.MODE_PRIVATE).getBoolean("locked", false)
    suspend fun requiresAuthentication(context: Context): Boolean {
        val state = JournalDatabase.get(context).rawDao().storageState()
        return state?.moduleLocked ?: false
    }
    internal fun restoreState(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences("journal-privacy", Context.MODE_PRIVATE).edit().putBoolean("locked", enabled).commit())
    }
    suspend fun prepare(context: Context) = withContext(Dispatchers.IO) {
        val db = JournalDatabase.get(context)
        val state = db.dao().initialize()
        restoreState(context, state.moduleLocked)
        // Remove obsolete ciphertext after changing protection keys.
        // Recovery must finish before collecting files needed by its ciphertext checkpoint.
        if (state.cleanupPending && !db.inTransaction()) {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.moveToFirst() && it.getInt(0) == 0) { "Journal storage is busy; reopen it to finish securing its old data" } }
            db.openHelper.writableDatabase.execSQL("VACUUM")
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { check(it.moveToFirst() && it.getInt(0) == 0) { "Journal storage is busy; reopen it to finish securing its old data" } }
            db.withTransaction {
                val referenced = db.rawDao().referencedImages().toSet()
                if (!java.io.File(context.filesDir, "backup_restore_journal.json").exists()) {
                    java.io.File(context.noBackupFilesDir, "journal-media").listFiles()?.filter {
                        (it.name.endsWith(".jpg") || it.name.startsWith("import-")) && it.name !in referenced
                    }?.forEach { check(it.delete()) { "Could not remove obsolete Journal media" } }
                    context.noBackupFilesDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("journal-export-") }
                        ?.forEach { check(it.deleteRecursively()) { "Could not remove an old Journal export" } }
                    db.rawDao().storageState()?.let { db.rawDao().state(it.copy(cleanupPending = false)) }
                }
            }
        }
    }
    suspend fun setEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        val db = JournalDatabase.get(context)
        if (enabled || requiresAuthentication(context)) {
            require(context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Set a device PIN, pattern or password first" }
            requireAuthorization()
        }
        db.dao().changeProtection(enabled)
        restoreState(context, enabled)
        prepare(context)
    }
}
