package dev.brahmkshatriya.echo.extension.downloaders

import dev.brahmkshatriya.echo.common.models.Streamable
import java.io.InputStream

/** Discriminated union of the two raw download sources. */
sealed interface DownloadSource {
    data class Stream(val inputStream: InputStream, val totalBytes: Long) : DownloadSource
    data class Http(val source: Streamable.Source.Http) : DownloadSource
}
