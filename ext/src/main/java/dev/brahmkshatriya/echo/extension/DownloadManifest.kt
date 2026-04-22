package dev.brahmkshatriya.echo.extension

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

/**
 * Represents a persisted playlist/album manifest on disk.
 * Lives at: Echo/playlists/{extensionId}_{contextId}.json
 */
data class DownloadManifest(
    val id: String,               // e.g. "spotify:playlist:abc"
    val extensionId: String,
    val title: String,
    val type: ContextType,
    val lastSynced: Long,         // epoch ms
    val tracks: List<ManifestTrack>
) {
    enum class ContextType { PLAYLIST, ALBUM, RADIO }

    data class ManifestTrack(
        val trackId: String,      // stable key: "{extensionId}_{track.id}"
        val sortOrder: Int?,
        val addedAt: Long         // epoch ms
    )

    /** Stable filename for this manifest. */
    fun fileName() = "${extensionId}_${sanitize(id)}.json"

    @Suppress("UNCHECKED_CAST")
    fun toJson(): String {
        val root = JSONObject()
        root["id"] = id
        root["extensionId"] = extensionId
        root["title"] = title
        root["type"] = type.name
        root["lastSynced"] = lastSynced
        val arr = JSONArray()
        tracks.forEach { t ->
            val obj = JSONObject()
            obj["trackId"] = t.trackId
            obj["sortOrder"] = t.sortOrder
            obj["addedAt"] = t.addedAt
            arr.add(obj)
        }
        root["tracks"] = arr
        return root.toJSONString()
    }

    companion object {
        private val ILLEGAL = "[/\\\\:*?\"<>|]".toRegex()
        fun sanitize(s: String) = ILLEGAL.replace(s, "_")

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String): DownloadManifest {
            val parser = JSONParser()
            val root = parser.parse(json) as JSONObject
            val arr = root["tracks"] as JSONArray
            val tracks = arr.map { item ->
                val obj = item as JSONObject
                ManifestTrack(
                    trackId = obj["trackId"] as String,
                    sortOrder = (obj["sortOrder"] as? Long)?.toInt(),
                    addedAt = obj["addedAt"] as Long
                )
            }
            return DownloadManifest(
                id = root["id"] as String,
                extensionId = root["extensionId"] as String,
                title = root["title"] as String,
                type = ContextType.valueOf(root["type"] as String),
                lastSynced = root["lastSynced"] as Long,
                tracks = tracks
            )
        }

        /** Build a stable track key consistent across the whole app. */
        fun trackKey(extensionId: String, trackId: String) =
            "${extensionId}_${sanitize(trackId)}"
    }
}
