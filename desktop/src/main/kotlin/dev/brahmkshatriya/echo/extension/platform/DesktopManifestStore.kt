package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.DownloadManifest.ContextType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

/**
 * Desktop implementation of [IManifestStore].
 *
 * Uses [WatchService] to detect filesystem changes under [playlistsDir].
 * The watching loop runs on a coroutine in [scope].
 *
 * BUG FIX: the original file was missing its closing `}` — fixed here.
 */
class DesktopManifestStore(private val playlistsDir: File) : IManifestStore {

    override val tracksDir: File = File(playlistsDir.parent, "tracks").apply { mkdirs() }

    private val _manifests = MutableStateFlow<Map<String, DownloadManifest>>(emptyMap())
    override val manifests: StateFlow<Map<String, DownloadManifest>> = _manifests.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchService: WatchService? = null

    override fun start() {
        playlistsDir.mkdirs()
        reloadAll()

        val ws = FileSystems.getDefault().newWatchService().also { watchService = it }
        playlistsDir.toPath().register(
            ws,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        )

        scope.launch {
            while (isActive) {
                val key = ws.poll(1, TimeUnit.SECONDS) ?: continue
                val changed = key.pollEvents().any { event ->
                    event.context()?.toString()?.endsWith(".json") == true
                }
                if (changed) reloadAll()
                if (!key.reset()) break
            }
        }
    }

    override fun stop() {
        watchService?.close()
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
