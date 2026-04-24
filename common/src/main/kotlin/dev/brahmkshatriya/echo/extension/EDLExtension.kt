package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.LyricsExtension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.DownloadClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.providers.LyricsExtensionsProvider
import dev.brahmkshatriya.echo.common.providers.MusicExtensionsProvider
import dev.brahmkshatriya.echo.extension.downloaders.HttpDownloader
import dev.brahmkshatriya.echo.extension.downloaders.StreamDownloader
import dev.brahmkshatriya.echo.extension.downloaders.FFmpegDownloader
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.pipeline.DownloadRegistry
import dev.brahmkshatriya.echo.extension.pipeline.ManifestManager
import dev.brahmkshatriya.echo.extension.pipeline.TaskRegistry
import dev.brahmkshatriya.echo.extension.platform.ICodecEngine
import dev.brahmkshatriya.echo.extension.platform.IManifestStore
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import dev.brahmkshatriya.echo.extension.processors.MergeProcessor
import dev.brahmkshatriya.echo.extension.processors.TagProcessor
import dev.brahmkshatriya.echo.extension.tasks.LyricsTask
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Abstract base class for EDL download extensions.
 *
 * ```kotlin
 * class AndroidEDLExtension : EDLExtension() {
 *     override fun onInitialize() {
 *         initPlatform(AndroidCodecEngine, AndroidManifestStore(…), AndroidSettingsProvider(settings))
 *     }
 * }
 * ```
 */
abstract class EDLExtension : DownloadClient, MusicExtensionsProvider, LyricsExtensionsProvider {

    override val requiredMusicExtensions = listOf<String>()
    var musicExtensionList: List<MusicExtension> = emptyList()
    override fun setMusicExtensions(extensions: List<MusicExtension>) {
        musicExtensionList = extensions
    }

    override val requiredLyricsExtensions = listOf<String>()
    var lyricsExtensionList: List<LyricsExtension> = emptyList()
    override fun setLyricsExtensions(extensions: List<LyricsExtension>) {
        lyricsExtensionList = extensions
    }

    override val concurrentDownloads: Int
        get() = settingsProvider.getConcurrentDownloads()

    protected lateinit var codecEngine: ICodecEngine
    protected lateinit var manifestStore: IManifestStore
    protected lateinit var settingsProvider: ISettingsProvider

    protected lateinit var mergeProcessor: MergeProcessor
    protected lateinit var tagProcessor: TagProcessor

    protected val taskRegistry = TaskRegistry()
    protected val downloadRegistry: DownloadRegistry by lazy {
        DownloadRegistry(settingsProvider)
    }

    private val manifestManager: ManifestManager by lazy {
        ManifestManager(manifestStore)
    }

    @Volatile
    private var isVideoFlag = false

    protected fun isVideo(): Boolean = isVideoFlag

    /**
     * Initialise the three platform dependencies.
     * Must be called before any download operation — typically from [onInitialize]
     * or the subclass `init` block.
     */
    protected fun initPlatform(
        codec: ICodecEngine,
        store: IManifestStore,
        settings: ISettingsProvider
    ) {
        codecEngine = codec
        manifestStore = store
        settingsProvider = settings
        mergeProcessor = MergeProcessor(codecEngine, settingsProvider, ::isVideo)
        tagProcessor = TagProcessor(
            codecEngine, settingsProvider, manifestStore,
            { musicExtensionList },
            { getOutputDir() },
            ::isVideo
        )
        
        store.start()

        downloadRegistry.register("http",   HttpDownloader())
        downloadRegistry.register("stream", StreamDownloader())
        downloadRegistry.register("ffmpeg", FFmpegDownloader(codecEngine))

        taskRegistry.register(
            LyricsTask(
                settings         = settingsProvider,
                musicExtensions  = { musicExtensionList },
                lyricsExtensions = { lyricsExtensionList }
            )
        )
    }

    override suspend fun getDownloadTracks(
        extensionId: String,
        item: EchoMediaItem,
        context: EchoMediaItem?
    ): List<DownloadContext> {
        val all = resolveTracksFromItem(extensionId, item)
        return manifestManager.filterNewTracks(all, item)
    }

    override suspend fun selectServer(context: DownloadContext): Streamable =
        downloadRegistry.selectServer(context)

    override suspend fun selectSources(
        context: DownloadContext,
        server: Streamable.Media.Server
    ): List<Streamable.Source> =
        downloadRegistry.selectSources(context, server)

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source
    ): File {
        isVideoFlag = source.isVideo
        if (source is Streamable.Source.Http && source.isLive) {
            throw ClientException.NotSupported("Live streams aren't supported")
        }

        val tempFile = File(
            manifestStore.tracksDir,
            "tmp_${DownloadManifest.trackKey(context.extensionId, context.track.id)}"
        )

        val rawFile = downloadRegistry.download(progressFlow, context, source, tempFile)
        return taskRegistry.executeAll(progressFlow, context, rawFile)
    }

    private suspend fun resolveTracksFromItem(
        extensionId: String,
        item: EchoMediaItem
    ): List<DownloadContext> = when (item) {
        is Track -> listOf(DownloadContext(extensionId, item))
        is EchoMediaItem.Lists -> {
            val ext = musicExtensionList.getExtension(extensionId)!!
            val tracks = when (item) {
                is Album -> ext.get<AlbumClient, List<Track>> {
                    val album = loadAlbum(item)
                    loadTracks(album)?.loadAll() ?: emptyList()
                }
                is Playlist -> ext.get<PlaylistClient, List<Track>> {
                    val playlist = loadPlaylist(item)
                    loadTracks(playlist).loadAll()
                }
                is Radio -> ext.get<RadioClient, List<Track>> {
                    loadTracks(item).loadAll()
                }
            }.getOrThrow()
            tracks.mapIndexed { index, track ->
                DownloadContext(extensionId, track, index, item)
            }
        }
        else -> emptyList()
    }

    override suspend fun merge(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        files: List<File>
    ): File {
        return mergeProcessor.execute(progressFlow, context, files.first())
    }

    override suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        return tagProcessor.execute(progressFlow, context, file)
    }

    abstract fun getOutputDir(): File

    companion object {
        fun List<Extension<*>>.getExtension(id: String?) = firstOrNull { it.id == id }

        suspend inline fun <reified T, R> Extension<*>.get(block: T.() -> R) = runCatching {
            val instance = instance.value().getOrThrow()
            if (instance !is T) throw ClientException.NotSupported(
                "$name Extension: ${T::class.simpleName}"
            )
            block.invoke(instance)
        }
    }
}
