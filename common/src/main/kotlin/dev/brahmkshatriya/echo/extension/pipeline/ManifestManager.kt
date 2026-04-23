package dev.brahmkshatriya.echo.extension.pipeline

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.extension.EDLUtils.toManifestType
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.platform.IManifestStore

/**
 * Pure business logic for deduplication and manifest updates.
 *
 * Used by [EDLExtension.getDownloadTracks] to decide which tracks need fetching.
 */
class ManifestManager(private val store: IManifestStore) {

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

        return contexts.filter { ctx ->
            val alreadyHave = store.trackExists(ctx.extensionId, ctx.track.id)
            if (alreadyHave) {
                val contextItem = ctx.context
                if (contextItem != null) {
                    store.recordTrackInManifest(
                        extensionId = ctx.extensionId,
                        contextId = contextItem.id,
                        contextTitle = contextItem.title,
                        contextType = contextItem.toManifestType(),
                        trackKey = DownloadManifest.trackKey(ctx.extensionId, ctx.track.id),
                        sortOrder = ctx.sortOrder
                    )
                }
            }
            !alreadyHave
        }
    }
}
