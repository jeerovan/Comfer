package com.jeerovan.comfer.journals

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Only a verified schema-1..4 upgrade can opt into plaintext conversion. */
internal const val LEGACY_JOURNAL_STORAGE = -1

internal val JOURNAL_MIGRATIONS = arrayOf(
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS journal_state (id INTEGER NOT NULL, generation INTEGER NOT NULL, PRIMARY KEY(id))")
        }
    },
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_deletedAt_day_createdAt_id ON journal_entries(deletedAt,day,createdAt,id)")
        }
    },
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journal_drafts ADD COLUMN createdAt INTEGER")
        }
    },
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE journal_state ADD COLUMN storageVersion INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE journal_state ADD COLUMN moduleLocked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE journal_state ADD COLUMN cleanupPending INTEGER NOT NULL DEFAULT 0")
            db.execSQL("INSERT OR IGNORE INTO journal_state (id,generation) VALUES (1,0)")
            // Do not mark old plaintext as encrypted. Conversion runs transactionally
            // through JournalDao on IO, using the existing device encryption format.
            db.execSQL("UPDATE journal_state SET storageVersion=$LEGACY_JOURNAL_STORAGE WHERE id=1")
        }
    },
)
