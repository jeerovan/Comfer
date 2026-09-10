package com.jeerovan.comfer.notifications

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
internal data class SavedNotification(
    val id: String, val app: String, val profile: Int, val title: String, val text: String,
    val postedAt: Long, val savedAt: Long,
    val keepInInbox: Boolean = false,
) { val appId: String get() = "$profile:$app" }

/** Only encrypted text DTOs in noBackupFilesDir; never persist platform action handles. */
internal class NotificationHistoryStore(
    private val directory: File,
    private val cipher: NotificationHistoryCipher = NotificationHistoryCipher(),
) {
    fun read(): List<SavedNotification> {
        check(directory.exists() || directory.mkdirs())
        val files = directory.listFiles().orEmpty().filter { it.name.endsWith(".record") || it.name.endsWith(".record.bak") }
        cipher.initializeEmptyStoreIfNeeded(files.isNotEmpty())
        return files.map { it.name.removeSuffix(".bak") }.distinct().map { name ->
            val file = File(directory, name)
            val atomic = AtomicFile(file)
            val bytes = atomic.openRead().use { it.readBytesBounded() }
            Json.decodeFromString<SavedNotification>(cipher.open(name.removeSuffix(".record"), bytes).decodeToString())
        }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            check(output.size() + n <= 16 * 1024 + 30)
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
    fun write(record: SavedNotification) {
        val bytes = cipher.seal(record.id, Json.encodeToString(record).encodeToByteArray())
        val file = AtomicFile(File(directory, "${record.id}.record"))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun delete(id: String) { AtomicFile(File(directory, "$id.record")).delete() }
    fun clear() {
        directory.listFiles().orEmpty().forEach { check(it.delete()) }
        cipher.deleteKey()
        cipher.initializeEmptyStore(false)
    }
}

internal fun retainedHistory(records: List<SavedNotification>, config: NotificationConfiguration, now: Long): List<SavedNotification> =
    records.filter { hasNotificationPreview(it.title, it.text) && it.savedAt > now - config.historyDays * 86_400_000L && it.appId !in config.historyExcludedApps && it.appId !in config.protectedApps }
        .sortedByDescending { it.savedAt }.take(500)

internal object NotificationHistory {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(128)
    private val mutable = MutableStateFlow<List<SavedNotification>>(emptyList())
    val records = mutable.asStateFlow()
    private val errorState = MutableStateFlow(false)
    val failed = errorState.asStateFlow()
    private val gapState = MutableStateFlow(false)
    val captureGap = gapState.asStateFlow()
    private var store: NotificationHistoryStore? = null
    private var redactionPlaceholder: String? = null
    @Volatile private var initialized = false
    @Volatile private var epoch = 0L
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        NotificationPreferences.initialize(context)
        redactionPlaceholder = notificationRedactionPlaceholder(context)
        initialized = true
        store = NotificationHistoryStore(File(context.applicationContext.noBackupFilesDir, "notification-history"))
        scope.launch {
            try { mutable.value = store!!.read(); prune() } catch (_: Exception) { fail() }
            for (work in queue) try { work() } catch (_: Exception) { fail() }
        }
        scope.launch { while (true) { delay(60_000); queue.send { if (!errorState.value) prune() } } }
    }
    private suspend fun fail() {
        errorState.value = true
        NotificationPreferences.update { it.copy(historyEnabled = false) }
    }
    private fun prune() {
        val retained = retainedHistory(mutable.value, NotificationPreferences.state.value, System.currentTimeMillis())
            .filter { hasNotificationPreview(it.title, it.text, redactionPlaceholder) }
        val keep = retained.map { it.id }.toSet()
        mutable.value.filter { it.id !in keep }.forEach { store!!.delete(it.id) }
        mutable.value = retained
    }
    fun offer(item: NotificationItem, canCapture: Boolean, keepInInbox: Boolean = false) {
        val config = NotificationPreferences.state.value
        if (!initialized || !config.historyEnabled || errorState.value) return
        if (!canCapture) { gapState.value = true; return }
        if (item.summary || item.protected || item.appId in config.protectedApps || item.appId in config.historyExcludedApps ||
            (!item.previewAvailable || !item.contentComplete || !hasNotificationPreview(item.title, item.text, redactionPlaceholder))) return
        val generation = epoch
        val consent = config.historySince
        val now = System.currentTimeMillis()
        val id = savedNotificationId(item)
        val copy = SavedNotification(id, item.app.take(256), item.profile, item.title.take(256), item.text.take(2048), item.postedAt, now, keepInInbox)
        if (!queue.trySend {
            val current = NotificationPreferences.state.value
            if (!errorState.value && generation == epoch && current.historyEnabled && current.historySince == consent && copy.appId !in current.historyExcludedApps && copy.appId !in current.protectedApps) {
                val previous = mutable.value.firstOrNull { it.id == id }
                val retained = copy.copy(keepInInbox = keepInInbox || previous?.keepInInbox == true)
                if (previous?.copy(savedAt = now) != retained) {
                    store!!.write(retained)
                    mutable.value = mutable.value.filterNot { it.id == id } + retained
                    prune()
                }
            }
        }.isSuccess) gapState.value = true
    }
    suspend fun refresh() = execute { if (!errorState.value) prune() }
    suspend fun retainOpened(item: NotificationItem): Boolean {
        offer(item, canCapture = true, keepInInbox = true)
        refresh()
        return mutable.value.any { it.id == savedNotificationId(item) && it.keepInInbox }
    }
    suspend fun dismissFromInbox(id: String) = execute {
        val current = mutable.value.firstOrNull { it.id == id } ?: return@execute
        val changed = current.copy(keepInInbox = false)
        store!!.write(changed)
        mutable.value = mutable.value.map { if (it.id == id) changed else it }
    }
    suspend fun delete(id: String) = execute {
        epoch++
        store!!.delete(id)
        mutable.value = mutable.value.filterNot { it.id == id }
    }
    suspend fun clear() = execute {
        epoch++
        store!!.clear()
        mutable.value = emptyList()
        errorState.value = false
        gapState.value = false
    }
    private suspend fun execute(action: () -> Unit): Boolean {
        val done = CompletableDeferred<Boolean>()
        queue.send { try { action(); done.complete(true) } catch (_: Exception) { fail(); done.complete(false) } }
        return done.await()
    }
}

internal fun savedNotificationId(item: NotificationItem): String = MessageDigest.getInstance("SHA-256")
    .digest("${item.profile}:${item.key}:${item.postedAt}".encodeToByteArray()).joinToString("") { "%02x".format(it) }

internal fun retainedInboxCopies(saved: List<SavedNotification>, live: List<NotificationItem>, enabled: Boolean): List<SavedNotification> {
    if (!enabled) return emptyList()
    val current = live.map(::savedNotificationId).toSet()
    return saved.filter { it.keepInInbox && it.id !in current }
}
