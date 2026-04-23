package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Low-level byte-transfer utilities used by the downloader implementations.
 *
 * This object has no knowledge of Streamable, tasks, or platform specifics.
 * It is intentionally kept dumb so it can be tested independently.
 */
object HttpStreamUtil {

    private const val BUFFER_SIZE = 512 * 1024

    /**
     * Write [stream] into [file], reporting throughput on [progressFlow].
     * Supports resume via [receiveFlow] — if the file already has bytes, the
     * caller is responsible for seeking [stream] to the right position before
     * passing it here.
     */
    suspend fun download(
        file: File,
        stream: InputStream,
        totalBytes: Long,
        progressFlow: MutableStateFlow<Progress>? = null,
        receiveFlow: MutableStateFlow<Long>? = null,
    ): File = withContext(Dispatchers.IO) {
        progressFlow?.tryEmit(Progress(totalBytes))

        stream.buffered(BUFFER_SIZE).use { bis ->
            FileOutputStream(file, false).buffered(BUFFER_SIZE).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var received = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                while (true) {
                    val bytesRead = bis.read(buffer).takeIf { it >= 0 } ?: break
                    out.write(buffer, 0, bytesRead)
                    received += bytesRead
                    receiveFlow?.value = received

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 1_000) {
                        val speed = received - lastBytes
                        lastBytes = received
                        lastTime = now
                        progressFlow?.tryEmit(Progress(totalBytes, received, speed))
                    }
                }
            }
        }
        file
    }

    val client: OkHttpClient by lazy { OkHttpClient() }

    /**
     * Download [req] into [file] using OkHttp, with byte-range resuming if
     * [file] already contains partial data.
     */
    suspend fun okHttpDownload(
        file: File,
        req: NetworkRequest,
        progressFlow: MutableStateFlow<Progress>? = null,
        receiveFlow: MutableStateFlow<Long>? = null,
    ): File {
        val fileLength = file.length()
        receiveFlow?.value = fileLength
        val headers = req.headers.toMutableMap().apply {
            if (fileLength > 0) put("Range", "bytes=$fileLength-")
        }.toHeaders()
        val request = Request.Builder().url(req.url).headers(headers).build()
        val response = client.newCall(request).await()
        val totalBytes = fileLength + response.body.contentLength()
        return download(file, response.body.byteStream(), totalBytes, progressFlow, receiveFlow)
    }
}
