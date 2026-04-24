package dev.brahmkshatriya.echo.extension.processors

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.EDLDirectories
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.get
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.getExtension
import dev.brahmkshatriya.echo.extension.HttpStreamUtil
import dev.brahmkshatriya.echo.extension.utils.EDLUtils
import dev.brahmkshatriya.echo.extension.utils.EDLUtils.illegalReplace
import dev.brahmkshatriya.echo.extension.utils.OggCoverHelper
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.TrackMetadata
import dev.brahmkshatriya.echo.extension.platform.ICodecEngine
import dev.brahmkshatriya.echo.extension.platform.IManifestStore
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ITask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 2 processor: embed ID3 / Vorbis / MP4 metadata and cover art via FFmpeg,
 * then rename the file to the canonical `{Title}.{ext}` form.
 *
 * After tagging, [IManifestStore.recordTrackInManifest] is called to persist the
 * track reference so the playlist manifest stays up-to-date.
 */
class TagProcessor(
    private val codecEngine: ICodecEngine,
    private val settings: ISettingsProvider,
    private val manifestStore: IManifestStore,
    private val directories: EDLDirectories,
    private val musicExtensions: () -> List<MusicExtension>,
    private val outputDir: (DownloadContext) -> File,
    private val isVideo: () -> Boolean
) : ITask {

    override suspend fun execute(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File = withContext(Dispatchers.IO) {
        val tagged = runCatching {
            val extension = musicExtensions().getExtension(context.extensionId)
            
            val albumKey = "${context.extensionId}:${context.track.album?.id}"
            val album = albumCache.getOrPut(albumKey) { loadAlbum(extension, context.track) }
            
            progressFlow.emit(Progress(5, 1))
            val coverFile = saveCoverImage(file, context.track)
            
            progressFlow.emit(Progress(5, 2))
            ffmpegTag(
                file = file,
                context = context,
                coverFile = coverFile,
                lyricsText = null, // LyricsTask handles writing separately
                album = album,
                extensionName = extension?.name.orEmpty(),
                hasCover = false
            )
        }.getOrThrow()

        progressFlow.emit(Progress(5, 3))

        // Rename to canonical stable form: "{Title}.{ext}" with collision handling
        val sanitizedTitle = illegalReplace(context.track.title).ifBlank { context.track.id }
        val destDir = outputDir(context).also { it.mkdirs() }
        val stableFile = MergeProcessor.getUniqueFile(destDir, sanitizedTitle, file.extension.lowercase(), tagged)
        
        progressFlow.emit(Progress(5, 4))

        // Record this track in the manifest so playlist metadata stays consistent
        val contextItem = context.context
        if (contextItem != null) {
            manifestStore.recordTrackInManifest(
                extensionId = context.extensionId,
                contextId = contextItem.id,
                contextTitle = contextItem.title,
                contextType = EDLUtils.run {
                    contextItem.toManifestType()
                },
                trackKey = DownloadManifest.trackKey(context.extensionId, context.track.id),
                sortOrder = context.sortOrder
            )
        }

        // Save track metadata
        manifestStore.saveTrackMetadata(TrackMetadata.fromTrack(context.track, context.extensionId))

        progressFlow.emit(Progress(5, 5))
        stableFile
    }

    private suspend fun ffmpegTag(
        file: File,
        context: DownloadContext,
        coverFile: File?,
        lyricsText: String?,
        album: Album?,
        extensionName: String,
        hasCover: Boolean
    ): File {
        val outputFile = File(file.parent, "temp_${file.name}")
        val ext = file.extension.lowercase()
        val track = context.track

        val cmd = buildString {
            append("-y ")
            append("-i \"${file.absolutePath}\" ")
            when (ext) {
                "m4a", "flac", "mp3" -> {
                    if (coverFile != null && !hasCover) {
                        append("-i \"${coverFile.absolutePath}\" ")
                        append("-map 0:a -map 1:v -c:a copy -c:v copy ")
                        append("-disposition:v:0 attached_pic ")
                    } else {
                        append("-map 0 -c copy ")
                    }
                    if (ext == "mp3") append("-id3v2_version 3 ")
                }
                "ogg" -> {
                    if (coverFile != null && !hasCover) {
                        val blockPic = OggCoverHelper.vorbisPictureBlockBase64(coverFile)
                        append("-metadata METADATA_BLOCK_PICTURE=$blockPic ")
                    }
                    append("-c:a copy ")
                }
                else -> {
                    if (coverFile != null && !hasCover) {
                        append("-i \"${coverFile.absolutePath}\" -map 0 -map 1 ")
                    } else {
                        append("-map 0 ")
                    }
                    append("-c copy ")
                }
            }
            if (coverFile != null && !hasCover) {
                append("-metadata:s:v:0 title=\"Album cover\" ")
                append("-metadata:s:v:0 comment=\"Cover (front)\" ")
            }
            val dateTag = if (ext == "flac" || ext == "mp4") "year" else "date"
            append("-metadata track=\"${context.sortOrder ?: track.albumOrderNumber ?: 0}\" ")
            append("-metadata title=\"${illegalReplace(track.title)}\" ")
            append("-metadata artist=\"${track.artists.joinToString(", ") { it.name }}\" ")
            append("-metadata album=\"${illegalReplace(track.album?.title.orEmpty())}\" ")
            append("-metadata $dateTag=\"${track.releaseDate}\" ")
            append("-metadata album_artist=\"${album?.artists.orEmpty().joinToString(", ") { illegalReplace(it.name) }}\" ")
            append("-metadata genre=\"${track.genres.joinToString(", ")}\" ")
            append("-metadata discnumber=\"${track.albumDiscNumber}\" ")
            append("-metadata isrc=\"${track.isrc}\" ")
            append("-metadata service_name=\"$extensionName\" ")
            append("-metadata service_provider=Echo ")
            if (lyricsText != null) {
                val lyricsKey = if (ext == "mp3") "lyrics-eng" else "lyrics"
                append("-metadata $lyricsKey=\"${lyricsText.replace("\"", "'")}\" ")
            }
            append("\"${outputFile.absolutePath}\"")
        }

        val result = runCatching { codecEngine.executeCommand(cmd) }
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.toString().orEmpty()
            if (msg.contains("JPEG-LS support not enabled")) {
                // Retry without cover art
                return ffmpegTag(file, context, coverFile, lyricsText, album, extensionName, hasCover = true)
            }
            coverFile?.delete()
            throw result.exceptionOrNull()!!
        }

        if (file.delete()) outputFile.renameTo(file)
        coverFile?.delete()

        return file
    }

    private suspend fun loadAlbum(extension: Extension<*>?, track: Track): Album? =
        extension?.get<AlbumClient, Album?> {
            track.album?.let { loadAlbum(it) }
        }?.getOrNull() ?: track.album

    private suspend fun saveCoverImage(file: File, track: Track): File? {
        val coverFile = File(file.parent, "cover_temp_${track.hashCode()}.jpeg")
        if (coverFile.exists() && !coverFile.delete()) return null
        return runCatching {
            val request = when (val cover = track.cover) {
                is ImageHolder.ResourceUriImageHolder -> cover.uri.toImageHolder().request
                is ImageHolder.NetworkRequestImageHolder -> cover.request
                else -> throw IllegalArgumentException("Unsupported ImageHolder type: ${cover?.javaClass}")
            }
            HttpStreamUtil.okHttpDownload(coverFile, request)
        }.getOrElse {
            it.printStackTrace()
            coverFile.delete()
            null
        }
    }

    companion object {
        // Album cache is per-JVM process; size 50 is sufficient for typical playlist sizes.
        private val albumCache = mutableMapOf<String, Album?>()
    }
}
