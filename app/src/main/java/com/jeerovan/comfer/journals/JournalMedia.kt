package com.jeerovan.comfer.journals

import androidx.room.withTransaction
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Private immutable files are published before a row references them. Failed commits leave an orphan,
 * never a broken committed image. No URI grant, EXIF, original name or location is retained. */
class JournalMedia(private val context: Context) {
    private val root get() = File(context.noBackupFilesDir, "journal-media").apply { mkdirs() }
    fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}\\.jpg")))
        return File(root, id)
    }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = File.createTempFile("import-", ".tmp", root)
        var bitmap: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(source).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 10_000_000) { "Choose an image smaller than 10 MB" }
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            } ?: error("The image is no longer available")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outMimeType in setOf("image/jpeg", "image/png")) { "Choose a JPEG or PNG image" }
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 20_000_000) { "Image is damaged or exceeds 20 megapixels" }
            if (bounds.outMimeType == "image/png") {
                java.io.DataInputStream(source.inputStream().buffered()).use { input ->
                    input.skipBytes(8)
                    while (input.available() > 0) {
                        val length = input.readInt()
                        require(length >= 0 && length.toLong() + 8 <= input.available()) { "Image is damaged" }
                        val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                        require(type != "acTL") { "Animated images are not supported" }
                        input.skipBytes(length + 4)
                        if (type == "IEND") break
                    }
                }
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + inSampleSize - 1) / inSampleSize > 2048) inSampleSize *= 2
            }
            bitmap = BitmapFactory.decodeFile(source.path, options) ?: error("Image is damaged")
            val orientation = ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
                val scale = (2048f / maxOf(bitmap!!.width, bitmap!!.height)).coerceAtMost(1f)
                postScale(scale, scale)
            }
            val normalized = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
            if (normalized !== bitmap) { bitmap!!.recycle(); bitmap = normalized }
            val id = "${UUID.randomUUID()}.jpg"
            val target = file(id)
            try {
                var quality = 90
                do {
                    FileOutputStream(target).use { output ->
                        check(bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Could not prepare image" }
                        output.fd.sync()
                    }
                    quality -= 10
                } while (target.length() > 2_000_000 && quality >= 30)
                require(target.length() in 1..2_000_000) { "Image cannot fit the storage limit" }
                id
            } catch (e: Exception) { target.delete(); throw e }
        } finally { bitmap?.recycle(); source.delete() }
    }
    suspend fun collect(db: JournalDatabase, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val cutoff = now - 7L * 24 * 60 * 60 * 1000
            while (true) {
                val expired = db.dao().expired(cutoff)
                if (expired.isEmpty()) break
                expired.forEach { db.dao().purge(it.id, it.revision) }
            }
            db.dao().removeOrphanSegments()
            context.noBackupFilesDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("journal-export-") && it.lastModified() < now - 24L * 60 * 60 * 1000 }?.forEach { it.deleteRecursively() }
            val references = db.dao().referencedImages().toSet()
            root.listFiles()?.forEach { file ->
                // Fresh staged/picker files may not have reached their draft transaction yet.
                if (file.name !in references && file.lastModified() < now - 24L * 60 * 60 * 1000) file.delete()
            }
        }
    }
    suspend fun thumbnail(id: String, maxEdge: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        require(maxEdge in 1..2048)
        val path = file(id).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > maxEdge) inSampleSize *= 2
        }
        BitmapFactory.decodeFile(path, options)
    }
}
