package dev.brahmkshatriya.echo.extension.downloaders

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.HttpStreamUtil
import dev.brahmkshatriya.echo.extension.platform.IDownloader
import kotlinx.coroutines.flow.MutableStateFlow

import java.io.File

/**
 * Downloads a progressive HTTP source using OkHttp with byte-range resuming.
 *
 * Registered in [DownloadRegistry] as the default handler for
 * [Streamable.Source.Http] with [Streamable.SourceType.Progressive].
 */
class HttpDownloader : IDownloader {

    private val receiveFlow = MutableStateFlow(0L)

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        file: File
    ): File {
        require(source is Streamable.Source.Http) {
            "HttpDownloader only handles Streamable.Source.Http, got ${source::class.simpleName}"
        }
        val result = runCatching {
            HttpStreamUtil.okHttpDownload(file, source.request, progressFlow, receiveFlow)
        }
        result.getOrElse { file.delete() }
        return file
    }
}
