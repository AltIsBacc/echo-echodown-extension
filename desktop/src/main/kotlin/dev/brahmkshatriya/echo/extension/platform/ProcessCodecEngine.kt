package dev.brahmkshatriya.echo.extension.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop implementation of [ICodecEngine].
 *
 * Delegates to a bundled or system-installed `ffmpeg` / `ffprobe` binary via
 * [ProcessBuilder].
 */
object ProcessCodecEngine : ICodecEngine {

    override suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        val args = listOf("ffmpeg") + splitCommand(command)
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw Exception("ffmpeg exited $exitCode:\n$output")
        output
    }

    override suspend fun probeFormat(file: File, isVideo: Boolean): String =
        withContext(Dispatchers.IO) {
            val args = listOf(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=format_name",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.absolutePath
            )
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            when (output.split(",").firstOrNull()) {
                "mov", "mp4", "m4a" -> if (isVideo) "mp4" else "m4a"
                "flac"               -> "flac"
                "ogg"                -> "ogg"
                else                 -> if (isVideo) "mp4" else "mp3"
            }
        }

    /**
     * Naive command splitter — splits on spaces while respecting double-quoted tokens.
     * Good enough for the FFmpeg commands we construct; not a full POSIX parser.
     */
    private fun splitCommand(command: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        for (ch in command) {
            when {
                ch == '"'         -> inQuote = !inQuote
                ch == ' ' && !inQuote -> {
                    if (current.isNotEmpty()) { result.add(current.toString()); current.clear() }
                }
                else              -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
