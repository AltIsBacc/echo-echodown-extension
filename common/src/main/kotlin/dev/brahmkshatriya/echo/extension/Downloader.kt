package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.Progress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object Downloader {

    private const val BUFFER_SIZE = 512 * 1024

    suspend fun download(
        file: File,
        stream: InputStream,
        totalBytes: Long,
        progressFlow: MutableSharedFlow<Progress>? = null,
        receiveFlow: MutableStateFlow<Long>? = null,
    ) = withContext(Dispatchers.IO) {
        progressFlow?.tryEmit(Progress(totalBytes))

        stream.buffered(BUFFER_SIZE).use { bis ->
            val fos = FileOutputStream(file, false)
            fos.buffered(BUFFER_SIZE).use { out ->
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

    val client by lazy { OkHttpClient() }

    suspend fun okHttpDownload(
        file: File,
        req: NetworkRequest,
        progressFlow: MutableSharedFlow<Progress>? = null,
        receiveFlow: MutableStateFlow<Long>? = null,
    ): File {
        val fileLength = file.length()
        receiveFlow?.value = fileLength
        val headers = req.headers.toMutableMap().apply {
            if (fileLength > 0) {
                put("Range", "bytes=$fileLength-")
            }
        }.toHeaders()
        val request = Request.Builder().url(req.url).headers(headers).build()
        val response = client.newCall(request).await()

        val totalBytes = fileLength + response.body.contentLength()
        return download(
            file,
            response.body.byteStream(),
            totalBytes,
            progressFlow,
            receiveFlow
        )
    }
}