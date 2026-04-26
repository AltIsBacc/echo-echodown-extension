package dev.brahmkshatriya.echo.extension.platform

import android.os.FileObserver
import dev.brahmkshatriya.echo.extension.EDLDirectories
import dev.brahmkshatriya.echo.extension.utils.EDLUtils
import dev.brahmkshatriya.echo.extension.models.ContextMetadata
import dev.brahmkshatriya.echo.extension.models.ContextMetadata.ContextType
import dev.brahmkshatriya.echo.extension.models.TrackManifest
import dev.brahmkshatriya.echo.extension.models.TrackMetadata
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
 * Uses [FileObserver] to detect changes under albums/, playlists/, and radios/ and keeps an
 * in-memory [StateFlow] map up to date.
 *
 * Directory layout under [echoRoot]:
 *   tracks/    — `{Artist}/{Title}.{ext}`
 *   albums/    — `{Album Title}/metadata.json`
 *   playlists/ — `{Playlist Title}/metadata.json`
 *   radios/    — `{Radio Title}/metadata.json`
 */
class AndroidManifestStore(
    private val echoRoot: File,
    private val directories: EDLDirectories
) : IManifestStore {

    private val albumsDir: File    = File(echoRoot, "albums").apply { mkdirs() }
    private val playlistsDir: File = File(echoRoot, "playlists").apply { mkdirs() }
    private val radiosDir: File    = File(echoRoot, "radios").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _manifests = MutableStateFlow<Map<String, ContextMetadata>>(emptyMap())
    override val manifests: StateFlow<Map<String, ContextMetadata>> = _manifests.asStateFlow()

    @Suppress("DEPRECATION")
    private fun makeObserver(dir: File) = object : FileObserver(
        dir.absolutePath,
        CREATE or DELETE or MOVED_FROM or MOVED_TO or CLOSE_WRITE
    ) {
        override fun onEvent(event: Int, path: String?) {
            path ?: return
            if (!path.endsWith(".json")) return
            scope.launch { reloadAll() }
        }
    }

    private val observers = listOf(
        makeObserver(albumsDir),
        makeObserver(playlistsDir),
        makeObserver(radiosDir)
    )

    override fun start() {
        scope.launch { reloadAll() }
        observers.forEach { it.startWatching() }
    }

    override fun stop() {
        observers.forEach { it.stopWatching() }
    }

    private fun reloadAll() {
        val loaded = listOf(albumsDir, playlistsDir, radiosDir)
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "json" && it.name == "metadata.json" }
                    .mapNotNull { f -> runCatching { ContextMetadata.fromJson(f.readText()) }.getOrNull() }
            }
            .associateBy { it.fileName() }
        _manifests.value = loaded
    }

    /** Route to the correct subfolder based on [manifest.type]. */
    private fun dirFor(type: ContextType): File = when (type) {
        ContextType.ALBUM    -> albumsDir
        ContextType.PLAYLIST -> playlistsDir
        ContextType.RADIO    -> radiosDir
    }

    override fun saveManifest(manifest: ContextMetadata) {
        val dir = File(dirFor(manifest.type), manifest.fileName().removeSuffix(".json")).apply { mkdirs() }
        File(dir, manifest.fileName()).writeText(manifest.toJson())
        _manifests.value = _manifests.value.toMutableMap().apply {
            put(manifest.fileName(), manifest)
        }
    }

    /**
     * Non-suspend: reads from the in-memory map — no async I/O involved.
     * See [IManifestStore.loadManifest] for the rationale.
     */
    override fun loadManifest(extensionId: String, contextId: String): ContextMetadata? {
        val name = ContextMetadata(
            id = contextId, extensionId = extensionId, title = "",
            type = ContextType.PLAYLIST, lastSynced = 0, tracks = emptyList()
        ).fileName()
        return _manifests.value[name]
    }

    override fun trackExists(extensionId: String, trackId: String): Boolean {
        val fileName = EDLUtils.illegalReplace("${extensionId}_${trackId}") + ".json"
        return File(directories.metadata, fileName).exists()
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
            tracks = existing.tracks + TrackManifest(trackKey, sortOrder, now)
        ) ?: ContextMetadata(
            id = contextId, extensionId = extensionId, title = contextTitle,
            type = contextType, lastSynced = now,
            tracks = listOf(TrackManifest(trackKey, sortOrder, now))
        )
        saveManifest(updated)
    }

    override suspend fun pruneOrphanedTracks() {
        val referencedKeys = _manifests.value.values
            .flatMap { m -> m.tracks.map { it.trackId } }
            .toSet()
        directories.tracks.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in referencedKeys) file.delete()
        }
    }

    override fun saveTrackMetadata(metadata: TrackMetadata) {
        val fileName = EDLUtils.illegalReplace("${metadata.extensionId}_${metadata.trackId}") + ".json"
        File(directories.metadata, fileName).also { it.setWritable(true) }.writeText(metadata.toJson())
    }

    override fun loadTrackMetadata(extensionId: String, trackId: String): TrackMetadata? {
        val fileName = EDLUtils.illegalReplace("${extensionId}_${trackId}") + ".json"
        val file = File(directories.metadata, fileName)
        return if (file.exists()) {
            runCatching { TrackMetadata.fromJson(file.readText()) }.getOrNull()
        } else {
            null
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "flac", "ogg", "mp4")
    }
}
