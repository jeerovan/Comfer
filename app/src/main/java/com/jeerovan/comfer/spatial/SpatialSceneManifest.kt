package com.jeerovan.comfer.spatial

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
internal data class SpatialSceneManifest(val version: Int, val layers: List<SpatialLayerAsset>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): SpatialSceneManifest {
            val result = json.decodeFromString<SpatialSceneManifest>(text)
            require(result.version == 1)
            spatialLayerNames(result.layers.size)
            result.layers.forEach { requireHttpsAsset(it.imageUrl); requireHttpsAsset(it.depthUrl) }
            return result
        }
    }
}

@Serializable
internal data class SpatialLayerAsset(val imageUrl: String, val depthUrl: String)

internal fun requireHttpsAsset(url: String) {
    val uri = URI(url)
    require(uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.userInfo == null)
}
