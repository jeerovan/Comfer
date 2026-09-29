package com.jeerovan.comfer.spatial

import com.jeerovan.comfer.ImageData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CloudWallpaperTest {
    private val original = ImageData(77, "https://images.example/photo?w=2000")
    private val scene = "https://comfer.jeerovan.com/api/wallpapers/77/spatial"

    @Test fun liveFeedShapeAndLegacyMetadataMatchOnlyTheAppliedCloudFile() {
        val metadata = """{"id":77,"imageUrl":"https://images.example/photo","hasSpatial":true,"spatialSceneUrl":"$scene"}"""
        assertEquals(scene, cloudWallpaperForPath("/files/comfer_77.jpg", metadata)?.spatialSceneUrl)
        assertEquals(original, cloudWallpaperForPath("/files/comfer_77.jpg", Json.encodeToString(original)))
        for (path in listOf(null, "/files/local.jpg", "/files/comfer_78.jpg", "/files/not_comfer_77.jpg")) {
            assertNull(cloudWallpaperForPath(path, metadata))
        }
        for (invalid in listOf(null, "null", "{}", "invalid")) {
            assertNull(cloudWallpaperForPath("/files/comfer_77.jpg", invalid))
        }
    }

    @Test fun oldWallpaperGetsSpatialMetadataWithoutChangingThePhoto() = runBlocking {
        val result = resolveCloudSpatialMetadata(original) { id ->
            assertEquals(77, id)
            ImageData(id, "https://images.example/photo", spatialSceneUrl = scene)
        }
        assertEquals(original.copy(spatialSceneUrl = scene), result)
    }

    @Test fun existingSceneOrDepthNeedsNoDetailRequest() = runBlocking {
        for (image in listOf(original.copy(spatialSceneUrl = scene), original.copy(depthUrl = "https://cdn.example/depth"))) {
            assertEquals(image, resolveCloudSpatialMetadata(image) { error("Unexpected request") })
        }
    }

    @Test fun unavailableAssetsPreserveOrdinaryWallpaperMotion() = runBlocking {
        assertEquals(original, resolveCloudSpatialMetadata(original) {
            original.copy(depthUrl = "", spatialSceneUrl = " ")
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentWallpaperCannotSupplyTheScene(): Unit = runBlocking {
        resolveCloudSpatialMetadata(original) { original.copy(id = 78, spatialSceneUrl = scene) }
    }

    @Test(expected = CancellationException::class)
    fun interruptedLookupCannotPublishMetadata(): Unit = runBlocking {
        resolveCloudSpatialMetadata(original) { throw CancellationException("Wallpaper changed") }
    }
}
