package dev.brahmkshatriya.echo.extension.models

import org.json.JSONArray
import org.json.JSONObject

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

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("extensionId", extensionId)
        .put("title", title)
        .put("type", type.name)
        .put("lastSynced", lastSynced)
        .put("tracks", JSONArray(tracks.map { t ->
            JSONObject()
                .put("trackId", t.trackId)
                .put("sortOrder", t.sortOrder)
                .put("addedAt", t.addedAt)
        }))
        .toString()

    companion object {
        private val ILLEGAL = "[/\\\\:*?\"<>|]".toRegex()
        fun sanitize(s: String) = ILLEGAL.replace(s, "_")

        fun fromJson(json: String): DownloadManifest {
            val root = JSONObject(json)
            val tracks = root.getJSONArray("tracks").map { item ->
                val obj = item as JSONObject
                ManifestTrack(
                    trackId = obj.getString("trackId"),
                    sortOrder = obj.optInt("sortOrder").takeUnless { obj.isNull("sortOrder") },
                    addedAt = obj.getLong("addedAt")
                )
            }
            return DownloadManifest(
                id = root.getString("id"),
                extensionId = root.getString("extensionId"),
                title = root.getString("title"),
                type = ContextType.valueOf(root.getString("type")),
                lastSynced = root.getLong("lastSynced"),
                tracks = tracks
            )
        }

        /** Build a stable track key consistent across the whole app. */
        fun trackKey(extensionId: String, trackId: String) =
            "${extensionId}_${sanitize(trackId)}"
    }
}
