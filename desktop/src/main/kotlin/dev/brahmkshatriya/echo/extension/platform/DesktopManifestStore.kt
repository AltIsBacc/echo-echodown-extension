package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.DownloadManifest
import dev.brahmkshatriya.echo.extension.DownloadManifest.ContextType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

class DesktopManifestStore(private val playlistsDir: File) : ManifestStore {
    override val tracksDir = File(playlistsDir.parent, "tracks").apply { mkdirs() }
    private val _manifests = MutableStateFlow<Map<String, DownloadManifest>>(emptyMap())
    override val manifests: StateFlow<Map<String, DownloadManifest>> = _manifests.asStateFlow()

    private var watchService: WatchService? = null

    override fun start() {
        playlistsDir.mkdirs()
        watchService = FileSystems.getDefault().newWatchService()
        val path = playlistsDir.toPath()
        path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY)
        // TODO: start watching thread
        reloadAll()
    }

    override fun stop() {
        watchService?.close()
    }

    private fun reloadAll() {
        val loaded = playlistsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { DownloadManifest.fromJson(f.readText()) }.getOrNull()
            }
            ?.associateBy { it.fileName() }
            ?: emptyMap()
        _manifests.value = loaded
    }

    override fun saveManifest(manifest: DownloadManifest) {
        val file = File(playlistsDir, manifest.fileName())
        file.writeText(manifest.toJson())
        // Reload triggered by FileObserver, but also do it inline for immediate consistency
        _manifests.value = _manifests.value.toMutableMap().apply {
            put(manifest.fileName(), manifest)
        }
    }

    override suspend fun loadManifest(extensionId: String, contextId: String): DownloadManifest? {
        val name = DownloadManifest(
            id = contextId, extensionId = extensionId, title = "",
            type = DownloadManifest.ContextType.PLAYLIST, lastSynced = 0, tracks = emptyList()
        ).fileName()
        return _manifests.value[name]
    }

    override fun trackExists(extensionId: String, trackId: String): Boolean {
        // Filename format is "{Artist} - {Title}_{sanitizedTrackId}.{ext}"
        // Match on the id suffix rather than reconstructing the human-readable part.
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

    override suspend fun pruneOrphanedTracks() {
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