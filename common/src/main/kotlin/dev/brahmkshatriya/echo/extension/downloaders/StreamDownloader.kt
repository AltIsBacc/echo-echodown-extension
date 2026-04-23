package dev.brahmkshatriya.echo.extension.downloaders

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.HttpStreamUtil
import dev.brahmkshatriya.echo.extension.platform.IDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Downloads a [Streamable.Source.Raw] (InputStream) source.
 *
 * Registered in [DownloadRegistry] as the handler for raw stream sources.
 */
class StreamDownloader : IDownloader {

    private val receiveFlow = MutableStateFlow(0L)

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        file: File
    ): File {
        require(source is Streamable.Source.Raw) {
            "StreamDownloader only handles Streamable.Source.Raw, got ${source::class.simpleName}"
        }
        val streamProvider = source.streamProvider?.provide(0, -1)
            ?: throw Exception("Stream provider is null")

        val result = runCatching {
            HttpStreamUtil.download(
                file,
                streamProvider.first,
                streamProvider.second,
                progressFlow,
                receiveFlow
            )
        }
        result.getOrElse { file.delete() }
        return file
    }
}
