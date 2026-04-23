package dev.brahmkshatriya.echo.extension.tasks

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.extension.Utils
import dev.brahmkshatriya.echo.extension.platform.CodecEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

object Merge {
    suspend fun merge(
        progressFlow: MutableSharedFlow<Progress>,
        context: DownloadContext,
        files: List<File>,
        trackNum: Boolean,
        isVideo: Boolean,
        codecEngine: CodecEngine? = null
    ): File {
        val file = files.first()
        progressFlow.emit(Progress(4, 1))
        val detectedExtension = codecEngine?.probeFormat(file, isVideo) ?: if (isVideo) "mp4" else "mp3"
        progressFlow.emit(Progress(4, 2))
        val sanitizedTitle =
            if (context.sortOrder != null && trackNum)
                "${context.sortOrder} ${Utils.illegalReplace(context.track.title)}"
            else
                Utils.illegalReplace(context.track.title)
        progressFlow.emit(Progress(4, 3))
        val finalFile = getUniqueFile(file.parentFile!!, sanitizedTitle, detectedExtension, file)
        progressFlow.emit(Progress(4, 4))
        return finalFile
    }

    fun getUniqueFile(directory: File, baseName: String, extension: String, f: File): File {
        val file = if(f.setWritable(true)) f else {
            File(directory, "$baseName-${f.hashCode()}.$extension").also {
                f.copyTo(it, true)
                f.delete()
            }
        }

        var uniqueName = "$baseName.$extension"
        var uniqueFile = File(directory, uniqueName)
        var counter = 1

        while (!file.renameTo(uniqueFile)) {
            uniqueName = "$baseName ($counter).$extension"
            uniqueFile = File(directory, uniqueName)
            counter++
        }

        return uniqueFile
    }
}