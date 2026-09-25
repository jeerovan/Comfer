package com.jeerovan.comfer.spatial

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpatialSceneCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private var time = TimeUnit.DAYS.toMillis(100)
    private val root by lazy { temporary.newFolder("local") }
    private val cloud by lazy { temporary.newFolder("cloud") }
    private fun valid(file: File) = File(file, "ready").readTextOrNull() == "1" &&
        File(file, "background.png").isFile && File(file, "background.depth").isFile
    private fun File.readTextOrNull() = if (isFile) readText() else null
    private fun cache(checkpoint: () -> Unit = {}) = SpatialSceneCache(root, cloud, ::valid, { time }, checkpoint)
    private fun scene(key: String, isCloud: Boolean = false): File = cache().directory(key, isCloud).apply {
        mkdirs()
        File(this, "background.png").writeBytes(ByteArray(4096) { (it % 251).toByte() })
        File(this, "background.depth").writeText("0.123456 ".repeat(4700))
        File(this, "ready").writeText("1")
        setLastModified(time)
    }

    @Test fun rotationBeyondThreePhotosRestoresExactAssetsWithoutRegeneration() {
        val cache = cache()
        val first = scene("photo0")
        val expected = first.listFiles()!!.associate { it.name to it.readBytes() }
        cache.activate("photo0", false, "source0")
        for (i in 1..5) {
            time += 1000
            cache.select("source$i")
            scene("photo$i")
            cache.activate("photo$i", false, "source$i")
            cache.maintain()
        }
        assertEquals(5, File(root, "archives").listFiles()!!.count { it.extension == "zip" })
        assertFalse(first.exists())
        assertTrue(cache.archiveFile("photo0").length() < expected.values.sumOf { it.size })
        ZipFile(cache.archiveFile("photo0")).use {
            assertEquals(ZipEntry.STORED, it.getEntry("background.png").method)
            assertEquals(ZipEntry.DEFLATED, it.getEntry("background.depth").method)
        }
        cache.select("source0")
        val restored = cache.find("photo0", false) ?: error("Expected cached scene")
        expected.forEach { (name, bytes) -> assertArrayEquals(bytes, File(restored, name).readBytes()) }
        cache.activate("photo0", false, "source0")
        cache.maintain()
        assertFalse(cache.archiveFile("photo0").exists())
        assertTrue(restored.isDirectory)
        assertTrue(cache.archiveFile("photo5").isFile)
    }

    @Test fun expiresThirtyDaysAfterLastUseAndDoesNotExpireActiveScene() {
        val cache = cache()
        scene("a"); cache.activate("a", false, "A")
        // Keeping a wallpaper applied for weeks still counts as use until switched away.
        time += LOCAL_SCENE_RETENTION_MS * 2
        cache.maintain()
        assertTrue(cache.directory("a", false).isDirectory)
        cache.select("B"); scene("b"); cache.activate("b", false, "B"); cache.maintain()
        val lastUse = time
        assertEquals(lastUse, cache.archiveFile("a").lastModified())
        time = lastUse + LOCAL_SCENE_RETENTION_MS - 1
        cache.maintain(); assertTrue(cache.archiveFile("a").exists())
        time++
        cache.maintain(); assertFalse(cache.archiveFile("a").exists())
        assertTrue(cache.directory("b", false).exists())
    }

    @Test fun restoringAndReusingResetsExpiryAndClockRollbackKeepsData() {
        val cache = cache()
        scene("a"); cache.activate("a", false, "A"); cache.select("B"); cache.maintain()
        time += LOCAL_SCENE_RETENTION_MS - 1
        assertNotNull(cache.find("a", false)); cache.activate("a", false, "A")
        time += 1000
        cache.select("B"); cache.maintain()
        val reused = time
        time -= LOCAL_SCENE_RETENTION_MS * 3
        cache.maintain(); assertTrue(cache.archiveFile("a").exists())
        time = reused + LOCAL_SCENE_RETENTION_MS - 1
        cache.maintain(); assertTrue(cache.archiveFile("a").exists())
    }

    @Test fun cloudKeepsOnlyCurrentAndNeverEvictsLocalArchives() {
        val cache = cache()
        scene("local"); cache.activate("local", false, "local-photo")
        cache.select("cloud1"); scene("cloud1", true); cache.activate("cloud1", true, "cloud1"); cache.maintain()
        assertTrue(cache.archiveFile("local").exists())
        cache.select("cloud2"); scene("cloud2", true); cache.activate("cloud2", true, "cloud2"); cache.maintain()
        assertEquals(listOf("cloud2"), cloud.listFiles()!!.map { it.name })
        assertTrue(cache.archiveFile("local").exists())
        // Wallpaper changes while motion is off: housekeeping still removes previous cloud data.
        cache.select("cloud3"); cache.maintain()
        assertTrue(cloud.listFiles()!!.isEmpty())
    }

    @Test fun failedCompressionPreservesSourceAndCleansPartialArchive() {
        val healthy = cache()
        val source = scene("a"); healthy.activate("a", false, "A"); healthy.select("B")
        var calls = 0
        cache { if (++calls == 5) throw IOException("Disk full") }.maintain()
        assertTrue(valid(source))
        assertFalse(cache().archiveFile("a").exists())
        assertTrue(File(root, "archives").listFiles()?.none { it.name.endsWith(".partial") } != false)
        healthy.maintain()
        assertTrue(healthy.archiveFile("a").exists())
        assertFalse(source.exists())
    }

    @Test fun corruptAndUnsafeArchivesCannotPublishPartialScenes() {
        val cache = cache()
        val archive = cache.archiveFile("bad").apply { parentFile!!.mkdirs() }
        archive.writeText("truncated archive"); archive.setLastModified(time)
        assertNull(cache.find("bad", false))
        assertFalse(cache.directory("bad", false).exists())
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../outside")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        archive.setLastModified(time)
        assertNull(cache.find("bad", false))
        assertFalse(File(root, "outside").exists())
        assertFalse(File(root, "bad.restore").exists())
    }

    @Test fun interruptedRestoreKeepsArchiveForNextAttempt() {
        val healthy = cache()
        scene("a"); healthy.activate("a", false, "A"); healthy.select("B"); healthy.maintain()
        var calls = 0
        val cancelled = cache { if (++calls == 3) throw kotlinx.coroutines.CancellationException("Changed wallpaper") }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { cancelled.find("a", false) }
        assertTrue(healthy.archiveFile("a").isFile)
        assertFalse(File(root, "a.restore").exists())
        assertNotNull(healthy.find("a", false))
    }

    @Test fun legacyFlatLocalSceneIsArchivedWithoutRegeneration() {
        val cache = cache()
        scene("existing-photo")
        cache.maintain()
        assertTrue(cache.archiveFile("existing-photo").exists())
        assertNotNull(cache.find("existing-photo", false))
    }
}
