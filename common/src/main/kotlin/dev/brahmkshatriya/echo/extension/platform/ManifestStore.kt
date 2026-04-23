package dev.brahmkshatriya.echo.extension.platform

import java.io.File
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.DownloadManifest.ContextType
import kotlinx.coroutines.flow.StateFlow

interface ManifestStore {
    val manifests: StateFlow<Map<String, DownloadManifest>>

    val tracksDir: File

    fun start()
    fun stop()

    fun saveManifest(manifest: DownloadManifest)

    suspend fun loadManifest(extensionId: String, contextId: String): DownloadManifest?

    fun trackExists(extensionId: String, trackId: String): Boolean

    fun recordTrackInManifest(
        extensionId: String,
        contextId: String,
        contextTitle: String,
        contextType: ContextType,
        trackKey: String,
        sortOrder: Int?
    )

    suspend fun pruneOrphanedTracks()
}