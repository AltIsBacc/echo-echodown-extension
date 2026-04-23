package dev.brahmkshatriya.echo.extension.platform

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Android implementation of [ICodecEngine].
 *
 * Uses FFmpegKit under the hood
 */
object AndroidCodecEngine : ICodecEngine {

    override suspend fun executeCommand(command: String): String =
        suspendCancellableCoroutine { cont ->
            val session = FFmpegKit.executeAsync(command, { session ->
                if (session.returnCode.isValueSuccess) cont.resume(session.output.orEmpty())
                else cont.resumeWithException(Exception(session.output))
            })
            cont.invokeOnCancellation { session.cancel() }
        }

    override suspend fun probeFormat(file: File, isVideo: Boolean): String {
        val cmd = "-v error -show_entries format=format_name " +
                "-of default=noprint_wrappers=1:nokey=1 \"${file.absolutePath}\""

        val session = runCatching {
            suspendCancellableCoroutine { cont ->
                val s = FFprobeKit.executeAsync(cmd, { sess ->
                    if (sess.returnCode.isValueSuccess) cont.resume(sess)
                    else cont.resumeWithException(Exception(sess.output))
                }, { /* log callback — ignore */ })
                cont.invokeOnCancellation { s.cancel() }
            }
        }.getOrElse { return if (isVideo) "mp4" else "mp3" }

        return when (session.output?.trim()?.split(",")?.firstOrNull()) {
            "mov", "mp4", "m4a" -> if (isVideo) "mp4" else "m4a"
            "flac"               -> "flac"
            "ogg"                -> "ogg"
            else                 -> "mp3"
        }
    }
}
