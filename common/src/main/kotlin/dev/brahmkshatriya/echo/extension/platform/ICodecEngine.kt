package dev.brahmkshatriya.echo.extension.platform

import java.io.File

/**
 * Abstraction over FFmpeg/FFprobe operations.
 */
interface ICodecEngine {
    /**
     * Probe [file] and return its canonical format extension (e.g. "m4a", "flac", "ogg", "mp3").
     * [isVideo] is a hint — when the container is ambiguous (e.g. MOV/MP4/M4A) the return
     * value differs: "mp4" for video, "m4a" for audio.
     */
    suspend fun probeFormat(file: File, isVideo: Boolean): String

    /**
     * Execute an FFmpeg [command] string (everything after the `ffmpeg` binary name).
     * Returns stdout/stderr combined output on success; throws on non-zero exit.
     */
    suspend fun executeCommand(command: String): String
}
