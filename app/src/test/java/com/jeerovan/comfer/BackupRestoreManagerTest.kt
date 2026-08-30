package com.jeerovan.comfer

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreManagerTest {

    @Test
    fun suggestedFileNameUsesStablePortableDateFormat() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .parse("2026-08-28 12:00:00") ?: error("date")

        assertEquals("Comfer-backup-2026-08-28.zip", BackupRestoreManager.suggestedFileName(date))
    }

    @Test
    fun automaticWallpaperDefaultsToEnabled() {
        assertTrue(BackupRestoreManager.isAutomaticWallpaperEnabled(emptyMap()))
        assertTrue(
            BackupRestoreManager.isAutomaticWallpaperEnabled(
                mapOf(PreferenceManager.AUTO_WALLPAPER to "true"),
            ),
        )
        assertFalse(
            BackupRestoreManager.isAutomaticWallpaperEnabled(
                mapOf(PreferenceManager.AUTO_WALLPAPER to "false"),
            ),
        )
        assertFalse(
            BackupRestoreManager.isAutomaticWallpaperEnabled(
                mapOf(
                    PreferenceManager.AUTO_WALLPAPER to "true",
                    PreferenceManager.BATTERY_SAVER_MODE to "true",
                ),
            ),
        )
    }

    @Test
    fun validPayloadAcceptsEmptyPortableStores() {
        BackupRestoreManager.validateBackupPayload(validPayload())
    }

    @Test
    fun duplicateAppListIdsAreRejectedBeforeRestore() {
        val list = BackupAppList("primary", "[]")
        val payload = validPayload().copy(
            room = validPayload().room.copy(appLists = listOf(list, list)),
        )

        assertThrows(InvalidBackupException::class.java) {
            BackupRestoreManager.validateBackupPayload(payload)
        }
    }

    @Test
    fun malformedPackageListIsRejectedBeforeRestore() {
        val payload = validPayload().copy(
            room = validPayload().room.copy(
                appLists = listOf(BackupAppList("primary", "not-json")),
            ),
        )

        assertThrows(InvalidBackupException::class.java) {
            BackupRestoreManager.validateBackupPayload(payload)
        }
    }

    @Test
    fun malformedWidgetPlacementIsRejectedBeforeRestore() {
        val payload = validPayload().copy(
            room = validPayload().room.copy(
                widgetPlacements = listOf(
                    BackupWidgetPlacement("widgets_center", "{}"),
                ),
            ),
        )

        assertThrows(InvalidBackupException::class.java) {
            BackupRestoreManager.validateBackupPayload(payload)
        }
    }

    @Test
    fun payloadChecksumMismatchIsRejected() {
        val archive = createArchive(validPayload(), payloadHash = "invalid")
        try {
            assertThrows(InvalidBackupException::class.java) {
                BackupRestoreManager.readAndValidateArchive(
                    expectedPackageName = "com.jeerovan.comfer",
                    archive = archive,
                    loadWallpaper = true,
                )
            }
        } finally {
            archive.delete()
        }
    }

    @Test
    fun wallpaperEntryIsNotReadWhenAutomaticWallpaperIsOff() {
        val archive = createArchive(
            payload = validPayload(),
            wallpaper = byteArrayOf(1, 2, 3, 4),
            wallpaperHash = "deliberately-invalid",
        )
        try {
            val validated = BackupRestoreManager.readAndValidateArchive(
                expectedPackageName = "com.jeerovan.comfer",
                archive = archive,
                loadWallpaper = true,
            )

            assertFalse(validated.shouldRestoreWallpaper)
            assertEquals(null, validated.wallpaperBytes)
        } finally {
            archive.delete()
        }
    }

    @Test
    fun wallpaperChecksumIsRequiredWhenAutomaticWallpaperIsOn() {
        val payload = validPayload().copy(
            settings = mapOf(PreferenceManager.AUTO_WALLPAPER to "true"),
        )
        val archive = createArchive(
            payload = payload,
            wallpaper = byteArrayOf(1, 2, 3, 4),
            wallpaperHash = "invalid",
        )
        try {
            assertThrows(InvalidBackupException::class.java) {
                BackupRestoreManager.readAndValidateArchive(
                    expectedPackageName = "com.jeerovan.comfer",
                    archive = archive,
                    loadWallpaper = true,
                )
            }
        } finally {
            archive.delete()
        }
    }

    private fun validPayload() = BackupPayload(
        settings = mapOf(PreferenceManager.AUTO_WALLPAPER to "false"),
        room = BackupRoomData(
            appLists = listOf(BackupAppList("primary", "[]")),
            folders = listOf(BackupFolder("folder-1", "Folder", "[]")),
            widgetPlacements = listOf(
                BackupWidgetPlacement("widgets_center", "[]"),
            ),
            imageData = emptyList(),
            settings = emptyList(),
        ),
        appLocaleTags = "en-IN",
    )

    private fun createArchive(
        payload: BackupPayload,
        payloadHash: String? = null,
        wallpaper: ByteArray? = null,
        wallpaperHash: String? = null,
    ): File {
        val json = Json { encodeDefaults = true }
        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        val wallpaperEntry = wallpaper?.let { "wallpaper/current.img" }
        val manifest = BackupManifest(
            formatVersion = 1,
            packageName = "com.jeerovan.comfer",
            sourceVersionCode = 44,
            sourceVersionName = "44.0",
            createdAtEpochMs = 1L,
            payloadSha256 = payloadHash ?: sha256ForTest(payloadBytes),
            wallpaperEntry = wallpaperEntry,
            wallpaperSha256 = wallpaperHash ?: wallpaper?.let(::sha256ForTest),
            wallpaperSize = wallpaper?.size?.toLong(),
        )
        val archive = File.createTempFile("comfer_backup_test_", ".zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.writeTestEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
            zip.writeTestEntry("payload.json", payloadBytes)
            if (wallpaper != null && wallpaperEntry != null) {
                zip.writeTestEntry(wallpaperEntry, wallpaper)
            }
        }
        return archive
    }

    private fun sha256ForTest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun ZipOutputStream.writeTestEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
