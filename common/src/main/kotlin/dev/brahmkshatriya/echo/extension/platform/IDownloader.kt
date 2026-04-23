package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Abstraction for a single download strategy.
 *
 * Concrete implementations live in common/downloaders/:
 *   - [HttpDownloader]    — progressive HTTP with OkHttp
 *   - [StreamDownloader]  — [Streamable.Source.Raw] / InputStream
 *   - [FfmpegDownloader]  — HLS / DASH via FFmpeg
 *
 * Registered by name in [DownloadRegistry].
 */
interface IDownloader {
    /**
     * Download the content described by [source] into [file].
     * Progress is reported on [progressFlow].
     * Returns the file that was written (may differ from [file] if the downloader
     * chose a different path, though in practice they do not).
     */
    suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        file: File
    ): File
}
