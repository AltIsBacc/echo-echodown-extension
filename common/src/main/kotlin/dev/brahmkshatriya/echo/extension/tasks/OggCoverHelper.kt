package dev.brahmkshatriya.echo.extension.tasks

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64

/**
 * Builds the Base64-encoded METADATA_BLOCK_PICTURE string required for OGG Vorbis
 * cover art embedding.
 *
 * Extracted from Tag (which previously lived in android/tasks/) so that [TagTask]
 * in common can use it without any Android imports.
 *
 * The image dimensions are read from the JPEG/PNG file header directly,
 * removing the dependency on [android.graphics.BitmapFactory].
 */
internal object OggCoverHelper {

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    fun vorbisPictureBlockBase64(cover: File): String {
        val (w, h) = imageDimensions(cover)

        val mimeBytes = "image/jpeg".toByteArray(Charsets.UTF_8)
        val hdrSize = 4 + 4 + mimeBytes.size + 4 + (4 * 4) + 4
        val imgBytes = cover.readBytes()

        val bb = ByteBuffer.allocate(hdrSize + imgBytes.size)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                putInt(3)               // picture type: front cover
                putInt(mimeBytes.size)
                put(mimeBytes)
                putInt(0)               // description length
                putInt(w)
                putInt(h)
                putInt(24)              // colour depth
                putInt(0)              // indexed colours
                putInt(imgBytes.size)
                put(imgBytes)
            }

        return Base64.encode(bb.array())
    }

    /**
     * Read image width × height from the file header without decoding the full image.
     * Supports JPEG (SOF markers) and PNG (IHDR chunk).
     * Falls back to 0×0 on any parse error — FFmpeg will still embed the image.
     */
    private fun imageDimensions(file: File): Pair<Int, Int> = runCatching {
        file.inputStream().buffered().use { stream ->
            val header = ByteArray(24)
            stream.read(header)
            when {
                // PNG: 8-byte sig + "IHDR" chunk: width at offset 16, height at 20
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() -> {
                    val w = header.beInt(16)
                    val h = header.beInt(20)
                    w to h
                }
                // JPEG: skip to first SOF marker (0xFF 0xC0 / 0xC2)
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> {
                    parseJpegDimensions(file)
                }
                else -> 0 to 0
            }
        }
    }.getOrDefault(0 to 0)

    private fun parseJpegDimensions(file: File): Pair<Int, Int> {
        file.inputStream().buffered().use { stream ->
            stream.skip(2) // skip SOI
            val buf = ByteArray(4)
            while (stream.read(buf, 0, 2) == 2) {
                if (buf[0] != 0xFF.toByte()) break
                val marker = buf[1].toInt() and 0xFF
                stream.read(buf, 0, 2)
                val segLen = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                if (marker in 0xC0..0xC3) {
                    val seg = ByteArray(segLen - 2)
                    stream.read(seg)
                    val h = ((seg[1].toInt() and 0xFF) shl 8) or (seg[2].toInt() and 0xFF)
                    val w = ((seg[3].toInt() and 0xFF) shl 8) or (seg[4].toInt() and 0xFF)
                    return w to h
                } else {
                    stream.skip((segLen - 2).toLong())
                }
            }
        }
        return 0 to 0
    }

    private fun ByteArray.beInt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)
}
