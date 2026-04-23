package dev.brahmkshatriya.echo.extension.downloaders

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.platform.ICodecEngine
import dev.brahmkshatriya.echo.extension.platform.IDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Downloads HLS / DASH (non-progressive) HTTP sources by delegating to FFmpeg.
 *
 * Requires an [ICodecEngine] — registered in [DownloadRegistry] only on platforms
 * that supply one (Android, Desktop with system FFmpeg).
 */
class FfmpegDownloader(private val codecEngine: ICodecEngine) : IDownloader {

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        file: File
    ): File {
        require(source is Streamable.Source.Http) {
            "FfmpegDownloader only handles Streamable.Source.Http, got ${source::class.simpleName}"
        }
        val result = runCatching {
            if (file.exists()) file.delete()

            val headers = source.request.headers.entries.takeIf { it.isNotEmpty() }
                ?.joinToString("\r\n") { "${it.key}: ${it.value}" }.orEmpty()

            val cmd = buildString {
                if (headers.isNotEmpty()) append("-headers \"$headers\" ")
                append("-extension_picky 0 ")
                append("-i \"${source.request.url}\" ")
                append("-c copy ")
                append("\"${file.absolutePath}\"")
            }

            progressFlow.emit(Progress())
            codecEngine.executeCommand(cmd)
            file
        }
        result.getOrElse { file.delete() }
        return file
    }
}
