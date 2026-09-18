package com.jeerovan.comfer.notes

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

class NotesMigrationTest {
    @Test fun migrationKeepsExistingCiphertextAndRevision() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="notes-migration-test"
        context.deleteDatabase(name)
        val schema=instrumentation.context.assets.open("com.jeerovan.comfer.notes.NotesDatabase/1.json").bufferedReader().use{JSONObject(it.readText()).getJSONObject("database")}
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
                val entities=schema.getJSONArray("entities")
                for(i in 0 until entities.length()){
                    val entity=entities.getJSONObject(i)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                    val indices=entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
                }
                val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO notes (id,payload,protected,revision,notebook,deletedAt) VALUES ('fixture',X'012345',0,7,'inbox',NULL)")
                db.execSQL("INSERT INTO notes_state (id,payload,generation,moduleLocked) VALUES (1,X'0189AB',2,0)")
                db.version=1
            }
            val room=Room.databaseBuilder(context,NotesDatabase::class.java,name).addMigrations(NotesDatabase.MIGRATION_1_2).build()
            try {
                val db=room.openHelper.writableDatabase
                db.query("SELECT hex(payload),revision FROM notes WHERE id='fixture'").use{assertTrue(it.moveToFirst());assertEquals("012345",it.getString(0));assertEquals(7L,it.getLong(1))}
                db.query("SELECT hex(payload),generation,keyGeneration FROM notes_state").use{assertTrue(it.moveToFirst());assertEquals("0189AB",it.getString(0));assertEquals(2L,it.getLong(1));assertEquals("",it.getString(2))}
            }finally{room.close()}
        }finally{context.deleteDatabase(name)}
    }
}
