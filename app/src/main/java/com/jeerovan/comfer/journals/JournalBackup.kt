package com.jeerovan.comfer.journals

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class JournalMediaBackup(val id: String, val sha256: String, val bytes: String)
@Serializable
data class JournalSnapshot(val version: Int = 1, val entries: List<JournalEntry>, val drafts: List<JournalDraft>, val media: List<JournalMediaBackup>, val segments: List<JournalSegment> = emptyList(), val protectionEnabled: Boolean = false)
@Serializable
data class JournalArchive(val version: Int = 1, val encrypted: Boolean = false, val content: String, val salt: String? = null, val nonce: String? = null, val media: List<JournalExternalMedia> = emptyList(), val externalMedia: Boolean = false)

class JournalArchiveException(message: String) : java.io.IOException(message)

object JournalBackup {
    const val MIN_PASSWORD_LENGTH = 4
    private val json = Json { encodeDefaults = true }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    suspend fun snapshot(context: Context): JournalSnapshot = JournalDatabase.get(context).withTransaction {
        val dao = JournalDatabase.get(context).dao()
        val entries = dao.exportEntries()
        val drafts = dao.exportDrafts()
        val media = JournalMedia(context)
        val ids = (entries.mapNotNull { it.image } + drafts.mapNotNull { it.image }).distinct()
        var total = 0L
        val images = ids.map { id ->
            val bytes = media.read(id)
            total += bytes.size
            if (total > 12_000_000) throw JournalArchiveException("Journal media exceeds this test build’s 12 MB backup limit")
            JournalMediaBackup(id, hash(bytes), encode(bytes))
        }
        JournalSnapshot(entries = entries, drafts = drafts, media = images, segments = dao.exportSegments(), protectionEnabled = JournalDatabase.get(context).rawDao().storageState()!!.moduleLocked)
    }
    fun pack(snapshot: JournalSnapshot, password: String?): JournalArchive {
        require(!snapshot.protectionEnabled || password != null) { "Protected Journal data requires encryption" }
        val bytes = json.encodeToString(snapshot).encodeToByteArray()
        if (bytes.size > 20_000_000) throw JournalArchiveException("Journal exceeds this test build’s backup size limit")
        if (password == null) return JournalArchive(content = bytes.decodeToString())
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must contain at least 4 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD("Comfer Journal 1".encodeToByteArray())
        return JournalArchive(encrypted = true, content = encode(cipher.doFinal(bytes)), salt = encode(salt), nonce = encode(nonce))
    }
    fun unpack(archive: JournalArchive, password: String?): JournalSnapshot {
        require(archive.version == 1 && archive.content.length <= 28_000_000) { "Unsupported Journal archive" }
        val bytes = if (archive.encrypted) {
            if (password.isNullOrEmpty()) throw JournalArchiveException("Enter the Journal export password")
            val salt = decode(requireNotNull(archive.salt)); val nonce = decode(requireNotNull(archive.nonce))
            require(salt.size == 16 && nonce.size == 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
            cipher.updateAAD("Comfer Journal 1".encodeToByteArray())
            try { cipher.doFinal(decode(archive.content)) } catch (_: Exception) { throw JournalArchiveException("Wrong Journal export password, or damaged Journal data") }
        } else archive.content.encodeToByteArray()
        require(bytes.size <= 20_000_000)
        return json.decodeFromString<JournalSnapshot>(bytes.decodeToString()).also { require(!it.protectionEnabled || archive.encrypted); validate(it, if(archive.externalMedia) archive.media.map { item -> item.id }.toSet() else emptySet()) }
    }
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 1_300_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
    fun validate(snapshot: JournalSnapshot, externalIds: Set<String> = emptySet()) {
        require(snapshot.version == 1)
        require(snapshot.entries.size <= 100_000 && snapshot.drafts.size <= 100_001)
        require(snapshot.entries.map { it.id }.distinct().size == snapshot.entries.size)
        require(snapshot.drafts.map { it.key }.distinct().size == snapshot.drafts.size)
        require(snapshot.media.map { it.id }.distinct().size == snapshot.media.size)
        require(snapshot.segments.size <= 100_000)
        require(snapshot.segments.map { it.sessionId to it.segmentId }.distinct().size == snapshot.segments.size)
        val entryIds = snapshot.entries.map { it.id }.toSet()
        snapshot.segments.forEach { require(it.entryId in entryIds && it.sessionId.isNotBlank() && it.segmentId >= 0) }
        val images = snapshot.media.map { it.id }.toSet() + externalIds
        snapshot.entries.forEach {
            require(it.id.isNotBlank() && it.revision >= 0 && (it.text.isNotBlank() || it.image != null))
            java.time.LocalDate.ofEpochDay(it.day); java.time.ZoneId.of(it.zone)
            require(it.image == null || it.image in images)
        }
        snapshot.drafts.forEach { require(it.prompt in 0..3 && it.revision >= 0); java.time.LocalDate.ofEpochDay(it.day); java.time.ZoneId.of(it.zone); require(it.image == null || it.image in images) }
        var total = 0L
        snapshot.media.forEach {
            require(it.id.matches(Regex("[a-f0-9-]{36}\\.jpg")))
            require(it.bytes.length <= 2_666_672)
            val bytes = decode(it.bytes)
            total += bytes.size
            require(bytes.size in 1..2_000_000 && total <= 12_000_000 && hash(bytes) == it.sha256)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048)
        }
    }
    /** Remap immutable image IDs. Previous files remain available to rollback until collection. */
    suspend fun replace(context: Context, snapshot: JournalSnapshot) {
        JournalArchiveMedia.replaceLocal(context, stageInline(context, snapshot))
    }
    fun stageInline(context: Context, snapshot: JournalSnapshot, protect: Boolean = false): JournalLocalSnapshot {
        validate(snapshot)
        val media = JournalMedia(context)
        val created = mutableListOf<java.io.File>()
        try {
            val remap = snapshot.media.associate { image ->
                val id = "${java.util.UUID.randomUUID()}.jpg"
                val file = media.file(id); created += file
                media.write(id, decode(image.bytes), snapshot.protectionEnabled || protect)
                image.id to id
            }
            return JournalLocalSnapshot(snapshot.entries.map { it.copy(image = it.image?.let(remap::getValue)) }, snapshot.drafts.map { it.copy(image = it.image?.let(remap::getValue)) }, snapshot.segments, snapshot.protectionEnabled || protect)
        } catch(e: Exception) { created.forEach(java.io.File::delete); throw e }
    }
}
