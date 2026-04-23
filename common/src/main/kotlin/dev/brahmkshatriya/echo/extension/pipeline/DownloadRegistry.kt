package dev.brahmkshatriya.echo.extension.pipeline

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.Utils.select
import dev.brahmkshatriya.echo.extension.platform.IDownloader
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Registry for all [IDownloader] implementations.
 *
 * Platform subclasses register their downloaders once during initialisation:
 *
 * ```kotlin
 * downloadRegistry.register("http",    HttpDownloader())
 * downloadRegistry.register("stream",  StreamDownloader())
 * downloadRegistry.register("ffmpeg",  FfmpegDownloader(codecEngine))
 * ```
 *
 * Server/source selection and downloader dispatch live here, driven by
 * [ISettingsProvider.getQualityPreference], so [EDLExtension] subclasses
 * never contain quality logic.
 */
class DownloadRegistry(private val settings: ISettingsProvider) {

    private val downloaders = mutableMapOf<String, IDownloader>()

    /**
     * Register a named [IDownloader].
     * Call order does not matter — lookup is by [name].
     */
    fun register(name: String, downloader: IDownloader) {
        downloaders[name] = downloader
    }

    // ── Server / source selection ────────────────────────────────────────────

    /**
     * Pick the best [Streamable] server from [context] according to the current
     * quality preference.
     */
    fun selectServer(context: DownloadContext): Streamable =
        context.track.servers.select(settings.getQualityPreference())

    /**
     * Pick the best [Streamable.Source] from [server] according to the current
     * quality preference.  Returns a single-element list (the pipeline handles
     * multi-source merging via [MergeTask]).
     */
    fun selectSources(
        context: DownloadContext,
        server: Streamable.Media.Server
    ): List<Streamable.Source> =
        listOf(server.sources.select(settings.getQualityPreference()))

    // ── Download dispatch ────────────────────────────────────────────────────

    /**
     * Route [source] to the appropriate [IDownloader] and execute the download.
     * Returns the resulting [File].
     *
     * Routing rules (in priority order):
     *  1. [Streamable.Source.Raw]  → the downloader registered as "stream"
     *  2. [Streamable.Source.Http] with non-progressive type → "ffmpeg" (if registered)
     *  3. [Streamable.Source.Http] progressive → "http"
     */
    suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        file: File
    ): File {
        val downloader = when {
            source is Streamable.Source.Raw -> downloaders["stream"]
                ?: throw ClientException.NotSupported("No StreamDownloader registered")

            source is Streamable.Source.Http && source.type != Streamable.SourceType.Progressive ->
                downloaders["ffmpeg"]
                    ?: throw ClientException.NotSupported(
                        "Non-progressive HTTP downloads require a registered FfmpegDownloader"
                    )

            else -> downloaders["http"]
                ?: throw ClientException.NotSupported("No HttpDownloader registered")
        }
        return downloader.download(progressFlow, context, source, file)
    }
}
