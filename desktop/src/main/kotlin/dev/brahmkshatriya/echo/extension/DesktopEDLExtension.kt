package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.downloaders.DownloadSource
import dev.brahmkshatriya.echo.extension.downloaders.Downloader
import dev.brahmkshatriya.echo.extension.platform.DesktopManifestStore
import dev.brahmkshatriya.echo.extension.platform.DesktopPlatformPaths
import dev.brahmkshatriya.echo.extension.platform.DesktopSettingsProvider
import dev.brahmkshatriya.echo.extension.tasks.Merge
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class DesktopEDLExtension : EDLExtension() {
    private val settingsProvider = DesktopSettingsProvider()
    private val platformPaths = DesktopPlatformPaths(settingsProvider)
    private val manifestStore = DesktopManifestStore(platformPaths.getPlaylistsDir())

    init {
        manifestStore.start()
    }

    override suspend fun onInitialize() {
        // TODO
    }

    override suspend fun getDownloadTracks(
        extensionId: String,
        item: EchoMediaItem,
        context: EchoMediaItem?
    ): List<DownloadContext> {
        val all = super.getDownloadTracks(extensionId, item, context)
        if (item is EchoMediaItem.Lists) {
            return all.filter { ctx ->
                val alreadyHave = manifestStore.trackExists(extensionId, ctx.track.id)
                if (alreadyHave) {
                    val contextItem = ctx.context
                    if (contextItem != null) {
                        manifestStore.recordTrackInManifest(
                            extensionId = extensionId,
                            contextId = contextItem.id,
                            contextTitle = contextItem.title,
                            contextType = contextItem.toManifestType(),
                            trackKey = DownloadManifest.trackKey(extensionId, ctx.track.id),
                            sortOrder = ctx.sortOrder
                        )
                    }
                }
                !alreadyHave
            }
        }
        return all
    }

    override suspend fun selectServer(context: DownloadContext): Streamable {
        return context.track.servers.select(settingsProvider.getQualityPreference())
    }

    override suspend fun selectSources(
        context: DownloadContext, server: Streamable.Media.Server
    ): List<Streamable.Source> {
        val sources = mutableListOf<Streamable.Source>()
        sources.add(server.sources.select(settingsProvider.getQualityPreference()))
        return sources
    }

    private fun getDownloadFile(extensionId: String, trackId: String): File {
        return File(platformPaths.getTempDir(), "tmp_${DownloadManifest.trackKey(extensionId, trackId)}")
    }

    private val downloader by lazy { Downloader() }

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source
    ): File {
        return when (source) {
            is Streamable.Source.Raw -> {
                val streamProvider = source.streamProvider?.provide(0, -1)
                    ?: throw Exception("Stream provider is null")
                downloader.download(
                    getDownloadFile(context.extensionId, context.track.id),
                    progressFlow,
                    DownloadSource.Stream(streamProvider.first, streamProvider.second)
                )
            }

            is Streamable.Source.Http -> {
                if (source.isLive) throw ClientException.NotSupported("Streams aren't supported")
                downloader.download(
                    getDownloadFile(context.extensionId, context.track.id),
                    progressFlow,
                    DownloadSource.Http(source)
                )
            }
        }
    }

    override suspend fun merge(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        files: List<File>
    ): File = Merge.merge(progressFlow, context, files, settingsProvider.shouldPrefixTrackNumbers(), false, null) // No codec

    override suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        progressFlow.emit(Progress(4, 1))
        progressFlow.emit(Progress(4, 2))
        progressFlow.emit(Progress(4, 3))
        progressFlow.emit(Progress(4, 4))
        return file // Skip tagging for desktop
    }

    override val concurrentDownloads: Int
        get() = settingsProvider.getConcurrentDownloads()
}