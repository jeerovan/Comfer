package com.jeerovan.comfer

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.jeerovan.comfer.data.AppFolderEntity
import com.jeerovan.comfer.data.AppListEntity
import com.jeerovan.comfer.data.ComferRepository
import com.jeerovan.comfer.data.ImageDataEntity
import com.jeerovan.comfer.data.RoomDataSnapshot
import com.jeerovan.comfer.data.SettingEntity
import com.jeerovan.comfer.data.WidgetPlacementEntity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val BACKUP_FORMAT_VERSION = 1
private const val MANIFEST_ENTRY = "manifest.json"
private const val PAYLOAD_ENTRY = "payload.json"
private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
private const val MAX_JSON_BYTES = 5L * 1024L * 1024L
private const val MAX_WALLPAPER_BYTES = 32L * 1024L * 1024L
private const val RESTORE_JOURNAL_FILE = "backup_restore_journal.json"
private const val RESTORED_WALLPAPER_PREFIX = "comfer_restored_wallpaper_"

private val backupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
internal data class BackupManifest(
    val formatVersion: Int,
    val packageName: String,
    val sourceVersionCode: Int,
    val sourceVersionName: String,
    val createdAtEpochMs: Long,
    val payloadSha256: String,
    val wallpaperEntry: String? = null,
    val wallpaperSha256: String? = null,
    val wallpaperSize: Long? = null,
)

@Serializable
internal data class BackupPayload(
    val settings: Map<String, String>,
    val room: BackupRoomData,
    val appLocaleTags: String,
)

@Serializable
internal data class BackupRoomData(
    val appLists: List<BackupAppList>,
    val folders: List<BackupFolder>,
    val widgetPlacements: List<BackupWidgetPlacement>,
    val imageData: List<BackupImageData>,
    val settings: List<BackupSetting>,
)

@Serializable
internal data class BackupAppList(val id: String, val packagesJson: String)

@Serializable
internal data class BackupFolder(
    val id: String,
    val title: String,
    val packagesJson: String,
)

@Serializable
internal data class BackupWidgetPlacement(val slot: String, val widgetsJson: String)

@Serializable
internal data class BackupImageData(
    val id: String,
    val isTemp: Boolean,
    val imageAvailable: Boolean,
    val json: String,
)

@Serializable
internal data class BackupSetting(val key: String, val value: String, val type: String)

@Serializable
private data class RestoreJournal(
    val settings: Map<String, String>,
    val room: BackupRoomData,
)

data class BackupSummary(
    val appListCount: Int,
    val folderCount: Int,
    val wallpaperIncluded: Boolean,
)

data class RestorePreview(
    val createdAtEpochMs: Long,
    val sourceVersionName: String,
    val appListCount: Int,
    val folderCount: Int,
    val wallpaperIncluded: Boolean,
)

data class RestoreResult(
    val appLocaleTags: String,
    val restoredWallpaperPath: String?,
)

class InvalidBackupException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

object BackupRestoreManager {

    fun suggestedFileName(now: Date = Date()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        return "Comfer-backup-$date.zip"
    }

    suspend fun createBackup(
        context: Context,
        destination: Uri,
        appLocaleTags: String,
    ): BackupSummary = withContext(Dispatchers.IO) {
        StartupCoordinator.awaitReady()
        val settings = PreferenceManager.snapshotForBackup()
        val room = ComferRepository.snapshot(context).toBackupData()
        val payload = BackupPayload(settings, room, appLocaleTags)
        validateBackupPayload(payload)
        val payloadBytes = backupJson.encodeToString(payload).encodeToByteArray()
        requireWithinLimit(payloadBytes.size.toLong(), MAX_JSON_BYTES, "Backup data")

        val automaticWallpaperEnabled = isAutomaticWallpaperEnabled(settings)
        val wallpaperFile = if (automaticWallpaperEnabled) {
            settings[PreferenceManager.PREF_BACKGROUND_IMAGE]
                ?.let(::File)
                ?.takeIf(File::isFile)
        } else {
            null
        }
        if (wallpaperFile != null) {
            requireWithinLimit(wallpaperFile.length(), MAX_WALLPAPER_BYTES, "Wallpaper")
        }
        val wallpaperEntry = wallpaperFile?.let { file ->
            val extension = file.extension
                .lowercase(Locale.US)
                .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
                ?: "img"
            "wallpaper/current.$extension"
        }
        val wallpaperHash = wallpaperFile?.let(::sha256)
        val manifest = BackupManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            packageName = context.packageName,
            sourceVersionCode = BuildConfig.VERSION_CODE,
            sourceVersionName = BuildConfig.VERSION_NAME,
            createdAtEpochMs = System.currentTimeMillis(),
            payloadSha256 = sha256(payloadBytes),
            wallpaperEntry = wallpaperEntry,
            wallpaperSha256 = wallpaperHash,
            wallpaperSize = wallpaperFile?.length(),
        )
        val manifestBytes = backupJson.encodeToString(manifest).encodeToByteArray()

        val output = context.contentResolver.openOutputStream(destination, "w")
            ?: throw IOException("The selected backup destination cannot be opened")
        output.buffered().use { buffered ->
            ZipOutputStream(buffered).use { zip ->
                zip.writeEntry(MANIFEST_ENTRY, manifestBytes)
                zip.writeEntry(PAYLOAD_ENTRY, payloadBytes)
                if (wallpaperFile != null && wallpaperEntry != null) {
                    zip.putNextEntry(ZipEntry(wallpaperEntry))
                    wallpaperFile.inputStream().buffered().use { input ->
                        input.copyToLimited(zip, MAX_WALLPAPER_BYTES, "Wallpaper")
                    }
                    zip.closeEntry()
                }
            }
        }
        BackupSummary(
            appListCount = room.appLists.size,
            folderCount = room.folders.size,
            wallpaperIncluded = wallpaperFile != null,
        )
    }

    suspend fun inspectBackup(context: Context, source: Uri): RestorePreview =
        withContext(Dispatchers.IO) {
            withStagedArchive(context, source) { archive ->
                val validated = readAndValidateArchive(
                    expectedPackageName = context.packageName,
                    archive = archive,
                    loadWallpaper = false,
                )
                RestorePreview(
                    createdAtEpochMs = validated.manifest.createdAtEpochMs,
                    sourceVersionName = validated.manifest.sourceVersionName,
                    appListCount = validated.payload.room.appLists.size,
                    folderCount = validated.payload.room.folders.size,
                    wallpaperIncluded = validated.shouldRestoreWallpaper &&
                        validated.manifest.wallpaperEntry != null,
                )
            }
        }

    suspend fun restoreBackup(context: Context, source: Uri): RestoreResult =
        withContext(Dispatchers.IO) {
            StartupCoordinator.awaitReady()
            withStagedArchive(context, source) { archive ->
                val validated = readAndValidateArchive(
                    expectedPackageName = context.packageName,
                    archive = archive,
                    loadWallpaper = true,
                )
                val previousSettings = PreferenceManager.snapshotForBackup()
                val previousRoom = ComferRepository.snapshot(context)
                writeRestoreJournal(
                    context,
                    RestoreJournal(previousSettings, previousRoom.toBackupData()),
                )

                var restoredWallpaper: File? = null
                try {
                    val restoredSettings = validated.payload.settings.toMutableMap()
                    restoredSettings.remove(PreferenceManager.APPLIED_WALLPAPER_IMAGE)
                    removeUnavailableUriSettings(context, restoredSettings)

                    if (validated.shouldRestoreWallpaper && validated.wallpaperBytes != null) {
                        restoredWallpaper = writeRestoredWallpaper(
                            context,
                            validated.manifest.wallpaperEntry.orEmpty(),
                            validated.wallpaperBytes,
                        )
                        restoredSettings[PreferenceManager.PREF_BACKGROUND_IMAGE] =
                            restoredWallpaper.absolutePath
                    } else {
                        restoredSettings.remove(PreferenceManager.PREF_BACKGROUND_IMAGE)
                    }

                    val portableRoom = validated.payload.room
                        .toRoomSnapshot()
                        .withOnlyValidWidgetBindings(context)
                    ComferRepository.replaceSnapshot(context, portableRoom)
                    PreferenceManager.replaceSnapshot(context, restoredSettings)
                    deleteRestoreJournal(context)

                    deleteSupersededRestoredWallpaper(
                        context = context,
                        previousPath = previousSettings[PreferenceManager.PREF_BACKGROUND_IMAGE],
                        currentPath = restoredWallpaper?.absolutePath,
                    )
                    RestoreResult(
                        appLocaleTags = validated.payload.appLocaleTags,
                        restoredWallpaperPath = restoredWallpaper?.absolutePath,
                    )
                } catch (error: Exception) {
                    restoredWallpaper?.delete()
                    try {
                        ComferRepository.replaceSnapshot(context, previousRoom)
                        PreferenceManager.replaceSnapshot(context, previousSettings)
                        deleteRestoreJournal(context)
                    } catch (rollbackError: Exception) {
                        error.addSuppressed(rollbackError)
                        Log.e("BackupRestore", "Restore rollback failed", rollbackError)
                    }
                    throw error
                }
            }
        }

    /** Restores the pre-operation snapshot if the process stopped mid-restore. */
    suspend fun recoverInterruptedRestore(context: Context) = withContext(Dispatchers.IO) {
        val journalFile = restoreJournalFile(context)
        if (!journalFile.exists()) return@withContext
        try {
            val journal = backupJson.decodeFromString<RestoreJournal>(journalFile.readText())
            ComferRepository.replaceSnapshot(context, journal.room.toRoomSnapshot())
            PreferenceManager.replaceSnapshot(context, journal.settings)
            deleteRestoreJournal(context)
            Log.w("BackupRestore", "Recovered data after an interrupted restore")
        } catch (error: Exception) {
            Log.e("BackupRestore", "Could not recover interrupted restore", error)
            throw error
        }
    }

    internal data class ValidatedArchive(
        val manifest: BackupManifest,
        val payload: BackupPayload,
        val shouldRestoreWallpaper: Boolean,
        val wallpaperBytes: ByteArray?,
    )

    internal fun readAndValidateArchive(
        expectedPackageName: String,
        archive: File,
        loadWallpaper: Boolean,
    ): ValidatedArchive {
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size !in 2..3) {
                    throw InvalidBackupException("Backup has an unexpected number of files")
                }
                val duplicateName = entries.groupingBy { it.name }.eachCount()
                    .entries.firstOrNull { it.value > 1 }?.key
                if (duplicateName != null) {
                    throw InvalidBackupException("Backup contains duplicate files")
                }

                val manifestBytes = zip.readRequiredEntry(MANIFEST_ENTRY, MAX_JSON_BYTES)
                val manifest = decodeManifest(manifestBytes)
                if (manifest.packageName != expectedPackageName) {
                    throw InvalidBackupException("This backup belongs to a different app")
                }
                if (manifest.formatVersion != BACKUP_FORMAT_VERSION) {
                    throw InvalidBackupException(
                        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
                            "This backup was created by a newer, unsupported version"
                        } else {
                            "This backup format is no longer supported"
                        },
                    )
                }

                val allowedNames = setOfNotNull(
                    MANIFEST_ENTRY,
                    PAYLOAD_ENTRY,
                    manifest.wallpaperEntry,
                )
                if (entries.any { it.isDirectory || it.name !in allowedNames }) {
                    throw InvalidBackupException("Backup contains an unexpected file")
                }

                val payloadBytes = zip.readRequiredEntry(PAYLOAD_ENTRY, MAX_JSON_BYTES)
                if (sha256(payloadBytes) != manifest.payloadSha256) {
                    throw InvalidBackupException("Backup data checksum does not match")
                }
                val payload = try {
                    backupJson.decodeFromString<BackupPayload>(payloadBytes.decodeToString())
                } catch (error: Exception) {
                    throw InvalidBackupException("Backup data is malformed", error)
                }
                validateBackupPayload(payload)

                val automaticWallpaperEnabled = isAutomaticWallpaperEnabled(payload.settings)
                val shouldRestoreWallpaper = automaticWallpaperEnabled &&
                    manifest.wallpaperEntry != null
                val wallpaperBytes = if (loadWallpaper && shouldRestoreWallpaper) {
                    val expectedSize = manifest.wallpaperSize
                        ?: throw InvalidBackupException("Wallpaper size is missing")
                    requireWithinLimit(expectedSize, MAX_WALLPAPER_BYTES, "Wallpaper")
                    val bytes = zip.readRequiredEntry(
                        manifest.wallpaperEntry,
                        MAX_WALLPAPER_BYTES,
                    )
                    if (bytes.size.toLong() != expectedSize) {
                        throw InvalidBackupException("Wallpaper size does not match")
                    }
                    if (sha256(bytes) != manifest.wallpaperSha256) {
                        throw InvalidBackupException("Wallpaper checksum does not match")
                    }
                    validateWallpaperImage(bytes)
                    bytes
                } else {
                    null
                }
                return ValidatedArchive(
                    manifest,
                    payload,
                    shouldRestoreWallpaper,
                    wallpaperBytes,
                )
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("The selected file is not a valid Comfer backup", error)
        }
    }

    private fun decodeManifest(bytes: ByteArray): BackupManifest = try {
        backupJson.decodeFromString(bytes.decodeToString())
    } catch (error: Exception) {
        throw InvalidBackupException("Backup manifest is malformed", error)
    }

    internal fun validateBackupPayload(payload: BackupPayload) {
        requireUnique(payload.room.appLists.map(BackupAppList::id), "app list")
        requireUnique(payload.room.folders.map(BackupFolder::id), "folder")
        requireUnique(payload.room.widgetPlacements.map(BackupWidgetPlacement::slot), "widget page")
        requireUnique(payload.room.imageData.map(BackupImageData::id), "image data")
        requireUnique(payload.room.settings.map(BackupSetting::key), "database setting")
        payload.room.appLists.forEach {
            validatePackageList(it.packagesJson, "app list ${it.id}")
        }
        payload.room.folders.forEach {
            if (it.title.length > 200) {
                throw InvalidBackupException("Folder title is too long")
            }
            validatePackageList(it.packagesJson, "folder ${it.id}")
        }
        payload.room.widgetPlacements.forEach {
            val widgets = try {
                backupJson.decodeFromString<List<PersistableBoundWidget>>(it.widgetsJson)
            } catch (error: Exception) {
                throw InvalidBackupException("Widget placement data is malformed", error)
            }
            if (widgets.any { widget ->
                    widget.widgetId <= 0 ||
                        widget.providerPackage.isBlank() ||
                        widget.providerPackage.length > 500 ||
                        widget.providerClass.isBlank() ||
                        widget.providerClass.length > 500 ||
                        widget.gridX < 0 || widget.gridY < 0 ||
                        widget.spanX !in 1..100 || widget.spanY !in 1..100
                }
            ) {
                throw InvalidBackupException("Widget placement data contains invalid values")
            }
        }
        if (payload.settings.any { (key, value) -> key.length > 200 || value.length > 1_000_000 }) {
            throw InvalidBackupException("Backup contains an invalid setting")
        }
        if (payload.appLocaleTags.length > 1_000) {
            throw InvalidBackupException("Backup contains an invalid app language")
        }
    }

    internal fun isAutomaticWallpaperEnabled(settings: Map<String, String>): Boolean =
        settings[PreferenceManager.BATTERY_SAVER_MODE]?.toBooleanStrictOrNull() != true &&
            (settings[PreferenceManager.AUTO_WALLPAPER]?.toBooleanStrictOrNull() ?: true)

    private fun validatePackageList(json: String, label: String) {
        val packages = try {
            backupJson.decodeFromString<List<String>>(json)
        } catch (error: Exception) {
            throw InvalidBackupException("The $label is malformed", error)
        }
        if (packages.any { it.isBlank() || it.length > 500 }) {
            throw InvalidBackupException("The $label contains an invalid package name")
        }
    }

    private fun validateWallpaperImage(bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (
            bounds.outWidth !in 1..32_768 ||
            bounds.outHeight !in 1..32_768 ||
            bounds.outMimeType.isNullOrBlank()
        ) {
            throw InvalidBackupException("Wallpaper image is invalid")
        }
    }

    private fun requireUnique(values: List<String>, label: String) {
        if (
            values.any { it.isBlank() || it.length > 500 } ||
            values.distinct().size != values.size
        ) {
            throw InvalidBackupException("Backup contains an invalid or duplicate $label")
        }
    }

    private fun RoomDataSnapshot.withOnlyValidWidgetBindings(
        context: Context,
    ): RoomDataSnapshot {
        val widgetManager = AppWidgetManager.getInstance(context)
        val validPlacements = widgetPlacements.map { placement ->
            val widgets = backupJson.decodeFromString<List<PersistableBoundWidget>>(
                placement.widgetsJson,
            )
            val validWidgets = widgets.filter { widget ->
                try {
                    val provider = widgetManager.getAppWidgetInfo(widget.widgetId)?.provider
                    provider == ComponentName(widget.providerPackage, widget.providerClass)
                } catch (error: RuntimeException) {
                    Log.w("BackupRestore", "Could not validate widget ${widget.widgetId}", error)
                    false
                }
            }
            placement.copy(widgetsJson = backupJson.encodeToString(validWidgets))
        }
        return copy(widgetPlacements = validPlacements)
    }

    private inline fun <T> withStagedArchive(
        context: Context,
        source: Uri,
        block: (File) -> T,
    ): T {
        val staged = File.createTempFile("comfer_restore_", ".zip", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw IOException("The selected backup cannot be opened")
            input.buffered().use { sourceStream ->
                FileOutputStream(staged).buffered().use { destination ->
                    sourceStream.copyToLimited(destination, MAX_ARCHIVE_BYTES, "Backup archive")
                }
            }
            return block(staged)
        } finally {
            staged.delete()
        }
    }

    private fun writeRestoredWallpaper(
        context: Context,
        entryName: String,
        bytes: ByteArray,
    ): File {
        val extension = entryName.substringAfterLast('.', "img")
            .lowercase(Locale.US)
            .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: "img"
        val file = File(
            context.filesDir,
            "$RESTORED_WALLPAPER_PREFIX${System.currentTimeMillis()}.$extension",
        )
        file.outputStream().buffered().use { it.write(bytes) }
        return file
    }

    private fun removeUnavailableUriSettings(
        context: Context,
        settings: MutableMap<String, String>,
    ) {
        val grants = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
        fun hasGrant(uri: String): Boolean = grants.any { grant ->
            uri == grant || uri.startsWith("$grant/")
        }
        listOf(
            PreferenceManager.WALLPAPER_DIRECTORY,
            PreferenceManager.WALLPAPER_URI,
        ).forEach { key ->
            val value = settings[key]
            if (value != null && !hasGrant(value)) settings.remove(key)
        }
    }

    private fun deleteSupersededRestoredWallpaper(
        context: Context,
        previousPath: String?,
        currentPath: String?,
    ) {
        if (previousPath == null || previousPath == currentPath) return
        val previous = File(previousPath)
        if (
            previous.parentFile == context.filesDir &&
            previous.name.startsWith(RESTORED_WALLPAPER_PREFIX)
        ) {
            previous.delete()
        }
    }

    private fun writeRestoreJournal(context: Context, journal: RestoreJournal) {
        val atomicFile = AtomicFile(restoreJournalFile(context))
        val bytes = backupJson.encodeToString(journal).encodeToByteArray()
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(atomicFile::failWrite)
            throw IOException("Could not prepare restore recovery data", error)
        }
    }

    private fun deleteRestoreJournal(context: Context) {
        AtomicFile(restoreJournalFile(context)).delete()
    }

    private fun restoreJournalFile(context: Context): File =
        File(context.filesDir, RESTORE_JOURNAL_FILE)
}

private fun RoomDataSnapshot.toBackupData() = BackupRoomData(
    appLists = appLists.map { BackupAppList(it.id, it.packagesJson) },
    folders = folders.map { BackupFolder(it.id, it.title, it.packagesJson) },
    widgetPlacements = widgetPlacements.map {
        BackupWidgetPlacement(it.slot, it.widgetsJson)
    },
    imageData = imageData.map {
        BackupImageData(it.id, it.isTemp, it.imageAvailable, it.json)
    },
    settings = settings.map { BackupSetting(it.key, it.value, it.type) },
)

private fun BackupRoomData.toRoomSnapshot() = RoomDataSnapshot(
    appLists = appLists.map { AppListEntity(it.id, it.packagesJson) },
    folders = folders.map { AppFolderEntity(it.id, it.title, it.packagesJson) },
    widgetPlacements = widgetPlacements.map {
        WidgetPlacementEntity(it.slot, it.widgetsJson)
    },
    imageData = imageData.map {
        ImageDataEntity(it.id, it.isTemp, it.imageAvailable, it.json)
    },
    settings = settings.map { SettingEntity(it.key, it.value, it.type) },
)

private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(bytes)
    closeEntry()
}

private fun ZipFile.readRequiredEntry(name: String, maxBytes: Long): ByteArray {
    val entry = getEntry(name) ?: throw InvalidBackupException("Backup is missing $name")
    if (entry.isDirectory) throw InvalidBackupException("Backup entry $name is invalid")
    if (entry.size > maxBytes) throw InvalidBackupException("Backup entry $name is too large")
    return getInputStream(entry).buffered().use {
        it.readBytesLimited(maxBytes, "Backup entry $name")
    }
}

private fun InputStream.readBytesLimited(maxBytes: Long, label: String): ByteArray {
    val output = ByteArrayOutputStream()
    copyToLimited(output, maxBytes, label)
    return output.toByteArray()
}

private fun InputStream.copyToLimited(
    output: java.io.OutputStream,
    maxBytes: Long,
    label: String,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        requireWithinLimit(total, maxBytes, label)
        output.write(buffer, 0, count)
    }
    return total
}

private fun requireWithinLimit(value: Long, maximum: Long, label: String) {
    if (value < 0 || value > maximum) {
        throw InvalidBackupException("$label is too large")
    }
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it.toInt() and 0xff) }
