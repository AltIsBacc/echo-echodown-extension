package dev.brahmkshatriya.echo.extension.tasks

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.LruCache
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.AndroidEDLExtension
import dev.brahmkshatriya.echo.extension.AndroidEDLExtension.Companion.illegalReplace
import dev.brahmkshatriya.echo.extension.DownloadManifest
import dev.brahmkshatriya.echo.extension.Downloader.okHttpDownload
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.get
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.getExtension
import dev.brahmkshatriya.echo.extension.platform.AndroidCodecEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64

class Tag(
    private val androidED: AndroidEDLExtension
) {
    private val musicExtensions
        get() = androidED.musicExtensionList

    private val lyricsExtensions
        get() = androidED.lyricsExtensionList

    suspend fun tag(
        progressFlow: MutableSharedFlow<Progress>,
        context: DownloadContext,
        file: File,
        downloadsDir: File
    ): File = withContext(Dispatchers.IO) {
        val finalFile = runCatching {
            val track = context.track

            val albumKey = "${context.extensionId}:${track.album?.id}"
            val cachedAlbum = albumCache.get(albumKey)
            val extension = musicExtensions.getExtension(context.extensionId)
            val album = cachedAlbum ?: run {
                val loaded = loadAlbum(extension, track)
                if (loaded != null) albumCache.put(albumKey, loaded)
                loaded
            }
            progressFlow.emit(Progress(4, progress = 1))

            val coverFile = saveCoverBitmap(file, context.track)
            progressFlow.emit(Progress(4, progress = 2))

            val lyrics = androidED.run {
                getActualLyrics(context, downLyrics, syncLyrics, downFallbackLyrics, extension)
            }
            progressFlow.emit(Progress(4, progress = 3))

            writeTags(
                file,
                context,
                track,
                coverFile,
                lyrics,
                album,
                extension
            )
        }.getOrElse {
            throw it
        }
        progressFlow.emit(Progress(4, progress = 4))
        finalFile
    }

    private suspend fun writeTags(
        file: File,
        context: DownloadContext,
        track: Track,
        coverFile: File?,
        lyrics: Lyrics?,
        album: Album?,
        extension: Extension<*>?,
        hasCover: Boolean = false
    ): File {
        val lyricsText = when (val lyric = lyrics?.lyrics) {
            is Lyrics.Timed -> {
                lyric.list.joinToString("\n") { item ->
                    "${formatTime(item.startTime)}${item.text}"
                }
            }

            is Lyrics.Simple -> {
                lyric.text
            }

            else -> null
        }

        val fileExtension = file.extension.lowercase()
        val extName = extension?.name.orEmpty()

        val finalFile = runCatching {
            ffmpegTag(file, context, track, coverFile, lyricsText, fileExtension, album, extName, hasCover)
        }.getOrElse { e ->
            val eString = e.toString()
            if (eString.contains("JPEG-LS support not enabled")) {
                writeTags(file, context, track, coverFile, lyrics, album, extension, true)
            } else {
                coverFile?.delete()
                throw e
            }
        }
        return finalFile
    }

    private suspend fun ffmpegTag(
        file: File,
        context: DownloadContext,
        track: Track,
        coverFile: File?,
        lyricsText: String?,
        fileExtension: String,
        album: Album?,
        extName: String,
        hasCover: Boolean
    ): File {
        val outputFile = File(file.parent, "temp_${file.name}")

        val mdOrder = "track=\"${context.sortOrder ?: track.albumOrderNumber ?: 0}\""
        val mdTitle = "title=\"${illegalReplace(track.title)}\""
        val mdArtist = "artist=\"${track.artists.joinToString(", ") { it.name }}\""
        val mdAlbumArtist = "album_artist=\"${album?.artists.orEmpty().joinToString(", ") { illegalReplace(it.name)  }}\""
        val mdAlbumYear = if(fileExtension == "flac" || fileExtension == "mp4") "year=\"${track.releaseDate}\"" else "date=\"${track.releaseDate}\""
        val mdAlbum = "album=\"${illegalReplace(track.album?.title.orEmpty())}\""
        val mdGenre = "genre=\"${track.genres.joinToString(", ") { it }}\""
        val mdDisc =  "discnumber=\"${track.albumDiscNumber}\""
        val mdIsrc = "isrc=\"${track.isrc}\""
        val mdServiceProvider = "service_provider=Echo"
        val mdServiceName = "service_name=\"$extName\""

        val mdCoverTitle = "title=\"Album cover\""
        val medCoverComment = "comment=\"Cover (front)\""

        val cmd = buildString {
            append("-y ")
            append("-i \"${file.absolutePath}\" ")
            when (fileExtension) {
                "m4a", "flac", "mp3" -> {
                    if (coverFile != null && !hasCover) {
                        append("-i \"${coverFile.absolutePath}\" ")
                        append("-map 0:a ")
                        append("-map 1:v ")
                        append("-c:a copy ")
                        append("-c:v copy ")
                        append("-disposition:v:0 attached_pic ")
                    } else {
                        append("-map 0 ")
                        append("-c copy ")
                    }
                    if (fileExtension == "mp3") {
                        append("-id3v2_version 3 ")
                    }
                }

                "ogg" -> {
                    if (coverFile != null && !hasCover) {
                        val blockPic = vorbisPictureBlockBase64(coverFile)
                        append("-metadata METADATA_BLOCK_PICTURE=$blockPic ")
                    }
                    append("-c:a copy ")
                }

                else -> {
                    if (coverFile != null && !hasCover) {
                        append("-i \"${coverFile.absolutePath}\" ")
                        append("-map 0 ")
                        append("-map 1 ")
                    } else {
                        append("-map 0 ")
                    }
                    append("-c copy ")
                }
            }
            if (coverFile != null && !hasCover) {
                append("-metadata:s:v:0 $mdCoverTitle ")
                append("-metadata:s:v:0 $medCoverComment ")
            }
            append("-metadata $mdOrder ")
            append("-metadata $mdTitle ")
            append("-metadata $mdArtist ")
            append("-metadata $mdAlbum ")
            append("-metadata $mdAlbumYear ")
            append("-metadata $mdAlbumArtist ")
            append("-metadata $mdGenre ")
            append("-metadata $mdDisc ")
            append("-metadata $mdIsrc ")
            append("-metadata $mdServiceName ")
            append("-metadata $mdServiceProvider ")
            if (lyricsText != null) {
                if (fileExtension == "mp3") {
                    append("-metadata lyrics-eng=\"${lyricsText.replace("\"", "'")}\" ")
                } else {
                    append("-metadata lyrics=\"${lyricsText.replace("\"", "'")}\" ")
                }
            }
            append("\"${outputFile.absolutePath}\"")
        }

        AndroidCodecEngine.execute(cmd)
        if (file.delete()) outputFile.renameTo(file)

        coverFile?.delete()

        // Rename to "{Artist1, Artist2} - {Title}_{trackId}.{ext}"
        // The _trackId suffix guarantees uniqueness for remixes/live versions.
        val artists = context.track.artists
            .joinToString(", ") { illegalReplace(it.name) }
            .ifBlank { "Unknown Artist" }
        val title = illegalReplace(context.track.title).ifBlank { context.track.id }
        val trackId = DownloadManifest.sanitize(context.track.id)
        val stableFile = File(file.parentFile!!, "$artists - ${title}_${trackId}.$fileExtension")
        if (!file.renameTo(stableFile)) {
            file.copyTo(stableFile, overwrite = true)
            file.delete()
        }

        return stableFile
    }

    private suspend fun loadAlbum(
        extension: Extension<*>?,
        track: Track
    ): Album? {
        return extension?.get<AlbumClient, Album?> {
            track.album?.let { loadAlbum(it) }
        }?.getOrNull() ?: track.album
    }

    private suspend fun saveCoverBitmap(file: File, track: Track): File? {
        val coverFile = File(file.parent, "cover_temp_${track.hashCode()}.jpeg")
        if (coverFile.exists() && !coverFile.delete()) return null
        return runCatching {
            val request = when (val cover = track.cover) {
                is ImageHolder.ResourceUriImageHolder -> cover.uri.toImageHolder().request
                is ImageHolder.NetworkRequestImageHolder -> cover.request
                else -> throw IllegalArgumentException("Invalid ImageHolder type")
            }
            okHttpDownload(coverFile, request)
        }.getOrElse {
            it.printStackTrace()
            coverFile.delete()
            null
        }
    }

    private fun vorbisPictureBlockBase64(cover: File): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cover.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight

        val mimeBytes = "image/jpeg".toByteArray(Charsets.UTF_8)
        // 4(picture type) +4(mime len)+mime +4(desc len)+4*4(dim & depth)+4(data len)
        val hdrSize = 4 + 4 + mimeBytes.size + 4 + (4*4) + 4
        val imgLen = cover.length().toInt()

        val bb = ByteBuffer.allocate(hdrSize + imgLen)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                putInt(3) // front cover
                putInt(mimeBytes.size) // mime length
                put(mimeBytes) // "image/jpeg"
                putInt(0)  // desc length
                putInt(w) // width
                putInt(h) // height
                putInt(24)  // color depth
                putInt(0) // colors
                putInt(imgLen) // data length
            }

        cover.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read != -1) {
                bb.put(buffer, 0, read)
                read = input.read(buffer)
            }
        }

        return Base64.encode(bb.array())
    }

    private suspend fun getActualLyrics(
        context: DownloadContext,
        downLyrics: Boolean,
        syncLyrics: Boolean,
        downFallbackLyrics: String,
        extension: Extension<*>?
    ) : Lyrics? {
        try {
            if (!downLyrics || extension == null) return null
            val extensionLyrics = getLyrics(extension, context.track, context.extensionId)
            if (extensionLyrics != null &&
                (extensionLyrics.lyrics is Lyrics.Timed || extensionLyrics.lyrics is Lyrics.Simple)
                && (!syncLyrics || lyricsExtensions.isEmpty())
            ) return extensionLyrics
            val lyricsExtension =
                lyricsExtensions.getExtension(downFallbackLyrics)
                    ?: return null
            val lyrics = getLyrics(lyricsExtension, context.track, context.extensionId)
            if (lyrics != null && lyrics.lyrics is Lyrics.Timed) return lyrics
            return extensionLyrics
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun getLyrics(
        extension: Extension<*>,
        track: Track,
        clientId: String
    ): Lyrics? {
        val data = extension.get<LyricsClient, Feed<Lyrics>> {
            searchTrackLyrics(clientId, track)
        }
        val value = data.getOrNull()?.loadAll()?.firstOrNull()
        return if (value != null) {
            extension.get<LyricsClient, Lyrics> {
                loadLyrics(value)
            }.getOrNull()?.fillGaps()
        } else {
            null
        }
    }

    private fun Lyrics.fillGaps(): Lyrics {
        val lyrics = this.lyrics as? Lyrics.Timed
        return if (lyrics != null && lyrics.fillTimeGaps) {
            val new = mutableListOf<Lyrics.Item>()
            var last = 0L
            lyrics.list.forEach {
                if (it.startTime > last) {
                    new.add(Lyrics.Item("", last, it.startTime))
                }
                new.add(it)
                last = it.endTime
            }
            this.copy(lyrics = Lyrics.Timed(new))
        } else this
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Long): String {
        val mm = millis / 60000
        val remainder = millis % 60000
        val ss = remainder / 1000
        val ms = remainder % 1000

        val hundredths = (ms / 10)
        return String.format(TIME_FORMAT, mm, ss, hundredths)
    }

    companion object {
        private val albumCache = LruCache<String, Album>(50)

        private const val TIME_FORMAT = "[%02d:%02d.%02d]"
    }
}