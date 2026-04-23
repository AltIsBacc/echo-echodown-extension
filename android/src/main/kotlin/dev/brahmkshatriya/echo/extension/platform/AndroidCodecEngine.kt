package dev.brahmkshatriya.echo.extension.platform

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object AndroidCodecEngine : CodecEngine {

    override suspend fun executeCommand(
        command: String
    ): String = suspendCancellableCoroutine { cont ->
        val session = FFmpegKit.executeAsync(command, {
            println("FFmpeg Complete: $it")
            if (it.returnCode.isValueSuccess) cont.resume(it.output.orEmpty())
            else cont.resumeWithException(Exception(it.output))
        })
        cont.invokeOnCancellation { session.cancel() }
    }

    private suspend fun executeProbe(
        command: String,
        onLog: (String?) -> Unit = {}
    ): FFprobeSession = suspendCancellableCoroutine { cont ->
        val session = FFprobeKit.executeAsync(command, {
            if (it.returnCode.isValueSuccess) cont.resume(it)
            else cont.resumeWithException(Exception(it.output))
        }, { onLog(it.message) })
        cont.invokeOnCancellation { session.cancel() }
    }

    override suspend fun probeFormat(file: File, isVideo: Boolean): String {
        val ffprobeCommand =
            "-v error -show_entries format=format_name -of default=noprint_wrappers=1:nokey=1 \"${file.absolutePath}\""

        val session = runCatching { executeProbe(ffprobeCommand) }.getOrElse {
            return "mp3"
        }

        return when (session.output?.trim()?.split(",")?.firstOrNull()) {
            "mov", "mp4", "m4a" -> if(isVideo) "mp4" else "m4a"
            "flac" -> "flac"
            "ogg" -> "ogg"
            else -> "mp3"
        }
    }
}
