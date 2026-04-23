package dev.brahmkshatriya.echo.extension

import android.os.FileObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single source of truth for all download manifests.
 *
 * Directory layout under [echoRoot]:
 *   tracks/      - flat store of audio files: {extensionId}_{trackId}.{ext}
 *   playlists/   - manifest JSONs for playlists/albums/radios
 *
 * Call [start] once (e.g. in AndroidED.onInitialize) and [stop] on teardown.
 */
class ManifestManager(private val echoRoot: File) {
    val tracksDir = File(echoRoot, "tracks").apply { mkdirs() }
    val playlistsDir = File(echoRoot, "playlists").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _manifests = MutableStateFlow<Map<String, DownloadManifest>>(emptyMap())
    val manifests: StateFlow<Map<String, DownloadManifest>> = _manifests.asStateFlow()

    @Suppress("DEPRECATION")
    private val observer = object : FileObserver(
        playlistsDir.absolutePath,
        CREATE or DELETE or MOVED_FROM or MOVED_TO or CLOSE_WRITE
    ) {
        override fun onEvent(event: Int, path: String?) {
            path ?: return
            if (!path.endsWith(".json")) return
            scope.launch { reloadAll() }
        }
    }

    fun start() {
        scope.launch { reloadAll() }
        observer.startWatching()
    }

    fun stop() {
        observer.stopWatching()
    }

    fun reloadAll() {
        val loaded = playlistsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { DownloadManifest.fromJson(f.readText()) }.getOrNull()
            }
            ?.associateBy { it.fileName() }
            ?: emptyMap()
        _manifests.value = loaded
    }

    fun getManifest(extensionId: String, contextId: String): DownloadManifest? {
        val name = DownloadManifest(
            id = contextId, extensionId = extensionId, title = "",
            type = DownloadManifest.ContextType.PLAYLIST, lastSynced = 0, tracks = emptyList()
        ).fileName()
        return _manifests.value[name]
    }

    // ----- Track existence -----

    /**
     * Returns the on-disk audio file for a track if it already exists,
     * so we can skip re-downloading it.
     */
    fun findTrackFile(extensionId: String, trackId: String): File? {
        // Filename format is "{Artist} - {Title}_{sanitizedTrackId}.{ext}"
        // Match on the id suffix rather than reconstructing the human-readable part.
        val idSuffix = "_${DownloadManifest.sanitize(trackId)}"
        return tracksDir.listFiles { f ->
            f.nameWithoutExtension.endsWith(idSuffix)
                && f.extension in AUDIO_EXTENSIONS
                && f.length() > 0
        }?.firstOrNull()
    }

    fun trackExists(extensionId: String, trackId: String): Boolean =
        findTrackFile(extensionId, trackId) != null

    /**
     * Persist a new or updated manifest to disk and refresh the in-memory map.
     */
    fun saveManifest(manifest: DownloadManifest) {
        val file = File(playlistsDir, manifest.fileName())
        file.writeText(manifest.toJson())
        // Reload triggered by FileObserver, but also do it inline for immediate consistency
        _manifests.value = _manifests.value.toMutableMap().apply {
            put(manifest.fileName(), manifest)
        }
    }

    /**
     * Add a single track entry to an existing manifest (or create the manifest if absent).
     * Called after a successful tag step.
     */
    fun recordTrackInManifest(
        extensionId: String,
        contextId: String,
        contextTitle: String,
        contextType: DownloadManifest.ContextType,
        trackKey: String,
        sortOrder: Int?
    ) {
        val existing = getManifest(extensionId, contextId)
        val now = System.currentTimeMillis()
        val alreadyPresent = existing?.tracks?.any { it.trackId == trackKey } == true
        if (alreadyPresent) return

        val updated = existing?.copy(
            lastSynced = now,
            tracks = existing.tracks + DownloadManifest.ManifestTrack(
                trackId = trackKey,
                sortOrder = sortOrder,
                addedAt = now
            )
        ) ?: DownloadManifest(
            id = contextId,
            extensionId = extensionId,
            title = contextTitle,
            type = contextType,
            lastSynced = now,
            tracks = listOf(
                DownloadManifest.ManifestTrack(
                    trackId = trackKey,
                    sortOrder = sortOrder,
                    addedAt = now
                )
            )
        )
        saveManifest(updated)
    }

    /**
     * Remove a manifest entirely (e.g. user deleted the playlist).
     * Note: this does NOT delete the track files — they may be shared.
     */
    fun deleteManifest(extensionId: String, contextId: String) {
        val name = DownloadManifest(
            id = contextId, extensionId = extensionId, title = "",
            type = DownloadManifest.ContextType.PLAYLIST, lastSynced = 0, tracks = emptyList()
        ).fileName()
        File(playlistsDir, name).delete()
        _manifests.value = _manifests.value.toMutableMap().apply { remove(name) }
    }

    /**
     * Garbage-collect track files that are not referenced by any manifest.
     * Safe to call in a background job.
     */
    fun pruneOrphanedTracks() {
        val referencedKeys = _manifests.value.values
            .flatMap { m -> m.tracks.map { it.trackId } }
            .toSet()

        tracksDir.listFiles()?.forEach { file ->
            val keyWithoutExt = file.nameWithoutExtension
            if (keyWithoutExt !in referencedKeys) {
                file.delete()
            }
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = listOf("m4a", "mp3", "flac", "ogg", "mp4")
    }
}
