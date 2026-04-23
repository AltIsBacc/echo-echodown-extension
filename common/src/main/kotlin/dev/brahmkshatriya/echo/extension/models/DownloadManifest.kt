package dev.brahmkshatriya.echo.extension.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted playlist/album manifest.
 *
 * File layout:
 *   {echoRoot}/playlists/{extensionId}_{sanitizedContextId}.json  ← playlist manifest
 *   {echoRoot}/metadata/{trackKey}.json                            ← per-track metadata
 *   {echoRoot}/tracks/{Artist} - {Title}_{sanitizedTrackId}.{ext} ← audio file
 *   {echoRoot}/lyrics/{trackKey}.lrc | .txt                       ← lyrics file
 *
 * Reference chain:
 *   playlists/*.json → ManifestTrack.trackId → metadata/{trackKey}.json
 *                                             → tracks/*_{trackKey}.{ext}
 *                                             → lyrics/{trackKey}.lrc
 *
 * This class lives in exactly ONE place: common/models/. Never duplicate it.
 */
data class DownloadManifest(
    val id: String,           // e.g. "spotify:playlist:abc"
    val extensionId: String,
    val title: String,
    val type: ContextType,
    val lastSynced: Long,     // epoch ms
    val tracks: List<ManifestTrack>
) {
    enum class ContextType { PLAYLIST, ALBUM, RADIO }

    data class ManifestTrack(
        val trackId: String,  // stable key: "{extensionId}_{sanitizedTrackId}"
        val sortOrder: Int?,
        val addedAt: Long     // epoch ms
    )

    /** Stable filename for this manifest on disk. */
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

        fun sanitize(s: String): String = ILLEGAL.replace(s, "_")

        fun fromJson(json: String): DownloadManifest {
            val root = JSONObject(json)
            val tracks = root.getJSONArray("tracks").let { arr ->
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ManifestTrack(
                        trackId = obj.getString("trackId"),
                        sortOrder = obj.optInt("sortOrder").takeUnless { obj.isNull("sortOrder") },
                        addedAt = obj.getLong("addedAt")
                    )
                }
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

        /**
         * Build a stable, filesystem-safe track key used as the primary identifier
         * in manifests and as the suffix of audio filenames.
         */
        fun trackKey(extensionId: String, trackId: String): String =
            "${extensionId}_${sanitize(trackId)}"
    }
}
