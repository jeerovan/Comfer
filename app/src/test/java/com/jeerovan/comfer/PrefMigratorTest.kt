package com.jeerovan.comfer

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeerovan.comfer.data.COMFER_DATABASE_NAME
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefMigratorTest {
    @Test
    fun validFolderFixturePreservesIdsTitlesAndPackages() {
        val folders = parseLegacyFolders(
            """{"work":{"title":"Work","packages":["com.example.one","com.example.\"quoted"]}}""",
        )

        assertEquals(1, folders.size)
        assertEquals("work", folders.single().id)
        assertEquals("Work", folders.single().title)
        assertEquals(
            """["com.example.one","com.example.\"quoted"]""",
            folders.single().packagesJson,
        )
    }

    @Test(expected = SerializationException::class)
    fun malformedFolderFixtureAbortsInsteadOfSilentlyDroppingData() {
        parseLegacyFolders("""{"work":{"title":"Work","packages":not-json}}""")
    }

    @Test
    fun version39PayloadPreservesEveryStoreAndEmptyLists() {
        val widgetJson =
            """[{"widgetId":42,"providerPackage":"com.example.widget","providerClass":"ExampleProvider","gridX":1,"gridY":2,"spanX":3,"spanY":4}]"""
        val payload = prepareLegacyMigration(
            settings = mapOf(
                "enabled" to true,
                "scale" to 1.25f,
                "counter" to 7,
                "timestamp" to 123456789L,
                "label" to "Comfer",
            ),
            appInfo = mapOf(
                "all_apps" to "com.example.one${LEGACY_APP_LIST_DELIMITER}com.example.two",
                "quick" to "com.example.one",
                "primary" to "",
                LEGACY_FOLDERS_PREF_KEY to
                    """{"folder_1":{"title":"Tools","packages":["com.example.two"]}}""",
            ),
            widgetValues = mapOf(
                "widgets_center" to widgetJson,
                "widgets_prefs_left" to null,
                "widgets_prefs_right" to "",
            ),
        )

        assertEquals("true", payload.settings["enabled"])
        assertEquals("1.25", payload.settings["scale"])
        assertEquals("7", payload.settings["counter"])
        assertEquals("123456789", payload.settings["timestamp"])
        assertEquals("Comfer", payload.settings["label"])
        assertEquals("""["com.example.one","com.example.two"]""", payload.appListsJson["all_apps"])
        assertEquals("""["com.example.one"]""", payload.appListsJson["quick"])
        assertEquals("[]", payload.appListsJson["primary"])
        assertEquals("folder_1", payload.folders.single().id)
        assertEquals("Tools", payload.folders.single().title)
        assertEquals("""["com.example.two"]""", payload.folders.single().packagesJson)
        assertEquals(mapOf("widgets_center" to widgetJson), payload.widgetsJson)
    }

    @Test(expected = SerializationException::class)
    fun malformedVersion39WidgetAbortsPreflightBeforeAnyWrite() {
        prepareLegacyMigration(
            settings = emptyMap<String, Any?>(),
            appInfo = emptyMap<String, Any?>(),
            widgetValues = mapOf("widgets_center" to """[{"widgetId":"wrong-type"}]"""),
        )
    }

    @Test
    fun version40StorageContractLoadsDirectlyAndSkipsLegacyMigration() {
        assertEquals("comfer_settings", SETTINGS_DATASTORE_NAME)
        assertEquals("comfer.db", COMFER_DATABASE_NAME)
        assertEquals("prefs_migrated_v2", PREFS_MIGRATED_FLAG)

        val v40Preferences = mutablePreferencesOf(
            stringPreferencesKey("icon_size") to "72",
            stringPreferencesKey("wallpaper_motion") to "true",
            booleanPreferencesKey(PREFS_MIGRATED_FLAG) to true,
        )
        val snapshot = preferencesToSnapshot(v40Preferences)

        assertEquals("72", snapshot["icon_size"])
        assertEquals("true", snapshot["wallpaper_motion"])
        assertEquals("true", snapshot[PREFS_MIGRATED_FLAG])
        assertFalse(shouldRunLegacyMigration(true))
        assertTrue(shouldRunLegacyMigration(false))
        assertTrue(shouldRunLegacyMigration(null))
    }

    @Test
    fun version39ExecutionPersistsBeforeDeletingAndCompleting() = runBlocking {
        val events = mutableListOf<String>()
        val payload = LegacyMigrationPayload(emptyMap(), emptyMap(), emptyList(), emptyMap())

        val migrated = executeLegacyMigration(
            migrated = null,
            readAndValidate = { events += "read"; payload },
            persist = { events += "persist" },
            deleteLegacySources = { events += "delete" },
            markComplete = { events += "complete" },
        )

        assertTrue(migrated)
        assertEquals(listOf("read", "persist", "delete", "complete"), events)
    }

    @Test
    fun version40ExecutionDoesNotTouchDeletedLegacySources() = runBlocking {
        val events = mutableListOf<String>()
        val payload = LegacyMigrationPayload(emptyMap(), emptyMap(), emptyList(), emptyMap())

        val migrated = executeLegacyMigration(
            migrated = true,
            readAndValidate = { events += "read"; payload },
            persist = { events += "persist" },
            deleteLegacySources = { events += "delete" },
            markComplete = { events += "complete" },
        )

        assertFalse(migrated)
        assertTrue(events.isEmpty())
    }

    @Test
    fun failedVersion39PersistenceKeepsSourcesAndFlagUnset() {
        val events = mutableListOf<String>()
        val payload = LegacyMigrationPayload(emptyMap(), emptyMap(), emptyList(), emptyMap())

        try {
            runBlocking {
                executeLegacyMigration(
                    migrated = false,
                    readAndValidate = { events += "read"; payload },
                    persist = { events += "persist"; error("storage failure") },
                    deleteLegacySources = { events += "delete" },
                    markComplete = { events += "complete" },
                )
            }
        } catch (_: IllegalStateException) {
            // Expected: startup remains failed/retryable.
        }

        assertEquals(listOf("read", "persist"), events)
    }
}
