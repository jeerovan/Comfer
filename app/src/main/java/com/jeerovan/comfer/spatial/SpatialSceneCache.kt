package com.jeerovan.comfer.spatial

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal val LOCAL_SCENE_RETENTION_MS = TimeUnit.DAYS.toMillis(30)

/** Disk-only policy. All callers (including readers) must hold the repository mutex.
 * PNG is already losslessly compressed: store it unchanged, deflate text meshes.
 * An archive is published before removing its expanded source; restoration is staged. */
internal class SpatialSceneCache(
    private val localRoot: File,
    private val cloudRoot: File,
    private val valid: (File) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val checkpoint: () -> Unit = {},
) {
    private val archives get() = File(localRoot, "archives")
    private val marker get() = File(localRoot, ".active")
    private data class Active(val key: String, val cloud: Boolean, val source: String)
    private fun checkKey(key: String) = require(key.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,180}")))
    fun directory(key: String, cloud: Boolean): File {
        checkKey(key)
        return File(if (cloud) cloudRoot else localRoot, key)
    }
    fun archiveFile(key: String): File { checkKey(key); return File(archives, "$key.zip") }
    private fun active(): Active? = try {
        val properties = Properties().apply { marker.inputStream().use { load(it) } }
        val key = properties.getProperty("key").also { checkKey(it) }
        Active(key, properties.getProperty("cloud").toBooleanStrict(), properties.getProperty("source") ?: error("Missing source"))
    } catch (_: Exception) { null }

    /** Called even when motion is disabled, so a previous cloud scene cannot accumulate. */
    fun select(source: String?) {
        val previous = active() ?: return
        if (previous.source == source) return
        // Last use ends when the wallpaper changes, not when it was first generated.
        directory(previous.key, previous.cloud).setLastModified(now())
        marker.delete()
        cloudRoot.listFiles()?.forEach { checkpoint(); it.deleteRecursively() }
    }

    fun activate(key: String, cloud: Boolean, source: String) {
        val scene = directory(key, cloud)
        check(valid(scene))
        check(localRoot.isDirectory || localRoot.mkdirs())
        val temporary = File(localRoot, ".active.tmp")
        try {
            val properties = Properties().apply {
                setProperty("key", key); setProperty("cloud", cloud.toString()); setProperty("source", source)
            }
            FileOutputStream(temporary).use { properties.store(it, null); it.fd.sync() }
            check(temporary.renameTo(marker))
            scene.setLastModified(now())
            if (!cloud) archiveFile(key).delete()
        } finally { temporary.delete() }
    }

    fun invalidate(key: String, cloud: Boolean) {
        directory(key, cloud).deleteRecursively()
        if (!cloud) archiveFile(key).delete()
    }

    /** Existing flat local caches remain readable; migrate the requested legacy cloud scene. */
    fun find(key: String, cloud: Boolean): File? {
        val destination = directory(key, cloud)
        if (cloud && !destination.exists()) {
            val legacy = File(localRoot, key)
            if (valid(legacy)) {
                check(cloudRoot.isDirectory || cloudRoot.mkdirs())
                check(legacy.renameTo(destination))
            }
        }
        if (valid(destination)) return destination
        if (cloud) return null
        val archive = archiveFile(key)
        if (!archive.isFile) return null
        if (expired(archive)) { archive.delete(); return null }
        val staging = File(localRoot, "$key.restore")
        staging.deleteRecursively()
        try {
            check(staging.mkdirs())
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                val seen = mutableSetOf<String>()
                var total = 0L
                while (entries.hasMoreElements()) {
                    checkpoint()
                    val entry = entries.nextElement()
                    require(!entry.isDirectory && entry.name in allowedNames && seen.add(entry.name))
                    require(seen.size <= 9)
                    val limit = when {
                        entry.name == "ready" -> 16L
                        entry.name.endsWith(".depth") -> 1024L * 1024
                        else -> 32L * 1024 * 1024
                    }
                    val crc = CRC32()
                    var size = 0L
                    zip.getInputStream(entry).use { input ->
                        File(staging, entry.name).outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                checkpoint()
                                val count = input.read(buffer)
                                if (count < 0) break
                                size += count; total += count
                                require(size <= limit && total <= 128L * 1024 * 1024)
                                crc.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(size == entry.size && crc.value == entry.crc)
                }
                require(valid(staging))
                require(seen == sceneFiles(staging).map { it.name }.toSet())
            }
            checkpoint()
            destination.deleteRecursively()
            check(staging.renameTo(destination))
            // Archive remains until activate succeeds; interrupted restores never lose the only copy.
            return destination
        } catch (_: IOException) { return null }
        catch (_: IllegalArgumentException) { return null }
        finally { staging.deleteRecursively() }
    }

    /** Only the active cloud scene and active expanded local scene are retained.
     * A failed archive leaves the expanded scene intact and can be retried later. */
    fun pruneExpired() {
        archives.listFiles()?.filter { it.extension == "zip" && expired(it) }?.forEach { checkpoint(); it.delete() }
    }

    fun maintain() {
        val current = active()
        pruneExpired()
        cloudRoot.listFiles()?.filter { current?.cloud != true || it.name != current.key }?.forEach {
            checkpoint(); it.deleteRecursively()
        }
        localRoot.listFiles()?.filter { it.isDirectory && it.name != "archives" }?.forEach { scene ->
            checkpoint()
            if (current?.cloud == false && scene.name == current.key) return@forEach
            // Remove obsolete cloud entries left in the former shared cache and interrupted work.
            if (scene.name.contains("-cloud-") || scene.name.endsWith(".partial") || scene.name.endsWith(".restore")) {
                scene.deleteRecursively()
            } else if (expired(scene) || !valid(scene)) {
                scene.deleteRecursively()
            } else {
                try { archive(scene) } catch (_: IOException) { /* Keep source on full disk / I/O failure. */ }
            }
        }
        archives.listFiles()?.filter { it.name.endsWith(".partial") }?.forEach { it.delete() }
    }

    private fun expired(file: File): Boolean {
        val time = now(); val lastUse = file.lastModified()
        // Clock rollback must not accidentally expire recent data.
        return time >= lastUse && time - lastUse >= LOCAL_SCENE_RETENTION_MS
    }

    private fun archive(scene: File) {
        val destination = archiveFile(scene.name)
        val used = scene.lastModified()
        if (!archives.isDirectory && !archives.mkdirs()) throw IOException("Cannot create archive directory")
        val temporary = File(archives, "${scene.name}.zip.partial")
        try {
            FileOutputStream(temporary).use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED)
                    val buffer = ByteArray(64 * 1024)
                    for (file in sceneFiles(scene)) {
                        checkpoint()
                        val entry = ZipEntry(file.name)
                        if (file.extension == "png") {
                            val crc = CRC32()
                            file.inputStream().use { input ->
                                while (true) {
                                    checkpoint()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    crc.update(buffer, 0, count)
                                }
                            }
                            entry.method = ZipEntry.STORED; entry.size = file.length(); entry.crc = crc.value
                        }
                        zip.putNextEntry(entry)
                        file.inputStream().use { input ->
                            while (true) {
                                checkpoint()
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                            }
                        }
                        zip.closeEntry()
                    }
                    zip.finish(); zip.flush(); raw.fd.sync()
                }
            }
            checkpoint()
            if (!temporary.renameTo(destination)) throw IOException("Cannot publish scene archive")
            if (!destination.setLastModified(used)) throw IOException("Cannot preserve scene last-use time")
            scene.deleteRecursively()
        } finally { temporary.delete() }
    }

    private fun sceneFiles(scene: File): List<File> =
        spatialLayerNames(File(scene, "ready").readText().toInt()).flatMap { name ->
            listOf(File(scene, "$name.png"), File(scene, "$name.depth"))
        } + File(scene, "ready")

    private val allowedNames = spatialLayerNames(MAX_SPATIAL_LAYERS).flatMap { listOf("$it.png", "$it.depth") }.toSet() + "ready"
}
