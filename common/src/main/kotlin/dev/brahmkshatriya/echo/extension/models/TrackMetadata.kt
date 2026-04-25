package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted metadata for a single downloaded track.
 *
 * Stored in metadata/{extensionId}_{trackId}.json
 */
data class TrackMetadata(
    val trackId: String,
    val extensionId: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val releaseDate: String?,
    val genres: List<String>,
    val savedAt: Long  // epoch ms
) {

    fun toJson(): String = JSONObject()
        .put("trackId", trackId)
        .put("extensionId", extensionId)
        .put("title", title)
        .put("artists", JSONArray(artists))
        .put("album", album)
        .put("releaseDate", releaseDate)
        .put("genres", JSONArray(genres))
        .put("savedAt", savedAt)
        .toString(2)

    companion object {
        fun fromJson(json: String): TrackMetadata {
            val root = JSONObject(json)
            val artists = root.getJSONArray("artists").let { arr ->
                (0 until arr.length()).map { i -> arr.getString(i) }
            }
            val genres = root.getJSONArray("genres").let { arr ->
                (0 until arr.length()).map { i -> arr.getString(i) }
            }
            return TrackMetadata(
                trackId = root.getString("trackId"),
                extensionId = root.getString("extensionId"),
                title = root.getString("title"),
                artists = artists,
                album = root.optString("album").takeUnless { it.isEmpty() },
                releaseDate = root.optString("releaseDate").takeUnless { it.isEmpty() },
                genres = genres,
                savedAt = root.getLong("savedAt")
            )
        }

        fun fromTrack(track: Track, extensionId: String): TrackMetadata = TrackMetadata(
            trackId = track.id,
            extensionId = extensionId,
            title = track.title,
            artists = track.artists.map { it.name },
            album = track.album?.title,
            releaseDate = track.releaseDate?.toString(),
            genres = track.genres,
            savedAt = System.currentTimeMillis()
        )
    }
}
