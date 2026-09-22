package com.jeerovan.comfer

import com.jeerovan.comfer.compat.FrameworkCompatibilityTest
import com.jeerovan.comfer.journals.*
import com.jeerovan.comfer.notes.NotesMigrationTest
import com.jeerovan.comfer.tasks.TaskLayoutTest
import com.jeerovan.comfer.tasks.TaskPersistenceTest
import com.jeerovan.comfer.tasks.TaskReminderReceiverTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Run on the isolated notificationTest application, never the user's release data. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    DatabasePackagingTest::class,
    FrameworkCompatibilityTest::class,
    JournalMigrationTest::class,
    NotesMigrationTest::class,
    AppIconLoadingTest::class,
    JournalActivityTest::class,
    JournalBackupTest::class,
    JournalEncryptionTest::class,
    JournalPersistenceTest::class,
    TaskPersistenceTest::class,
    TaskReminderReceiverTest::class,
    TaskLayoutTest::class,
    ModuleLocaleTest::class,
)
class Release53RegressionSuite
