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
import dev.brahmkshatriya.echo.extension.pipeline.DownloadPipeline
import dev.brahmkshatriya.echo.extension.pipeline.DownloadRegistry
import dev.brahmkshatriya.echo.extension.pipeline.ManifestManager
import dev.brahmkshatriya.echo.extension.pipeline.TaskRegistry
import dev.brahmkshatriya.echo.extension.platform.ICodecEngine
import dev.brahmkshatriya.echo.extension.platform.IManifestStore
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Abstract base class for EDL download extensions.
 *
 * ── What this class owns ──────────────────────────────────────────────────
 *  • [DownloadRegistry]  — registered downloaders + server/source selection
 *  • [TaskRegistry]      — ordered post-download task pipeline
 *  • [DownloadPipeline]  — orchestrates download + task execution
 *  • [ManifestManager]   — deduplication logic
 *  • Extension-list management (music + lyrics)
 *  • [DownloadClient] API surface (getDownloadTracks, selectServer, selectSources, download)
 *
 * ── What subclasses provide ───────────────────────────────────────────────
 *  Subclasses pass their platform implementations via [initPlatform] and register
 *  tasks/downloaders in their `init` block or [onInitialize]:
 *
 * ```kotlin
 * class AndroidEDLExtension : EDLExtension() {
 *     override fun onInitialize() {
 *         initPlatform(AndroidCodecEngine, AndroidManifestStore(…), AndroidSettingsProvider(settings))
 *         downloadRegistry.register("http",   HttpDownloader())
 *         downloadRegistry.register("stream", StreamDownloader())
 *         downloadRegistry.register("ffmpeg", FFmpegDownloader(AndroidCodecEngine))
 *         taskRegistry.register(MergeTask(AndroidCodecEngine, settingsProvider) { isVideo })
 *         taskRegistry.register(TagTask(AndroidCodecEngine, settingsProvider, manifestStore, …))
 *         taskRegistry.register(LyricsTask(settingsProvider, { musicExtensionList }, { lyricsExtensionList }))
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

    protected lateinit var codecEngine: ICodecEngine
    protected lateinit var manifestStore: IManifestStore
    protected lateinit var settingsProvider: ISettingsProvider

    protected val taskRegistry = TaskRegistry()
    protected val downloadRegistry: DownloadRegistry by lazy {
        DownloadRegistry(settingsProvider)
    }

    private val pipeline: DownloadPipeline by lazy {
        DownloadPipeline(downloadRegistry, taskRegistry)
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
        store.start()

        // default, not platform dependent
        downloadRegistry.register("http",   HttpDownloader())
        downloadRegistry.register("stream", StreamDownloader())
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
            "tmp_${dev.brahmkshatriya.echo.extension.models.DownloadManifest.trackKey(context.extensionId, context.track.id)}"
        )
        return pipeline.execute(progressFlow, context, source, tempFile)
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
        TODO("Not yet implemented")
    }

    override suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        TODO("Not yet implemented")
    }

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
