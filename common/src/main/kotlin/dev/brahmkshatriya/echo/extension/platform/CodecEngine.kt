package dev.brahmkshatriya.echo.extension.platform

import java.io.File

interface CodecEngine {
    suspend fun probeFormat(file: File, isVideo: Boolean): String
    suspend fun executeCommand(command: String): String
}