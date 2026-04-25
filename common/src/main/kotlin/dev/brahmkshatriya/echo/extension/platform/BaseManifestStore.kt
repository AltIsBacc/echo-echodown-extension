package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.EDLDirectories
import dev.brahmkshatriya.echo.extension.models.ContextMetadata
import dev.brahmkshatriya.echo.extension.utils.EDLUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File


/**
 * All context metadata & track persistence operations.
 * 
 * Only context metadata is kept in memory; track metadata is read/written on demand.
 * The map is kept in memory and exposed as a [StateFlow], so observers
 * (e.g. a UI layer) react to changes automatically.
 */
abstract class BaseMetadataStore(
    private val directories: EDLDirectories
) {
    /** Live, in-memory view of every loaded manifest keyed by ContextMetadata's title. */
    protected var contextMetadata = MutableStateFlow<Map<String, ContextMetadata>>(emptyMap())

    /** Factory method for creating a platform-specific [IFileObserver] instance. */
    protected abstract fun makeObserver(dir: File, onChange: () -> Unit): IFileObserver

    private val observers = listOf(
        makeObserver(File("albums")) {},
        makeObserver(File("playlists")) {},
        makeObserver(File("radios")) {},
    )

    /** Begin watching the playlists directory for changes and load existing manifests. */
    fun start() {
        observers.forEach { it.start() }
    }
    
    /** Stop watching and release resources. */
    fun stop() {
        observers.forEach { it.stop() }
    }

    /** Write [ContextMetadata] to disk (and update the in-memory map immediately). */
    fun saveContextMetadata(metadata: ContextMetadata) {
        val dir = directories.contextDirFor(metadata)
        val file = File(dir, "metadata.json")
        file.writeText(metadata.toJson())
        contextMetadata.value += metadata.title to metadata
    }

    /**
     * Look up a manifest by [extensionId] + [id].
     */
    fun loadManifest(extensionId: String, id: String): ContextMetadata? {
        return contextMetadata.value.values.find {
            it.extensionId == extensionId &&
            it.id == id
        }
    }

    /**
     * Returns true if a file whose name ends with `_{sanitizedTrackId}.{ext}`
     * already exists in the tracks directory and has non-zero size.
     * Used to skip re-downloading a track that was already fetched.
     */
    fun trackExists(extensionId: String, trackId: String): Boolean {
        return directories.tracks.listFiles()?.any {
            it.isFile &&
            it.nameWithoutExtension.endsWith(BaseMetadataStore.trackKey(extensionId, trackId))
            && it.length() > 0
        } == true
    }

    /**
     * Upsert a single [TrackManifest] entry into the manifest identified by
     * [extensionId] + [contextId], creating the manifest if it does not yet exist.
     * Called after a successful tag step and also during the deduplication pass
     * (to keep playlist manifests up-to-date even when download is skipped).
     */
    fun recordTrackInContext(
        track: TrackMetadata,
        extensionId: String,
        contextId: String,
        sortOrder: Int?
    ) {
        val existing = loadManifest(extensionId, contextId)
        if (existing?.tracks?.any { it.trackId == track.trackId } == true) return

        val now = System.currentTimeMillis()
        val updated = existing?.copy(
            lastSynced = now,
            tracks = existing.tracks + track.copy(sortOrder = sortOrder, addedAt = now)
        ) ?: ContextMetadata(
            id = contextId, extensionId = extensionId, title = "",
            type = ContextType.PLAYLIST, lastSynced = now,
            tracks = listOf(track.copy(sortOrder = sortOrder, addedAt = now))
        )
        saveContextMetadata(updated)
    }

    /**
     * Delete track files in the tracks directory that are not referenced by any manifest.
     * Safe to call on a background thread/coroutine.
     */
    suspend fun pruneOrphanedTracks()

    /**
     * Save detailed track metadata to the metadata directory.
     */
    fun saveTrackMetadata(metadata: TrackMetadata)

    /**
     * Load track metadata by extension ID and track ID.
     */
    fun loadTrackMetadata(extensionId: String, trackId: String): TrackMetadata?

    private fun reload(dir: File) {
        val manifests = dir.listFiles()?.mapNotNull { file ->
            file.takeIf { it.isFile && it.extension == "json" }?.let {
                ContextMetadata.fromJson(it.readText())
            }
        }?.associateBy { it.title } ?: emptyMap()
        contextMetadata.value += manifests
    }

    companion object {
        /**
         * Build a stable, filesystem-safe track key
         * used as the primary identifierbin manifests and as the suffix of audio filenames.
         */
        fun trackKey(extensionId: String, trackId: String): String =
            "${extensionId}_${EDLUtils.illegalReplace(trackId)}"
    }
}