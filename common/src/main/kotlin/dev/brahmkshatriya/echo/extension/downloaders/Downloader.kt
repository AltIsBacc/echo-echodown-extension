package dev.brahmkshatriya.echo.extension.downloaders

import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.Downloader as DownloaderUtils
import dev.brahmkshatriya.echo.extension.platform.CodecEngine
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.InputStream

class Downloader(private val codecEngine: CodecEngine? = null) {
    private val receiveFlow = MutableStateFlow(0L)

    suspend fun download(
        file: File,
        progressFlow: MutableStateFlow<Progress>,
        source: DownloadSource
    ): File = when (source) {
        is DownloadSource.Stream -> downloadStream(file, progressFlow, source.inputStream, source.totalBytes)
        is DownloadSource.Http -> downloadHttp(file, progressFlow, source.source)
    }

    private suspend fun downloadStream(
        file: File,
        progressFlow: MutableStateFlow<Progress>,
        stream: InputStream,
        totalBytes: Long
    ): File {
        runCatching {
            DownloaderUtils.download(file, stream, totalBytes, progressFlow, receiveFlow)
        }.getOrElse {
            file.delete()
        }
        return file
    }

    private suspend fun downloadHttp(
        file: File,
        progressFlow: MutableStateFlow<Progress>,
        source: Streamable.Source.Http
    ): File {
        if (source.type != Streamable.SourceType.Progressive && codecEngine == null) {
            throw ClientException.NotSupported("Non-progressive HTTP downloads require a codec engine")
        }

        val result: Result<File> = if (source.type != Streamable.SourceType.Progressive) {
            ffmpegDownload(file, source, progressFlow)
        } else {
            runCatching {
                DownloaderUtils.okHttpDownload(file, source.request, progressFlow, receiveFlow)
            }
        }

        result.getOrElse {
            file.delete()
        }
        return file
    }

    private suspend fun ffmpegDownload(
        file: File,
        source: Streamable.Source.Http,
        progressFlow: MutableStateFlow<Progress>
    ): Result<File> = runCatching {
        if (file.exists()) file.delete()

        val headers = source.request.headers.entries.takeIf { it.isNotEmpty() }
            ?.joinToString("\r\n") { "${it.key}: ${it.value}" }.orEmpty()

        val ffmpegCommand = buildString {
            if (headers.isNotEmpty()) append("-headers \"$headers\" ")
            append("-extension_picky 0 ")
            append("-i \"${source.request.url}\" ")
            append("-c copy ")
            append("\"${file.absolutePath}\"")
        }

        progressFlow.emit(Progress())
        codecEngine!!.executeCommand(ffmpegCommand)
        file
    }
}
