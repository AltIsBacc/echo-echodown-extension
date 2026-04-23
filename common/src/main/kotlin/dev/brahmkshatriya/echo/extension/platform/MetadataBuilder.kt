package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.models.DownloadContext
import java.io.File

interface MetadataBuilder {
    suspend fun buildMetadata(file: File, context: DownloadContext): File
}