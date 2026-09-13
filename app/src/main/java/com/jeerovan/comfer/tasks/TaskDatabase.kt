package com.jeerovan.comfer.tasks

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_lists ORDER BY position, id") suspend fun lists(): List<TaskList>
    @Query("SELECT * FROM tasks ORDER BY position, id") suspend fun tasks(): List<TaskItem>
    @Query("SELECT * FROM task_series ORDER BY id") suspend fun series(): List<TaskSeries>
    @Query("SELECT * FROM task_preferences WHERE id=1") suspend fun preferences(): TaskPreferences?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun lists(items: List<TaskList>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun tasks(items: List<TaskItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun series(items: List<TaskSeries>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun preferences(item: TaskPreferences)
    @Query("DELETE FROM task_lists") suspend fun clearLists()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM task_series") suspend fun clearSeries()
}

/** Separate schema avoids rewriting the launcher's existing database on task feature rollout. */
@Database(entities = [TaskList::class, TaskItem::class, TaskSeries::class, TaskPreferences::class], version = 2, exportSchema = true)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun dao(): TaskDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_preferences ADD COLUMN guidanceDismissed INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: TaskDatabase? = null
        fun get(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java,
                File(context.noBackupFilesDir, "tasks.db").absolutePath).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

class TaskUndo internal constructor(val before: TaskSnapshot, val after: TaskSnapshot)

object TaskStore {
    internal val mutex = Mutex()
    private val mutable = MutableStateFlow(TaskSnapshot())
    val state = mutable.asStateFlow()
    private var loaded = false
    private suspend fun load(context: Context) {
        if (loaded) return
        val db = TaskDatabase.get(context)
        val snapshot = db.withTransaction {
            val dao = db.dao()
            val prefs = dao.preferences()
            if (prefs == null) {
                val initial = TaskSnapshot()
                dao.lists(initial.lists); dao.preferences(initial.preferences)
                initial
            } else TaskSnapshot(lists = dao.lists(), tasks = dao.tasks(), series = dao.series(), preferences = prefs)
        }
        snapshot.validate()
        mutable.value = snapshot
        loaded = true
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) { mutex.withLock { load(context) } }
    suspend fun snapshot(context: Context): TaskSnapshot = withContext(Dispatchers.IO) { mutex.withLock { load(context); mutable.value } }

    suspend fun change(context: Context, transform: (TaskSnapshot) -> TaskSnapshot): TaskUndo = withContext(Dispatchers.IO) {
        mutex.withLock {
            load(context)
            val before = mutable.value
            val next = transform(before)
            if(next == before) return@withLock TaskUndo(before, before)
            val after = next.copy(preferences = next.preferences.copy(revision = before.preferences.revision + 1))
            write(context, after)
            TaskUndo(before, after)
        }
    }.also { TaskReminders.request(context) }

    internal suspend fun <T> exclusive(context: Context, block: suspend (TaskSnapshot) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { load(context); block(mutable.value) }
    }

    suspend fun undo(context: Context, undo: TaskUndo) = change(context) { current ->
        // Only revert affected rows; unrelated reminders and edits must not be overwritten.
        fun <T, K> revert(now: List<T>, before: List<T>, after: List<T>, id: (T) -> K): List<T> {
            val b = before.associateBy(id); val a = after.associateBy(id); val n = now.associateBy(id)
            val changed = (b.keys + a.keys).filter { b[it] != a[it] }.toSet()
            require(changed.all { n[it] == a[it] }) { "This item changed again; Undo is no longer available" }
            return now.filterNot { id(it) in changed } + before.filter { id(it) in changed }
        }
        val lists = revert(current.lists, undo.before.lists, undo.after.lists) { it.id }
        val selected = if(current.preferences.selectedList == undo.after.preferences.selectedList && undo.before.preferences.selectedList != undo.after.preferences.selectedList) undo.before.preferences.selectedList else current.preferences.selectedList
        current.copy(tasks = revert(current.tasks, undo.before.tasks, undo.after.tasks) { it.id },
            lists = lists,
            series = revert(current.series, undo.before.series, undo.after.series) { it.id },
            preferences = current.preferences.copy(selectedList = selected.takeIf { id -> lists.any { it.id == id } } ?: lists.first().id))
    }

    internal suspend fun write(context: Context, value: TaskSnapshot) {
        value.validate()
        val db = TaskDatabase.get(context)
        db.withTransaction {
            db.dao().apply {
                clearTasks(); clearSeries(); clearLists()
                lists(value.lists); tasks(value.tasks); series(value.series); preferences(value.preferences)
            }
        }
        mutable.value = value
        loaded = true
    }
}
