package com.jeerovan.comfer

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.data.ComferDatabase
import com.jeerovan.comfer.journals.JournalDatabase
import com.jeerovan.comfer.notes.NotesDatabase
import com.jeerovan.comfer.tasks.TaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DatabasePackagingTest {
    @Test fun everyPackagedDatabaseCanInstantiateItsGeneratedImplementationAndOpen() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val factories: List<Pair<Int, () -> RoomDatabase>> = listOf(
            1 to { Room.inMemoryDatabaseBuilder(context, ComferDatabase::class.java).build() },
            2 to { Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build() },
            4 to { Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java).build() },
            5 to { Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build() },
        )
        for ((version, create) in factories) {
            val db = create()
            try {
                db.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(version, cursor.getInt(0))
                }
            } finally { db.close() }
        }
    }
}
