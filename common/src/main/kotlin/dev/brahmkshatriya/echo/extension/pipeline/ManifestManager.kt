package dev.brahmkshatriya.echo.extension.pipeline

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.extension.EDLDirectories
import dev.brahmkshatriya.echo.extension.utils.EDLUtils
import dev.brahmkshatriya.echo.extension.models.ContextMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pure business logic for deduplication and manifest updates.
 *
 * Used by [EDLExtension.getDownloadTracks] to decide which tracks need fetching.
 */
class ManifestManager(private val directories: EDLDirectories) {

    /**
     * Filter [contexts] down to only the tracks that still need downloading.
     *
     * For every track that already exists on disk, the playlist manifest is
     * updated to include it (deduplication rule: skip download but keep the
     * manifest reference current).
     *
     * @param item The top-level item being downloaded (album, playlist, radio…).
     *             Deduplication only runs for list types; single tracks always proceed.
     */
    fun filterNewTracks(
        contexts: List<DownloadContext>,
        item: EchoMediaItem
    ): List<DownloadContext> {
        if (item !is EchoMediaItem.Lists) return contexts

        // Load the ContextMetadata file once before the filter loop
        val contextItem = item as? EchoMediaItem.Lists
        val manifestFile = directories.contextDirFor(item)?.let { File(it, "metadata.json") }
        val existingMetadata = if (manifestFile?.exists() == true) {
            ContextMetadata.fromJson(manifestFile.readText())
        } else null
        val existingTrackIds = existingMetadata?.tracks.map { it.trackId }?.toSet() ?: emptySet()

        // Collect all already-existing tracks that need adding to the manifest into a list
        val tracksToAdd = mutableListOf<ContextMetadata.TrackManifest>()

        val filteredContexts = contexts.filter { ctx ->
            val trackId = EDLUtils.trackKey(ctx.extensionId, ctx.track.id)
            val alreadyHave = EDLUtils.trackExists(directories, ctx.extensionId, ctx.track.id)
            if (alreadyHave && !existingTrackIds.contains(trackId)) {
                // Track exists on disk but not in manifest, so we need to add it to the manifest
                tracksToAdd.add(ContextMetadata.TrackManifest(
                    trackId = trackId,
                    sortOrder = ctx.sortOrder,
                    addedAt = System.currentTimeMillis()
                ))
            }
            !alreadyHave
        }

        // After the loop, if any need adding, do a single upsert and write once
        if (tracksToAdd.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val updatedTracks = (existingMetadata?.tracks ?: emptyList()) + tracksToAdd
            val updatedMetadata = existingMetadata?.copy(
                lastSynced = now,
                tracks = updatedTracks
            ) ?: ContextMetadata(
                id = item.id,
                extensionId = item.extensionId,
                title = item.title,
                type = item.toManifestType(),
                lastSynced = now,
                tracks = tracksToAdd
            )
            manifestFile?.writeText(updatedMetadata.toJson())
        }

        return filteredContexts
    }
}
