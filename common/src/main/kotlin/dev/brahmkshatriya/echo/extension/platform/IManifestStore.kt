package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.DownloadManifest.ContextType
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * All manifest and track-persistence operations.
 *
 * The manifest map is kept in memory and exposed as a [StateFlow], so observers
 * (e.g. a UI layer) react to changes automatically.
 */
interface IManifestStore {

    /** Live, in-memory view of every loaded manifest keyed by [DownloadManifest.fileName]. */
    val manifests: StateFlow<Map<String, DownloadManifest>>

    /**
     * Root directory that holds audio files.
     * Named "{Artist} - {Title}_{sanitizedTrackId}.{ext}" so they are both
     * human-readable and deduplication-safe.
     */
    val tracksDir: File

    /** Begin watching the playlists directory for changes and load existing manifests. */
    fun start()

    /** Stop watching and release resources. */
    fun stop()

    /** Write [manifest] to disk (and update the in-memory map immediately). */
    fun saveManifest(manifest: DownloadManifest)

    /**
     * Look up a manifest by [extensionId] + [contextId].
     *
     * NOT suspend: the manifest map is always in memory (populated by the
     * file-system watcher), so there is no async I/O at the point of lookup.
     * This keeps call-sites clean and avoids spurious coroutine overhead.
     */
    fun loadManifest(extensionId: String, contextId: String): DownloadManifest?

    /**
     * Returns true if a file whose name ends with `_{sanitizedTrackId}.{ext}`
     * already exists in [tracksDir] and has non-zero size.
     * Used to skip re-downloading a track that was already fetched.
     */
    fun trackExists(extensionId: String, trackId: String): Boolean

    /**
     * Upsert a single [TrackManifest] entry into the manifest identified by
     * [extensionId] + [contextId], creating the manifest if it does not yet exist.
     * Called after a successful tag step and also during the deduplication pass
     * (to keep playlist manifests up-to-date even when download is skipped).
     */
    fun recordTrackInManifest(
        extensionId: String,
        contextId: String,
        contextTitle: String,
        contextType: ContextType,
        trackKey: String,
        sortOrder: Int?
    )

    /**
     * Delete track files in [tracksDir] that are not referenced by any manifest.
     * Safe to call on a background thread/coroutine.
     */
    suspend fun pruneOrphanedTracks()
}
