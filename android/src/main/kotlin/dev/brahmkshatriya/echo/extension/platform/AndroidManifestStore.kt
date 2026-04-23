package dev.brahmkshatriya.echo.extension.platform

import android.os.FileObserver
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.DownloadManifest.ContextType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android implementation of [IManifestStore].
 *
 * Uses [FileObserver] to detect changes under [playlistsDir] and keeps an
 * in-memory [StateFlow] map up to date.
 *
 * Directory layout under [echoRoot]:
 *   tracks/    — `{Artist} - {Title}_{sanitizedTrackId}.{ext}`
 *   playlists/ — `{extensionId}_{sanitizedContextId}.json`
 *
 * Call [start] once in [AndroidEDLExtension.onInitialize] and [stop] on teardown.
 */
class AndroidManifestStore(private val echoRoot: File) : IManifestStore {

    override val tracksDir: File = File(echoRoot, "tracks").apply { mkdirs() }
    private val playlistsDir: File = File(echoRoot, "playlists").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _manifests = MutableStateFlow<Map<String, DownloadManifest>>(emptyMap())
    override val manifests: StateFlow<Map<String, DownloadManifest>> = _manifests.asStateFlow()

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

    override fun start() {
        scope.launch { reloadAll() }
        observer.startWatching()
    }

    override fun stop() {
        observer.stopWatching()
    }

    private fun reloadAll() {
        val loaded = playlistsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { DownloadManifest.fromJson(f.readText()) }.getOrNull() }
            ?.associateBy { it.fileName() }
            ?: emptyMap()
        _manifests.value = loaded
    }

    override fun saveManifest(manifest: DownloadManifest) {
        File(playlistsDir, manifest.fileName()).writeText(manifest.toJson())
        _manifests.value = _manifests.value.toMutableMap().apply {
            put(manifest.fileName(), manifest)
        }
    }

    /**
     * Non-suspend: reads from the in-memory map — no async I/O involved.
     * See [IManifestStore.loadManifest] for the rationale.
     */
    override fun loadManifest(extensionId: String, contextId: String): DownloadManifest? {
        val name = DownloadManifest(
            id = contextId, extensionId = extensionId, title = "",
            type = ContextType.PLAYLIST, lastSynced = 0, tracks = emptyList()
        ).fileName()
        return _manifests.value[name]
    }

    override fun trackExists(extensionId: String, trackId: String): Boolean {
        val idSuffix = "_${DownloadManifest.sanitize(trackId)}"
        return tracksDir.listFiles { f ->
            f.nameWithoutExtension.endsWith(idSuffix)
                && f.extension in AUDIO_EXTENSIONS
                && f.length() > 0
        }?.isNotEmpty() == true
    }

    override fun recordTrackInManifest(
        extensionId: String,
        contextId: String,
        contextTitle: String,
        contextType: ContextType,
        trackKey: String,
        sortOrder: Int?
    ) {
        val existing = loadManifest(extensionId, contextId)
        if (existing?.tracks?.any { it.trackId == trackKey } == true) return

        val now = System.currentTimeMillis()
        val updated = existing?.copy(
            lastSynced = now,
            tracks = existing.tracks + DownloadManifest.ManifestTrack(trackKey, sortOrder, now)
        ) ?: DownloadManifest(
            id = contextId, extensionId = extensionId, title = contextTitle,
            type = contextType, lastSynced = now,
            tracks = listOf(DownloadManifest.ManifestTrack(trackKey, sortOrder, now))
        )
        saveManifest(updated)
    }

    override suspend fun pruneOrphanedTracks() {
        val referencedKeys = _manifests.value.values
            .flatMap { m -> m.tracks.map { it.trackId } }
            .toSet()
        tracksDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in referencedKeys) file.delete()
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "flac", "ogg", "mp4")
    }
}
