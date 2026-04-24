package dev.brahmkshatriya.echo.extension.models

import org.json.JSONArray
import org.json.JSONObject
import dev.brahmkshatriya.echo.extension.utils.EDLUtils

/**
 * Persisted playlist/album manifest.
 *
 * File layout:
 *   {echoRoot}/playlists/{extensionId}_{sanitizedContextId}.json  ← playlist manifest
 *   {echoRoot}/metadata/{trackKey}.json                           ← per-track metadata
 *   {echoRoot}/tracks/{Artist} - {Title}_{sanitizedTrackId}.{ext} ← audio file
 *   {echoRoot}/lyrics/{trackKey}.lrc | .txt                       ← lyrics file
 *
 * Reference chain:
 *   playlists/{fileName}.json → TrackManifest.trackId → metadata/{trackKey}.json
 *                                             → tracks/{fileName}_{trackKey}.{ext}
 *                                             → lyrics/{trackKey}.lrc
 */
data class DownloadManifest(
    val id: String,           // e.g. "spotify:playlist:abc"
    val extensionId: String,
    val title: String,
    val type: ContextType,
    val lastSynced: Long,     // epoch ms
    val tracks: List<TrackManifest>
) {
    enum class ContextType { PLAYLIST, ALBUM, RADIO }

    /** Stable filename for this manifest on disk. */
    fun fileName() = "${extensionId}_${EDLUtils.illegalReplace(id)}.json"

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
        fun fromJson(json: String): DownloadManifest {
            val root = JSONObject(json)
            val tracks = root.getJSONArray("tracks").let { arr ->
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    TrackManifest(
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
         * Build a stable, filesystem-safe track key
         * used as the primary identifierbin manifests and as the suffix of audio filenames.
         */
        fun trackKey(extensionId: String, trackId: String): String =
            "${extensionId}_${EDLUtils.illegalReplace(trackId)}"
    }
}
