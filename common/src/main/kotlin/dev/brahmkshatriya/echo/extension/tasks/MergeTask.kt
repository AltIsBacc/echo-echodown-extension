package dev.brahmkshatriya.echo.extension.tasks

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.extension.Utils.illegalReplace
import dev.brahmkshatriya.echo.extension.platform.ICodecEngine
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ITask
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Pipeline step 1: detect the container format, then rename the temp file
 * to a human-readable sanitised title.
 *
 * Renamed form: `"{sortOrder} {Title}.{ext}"` when track numbers are enabled,
 * else `"{Title}.{ext}"`.
 *
 */
class MergeTask(
    private val codecEngine: ICodecEngine?,
    private val settings: ISettingsProvider,
    private val isVideo: () -> Boolean
) : ITask {

    override suspend fun execute(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        progressFlow.emit(Progress(4, 1))
        val ext = codecEngine?.probeFormat(file, isVideo()) ?: if (isVideo()) "mp4" else "mp3"

        progressFlow.emit(Progress(4, 2))
        val sanitizedTitle = buildString {
            if (context.sortOrder != null && settings.shouldPrefixTrackNumbers()) {
                append("${context.sortOrder} ")
            }
            append(illegalReplace(context.track.title))
        }

        progressFlow.emit(Progress(4, 3))
        val finalFile = getUniqueFile(file.parentFile!!, sanitizedTitle, ext, file)

        progressFlow.emit(Progress(4, 4))
        return finalFile
    }

    companion object {
        /**
         * Rename [f] to `{baseName}.{extension}` inside [directory], appending a
         * counter suffix if a file with that name already exists.
         */
        fun getUniqueFile(directory: File, baseName: String, extension: String, f: File): File {
            val file = if (f.setWritable(true)) f else {
                File(directory, "$baseName-${f.hashCode()}.$extension").also {
                    f.copyTo(it, overwrite = true)
                    f.delete()
                }
            }
            var name = "$baseName.$extension"
            var target = File(directory, name)
            var counter = 1
            while (!file.renameTo(target)) {
                name = "$baseName ($counter).$extension"
                target = File(directory, name)
                counter++
            }
            return target
        }
    }
}
