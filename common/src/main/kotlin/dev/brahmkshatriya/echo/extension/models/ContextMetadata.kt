package dev.brahmkshatriya.echo.extension.models

import dev.brahmkshatriya.echo.common.models.DownloadContext
import org.json.JSONArray
import org.json.JSONObject
import dev.brahmkshatriya.echo.extension.utils.EDLUtils

/**
 * Persisted playlist/album/radio metadata.
 *
 * Stored in {playlists,albums,radios}/{sanitizedTitle}/metadata.json
 */
data class ContextMetadata(
    val contextId: String,    // e.g. "spotify:playlist:abc"
    val extensionId: String,
    val title: String,
    val type: ContextType,
    val lastSynced: Long,     // epoch ms
    val tracks: List<TrackManifest>
) {
    enum class ContextType { PLAYLIST, ALBUM, RADIO }

    data class TrackManifest(
        val trackId: String,  // stable key: "{extensionId}_{sanitizedTrackId}"
        val sortOrder: Int?,
        val addedAt: Long     // epoch ms
    )

    fun toJson(): String = JSONObject()
        .put("contextId", contextId)
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
        fun fromJson(json: String): ContextMetadata {
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
            return ContextMetadata(
                contextId = root.getString("contextId"),
                extensionId = root.getString("extensionId"),
                title = root.getString("title"),
                type = ContextType.valueOf(root.getString("type")),
                lastSynced = root.getLong("lastSynced"),
                tracks = tracks
            )
        }

        fun fromDownloadContext(context: DownloadContext): ContextMetadata = ContextMetadata(
            contextId = context.context!!.id,
            extensionId = context.extensionId,
            title = context.context!!.title,
            type = context.contextType.name
            lastSynced = System.currentTimeMillis(),
            tracks = listOf(TrackManifest(
                trackId = BaseManifestStore.trackKey(context.extensionId, context.track.id),
                sortOrder = context.sortOrder,
                addedAt = System.currentTimeMillis()
            ))
        )
    }
}
