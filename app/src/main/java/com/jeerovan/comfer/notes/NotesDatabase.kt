package com.jeerovan.comfer.notes

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "notes", indices = [Index("notebook"), Index("deletedAt")])
data class NoteRow(@PrimaryKey val id: String, val payload: ByteArray, val protected: Boolean, val revision: Long,
    val notebook: String, val deletedAt: Long?)
@Entity(tableName = "note_drafts")
data class NoteDraftRow(@PrimaryKey val id: String, val noteId: String, val payload: ByteArray, val protected: Boolean, val revision: Long)
@Entity(tableName = "note_labels")
data class NoteLabelRow(@PrimaryKey val id: String, val payload: ByteArray)
@Entity(tableName = "notes_state")
data class NotesStateRow(@PrimaryKey val id: Int = 1, val payload: ByteArray, val generation: Long = 0, val moduleLocked: Boolean = false, @ColumnInfo(defaultValue="''") val keyGeneration:String="")

@Dao interface NotesDao {
    @Query("SELECT * FROM notes WHERE id=:id") suspend fun note(id: String): NoteRow?
    @Query("SELECT * FROM notes ORDER BY id LIMIT :limit OFFSET :offset") suspend fun page(limit: Int = 100, offset: Int = 0): List<NoteRow>
    @Query("SELECT * FROM notes ORDER BY id") fun observeRows(): Flow<List<NoteRow>>
    @Query("SELECT * FROM note_drafts ORDER BY id") suspend fun drafts(): List<NoteDraftRow>
    @Query("SELECT * FROM note_drafts WHERE id=:id") suspend fun draft(id: String): NoteDraftRow?
    @Query("SELECT * FROM note_labels ORDER BY id") suspend fun labels(): List<NoteLabelRow>
    @Query("SELECT * FROM notes_state WHERE id=1") suspend fun state(): NotesStateRow?
    @Upsert suspend fun put(row: NoteRow)
    @Upsert suspend fun putDraft(row: NoteDraftRow)
    @Upsert suspend fun putLabel(row: NoteLabelRow)
    @Upsert suspend fun putState(row: NotesStateRow)
    @Query("DELETE FROM notes WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM note_drafts WHERE id=:id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM note_labels WHERE id=:id") suspend fun deleteLabel(id: String)
    @Query("DELETE FROM notes_state") suspend fun clearState()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM note_drafts") suspend fun clearDrafts()
    @Query("DELETE FROM note_labels") suspend fun clearLabels()
}
@Database(entities = [NoteRow::class, NoteDraftRow::class, NoteLabelRow::class, NotesStateRow::class], version = 2, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun dao(): NotesDao
    companion object {
        val MIGRATION_1_2 = object:androidx.room.migration.Migration(1,2) {
            override fun migrate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes_state ADD COLUMN keyGeneration TEXT NOT NULL DEFAULT ''")
            }
        }
        @Volatile private var instance: NotesDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NotesDatabase::class.java,
                File(context.noBackupFilesDir, "notes.db").absolutePath).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
