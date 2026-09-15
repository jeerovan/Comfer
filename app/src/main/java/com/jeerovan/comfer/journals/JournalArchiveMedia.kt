package com.jeerovan.comfer.journals

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class JournalExternalMedia(val id: String, val entry: String, val size: Long, val sha256: String, val nonce: String? = null)
@Serializable
data class JournalLocalSnapshot(val entries: List<JournalEntry>, val drafts: List<JournalDraft>, val segments: List<JournalSegment> = emptyList(), val protectionEnabled: Boolean = false)

/** Image payloads are streamed through ZIP as separate, bounded files. Only one decoded file's
 * bytes are held at a time. Staging always precedes replacement, including authenticated images. */
object JournalArchiveMedia {
    private val json = Json { encodeDefaults = true }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun un64(text: String) = Base64.decode(text, Base64.NO_WRAP)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 1_300_000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES") } finally { spec.clearPassword() }
    }
    private fun crypt(mode: Int, key: SecretKeySpec, nonce: ByteArray, bytes: ByteArray, aad: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(128, nonce)); cipher.updateAAD(aad.toByteArray())
        return cipher.doFinal(bytes)
    }
    private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
    data class Prepared(val archive: JournalArchive, val files: Map<String, File>, private val staging: File) : java.io.Closeable {
        override fun close() { staging.deleteRecursively() }
    }
    suspend fun localSnapshot(context: Context): JournalLocalSnapshot = JournalDatabase.get(context).withTransaction {
        val dao = JournalDatabase.get(context).dao()
        JournalLocalSnapshot(dao.exportEntries(), dao.exportDrafts(), dao.exportSegments(), JournalProtection.enabled(context))
    }
    suspend fun prepare(context: Context, password: String?): Prepared {
        val local = localSnapshot(context)
        if(local.protectionEnabled) { JournalProtection.requireAuthorization(); require(password != null) { "Protected Journals require an export password" } }
        val ids = (local.entries.mapNotNull { it.image } + local.drafts.mapNotNull { it.image }).distinct()
        val staging = File(context.noBackupFilesDir, "journal-export-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            if(password != null) require(password.length >= 12)
            val salt = if(password != null) random(16) else null
            val secret = password?.let { key(it, salt!!) }
            val files = linkedMapOf<String, File>()
            var total = 0L
            val media = ids.map { id ->
                val source = JournalMedia(context).file(id)
                require(source.length() in 1..2_000_000) { "A Journal image is unavailable" }
                total += source.length()
                if(total > 480L * 1024 * 1024) throw JournalArchiveException("Journal images exceed the 480 MB archive limit")
                val bytes = source.readBytes()
                validateImage(bytes)
                val nonce = secret?.let { random(12) }
                val payload = if(secret != null) crypt(Cipher.ENCRYPT_MODE, secret, nonce!!, bytes, "Comfer Journal image 1:$id") else bytes
                val entry = "journals/$id"
                val file = if(secret == null) source else File(staging, id).also { file -> FileOutputStream(file).use { it.write(payload); it.fd.sync() } }
                files[entry] = file
                JournalExternalMedia(id, entry, payload.size.toLong(), hash(payload), nonce?.let(::b64))
            }
            val metadata = json.encodeToString(JournalSnapshot(entries = local.entries, drafts = local.drafts, media = emptyList(), segments = local.segments, protectionEnabled = local.protectionEnabled)).toByteArray()
            require(metadata.size <= 20_000_000) { "Journal text exceeds the archive limit" }
            val nonce = secret?.let { random(12) }
            val content = if(secret != null) b64(crypt(Cipher.ENCRYPT_MODE, secret, nonce!!, metadata, "Comfer Journal 1")) else metadata.decodeToString()
            val archive = JournalArchive(content = content, encrypted = secret != null, salt = salt?.let(::b64), nonce = nonce?.let(::b64), media = media, externalMedia = true)
            return Prepared(archive, files, staging)
        } catch(e: Exception) { staging.deleteRecursively(); throw e }
    }
    fun validateDescriptors(archive: JournalArchive) {
        require(archive.version == 1 && archive.content.length <= 28_000_000)
        if(archive.encrypted) {
            require(archive.salt != null && un64(archive.salt).size == 16)
            require(archive.nonce != null && un64(archive.nonce).size == 12)
        } else require(archive.salt == null && archive.nonce == null)
        require(archive.media.size <= 10_000)
        require(archive.media.map { it.id }.distinct().size == archive.media.size)
        var total = 0L
        archive.media.forEach {
            require(it.id.matches(Regex("[a-f0-9-]{36}\\.jpg")) && it.entry == "journals/${it.id}")
            require(it.size in 1..2_000_016 && it.sha256.matches(Regex("[a-f0-9]{64}")))
            require(if(archive.encrypted) it.nonce != null && un64(it.nonce).size == 12 else it.nonce == null)
            total += it.size
        }
        require(total <= 481L * 1024 * 1024)
    }
    fun verifyFiles(zip: ZipFile, archive: JournalArchive) {
        validateDescriptors(archive)
        archive.media.forEach { descriptor ->
            val bytes = read(zip, descriptor)
            if(!archive.encrypted) validateImage(bytes)
        }
    }
    private fun read(zip: ZipFile, item: JournalExternalMedia): ByteArray {
        val entry = zip.getEntry(item.entry) ?: throw JournalArchiveException("A Journal image is missing from the backup")
        require(entry.size == item.size)
        val bytes = zip.getInputStream(entry).use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while(true) { val count = input.read(buffer); if(count < 0) break; total += count; require(total <= item.size); result.write(buffer, 0, count) }
            result.toByteArray()
        }
        require(bytes.size.toLong() == item.size && hash(bytes) == item.sha256) { "Journal image checksum does not match" }
        return bytes
    }
    fun stage(context: Context, zipFile: File, archive: JournalArchive, password: String?): JournalLocalSnapshot {
        validateDescriptors(archive)
        val secret = if(archive.encrypted) {
            if(password.isNullOrEmpty()) throw JournalArchiveException("Enter the Journal export password")
            val salt = un64(requireNotNull(archive.salt)); require(salt.size == 16)
            key(password, salt)
        } else null
        val metadata = if(secret != null) {
            val nonce = un64(requireNotNull(archive.nonce)); require(nonce.size == 12)
            try { crypt(Cipher.DECRYPT_MODE, secret, nonce, un64(archive.content), "Comfer Journal 1") }
            catch (_: Exception) { throw JournalArchiveException("Wrong Journal export password, or damaged Journal data") }
        } else archive.content.toByteArray()
        require(metadata.size <= 20_000_000)
        val snapshot = json.decodeFromString<JournalSnapshot>(metadata.decodeToString())
        require(!snapshot.protectionEnabled || archive.encrypted)
        JournalBackup.validate(snapshot, archive.media.map { it.id }.toSet())
        val created = mutableListOf<File>()
        try {
            val mapping = ZipFile(zipFile).use { zip -> archive.media.associate { item ->
                val encoded = read(zip, item)
                val bytes = if(secret != null) crypt(Cipher.DECRYPT_MODE, secret, un64(item.nonce!!), encoded, "Comfer Journal image 1:${item.id}") else encoded
                validateImage(bytes)
                val id = "${UUID.randomUUID()}.jpg"
                val file = JournalMedia(context).file(id); created += file
                FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
                item.id to id
            } }
            return JournalLocalSnapshot(snapshot.entries.map { it.copy(image = it.image?.let(mapping::getValue)) }, snapshot.drafts.map { it.copy(image = it.image?.let(mapping::getValue)) }, snapshot.segments, snapshot.protectionEnabled)
        } catch(e: Exception) { created.forEach(File::delete); throw e }
    }
    suspend fun replaceLocal(context: Context, snapshot: JournalLocalSnapshot) {
        val ids = (snapshot.entries.mapNotNull { it.image } + snapshot.drafts.mapNotNull { it.image }).toSet()
        JournalBackup.validate(JournalSnapshot(entries = snapshot.entries, drafts = snapshot.drafts, media = emptyList(), segments = snapshot.segments), ids)
        ids.forEach { require(JournalMedia(context).file(it).isFile) { "A recovery image is missing" } }
        val db = JournalDatabase.get(context)
        db.withTransaction {
            val dao = db.dao(); dao.clearSegments(); dao.clearDrafts(); dao.clearEntries()
            snapshot.entries.forEach { dao.insert(it) }; snapshot.drafts.forEach { dao.putDraft(it) }; snapshot.segments.forEach { dao.segment(it) }
            dao.state(JournalState(generation = dao.generation() + 1))
            JournalProtection.restoreState(context, snapshot.protectionEnabled)
        }
    }
    private fun validateImage(bytes: ByteArray) {
        require(bytes.size in 2..2_000_000 && bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte())
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType == "image/jpeg" && options.outWidth in 1..2048 && options.outHeight in 1..2048)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })
        require(decoded != null); decoded.recycle()
    }
}
