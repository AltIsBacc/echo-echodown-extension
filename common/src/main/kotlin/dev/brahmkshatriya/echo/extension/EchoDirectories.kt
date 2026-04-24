package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.extension.utils.EDLUtils.illegalReplace
import java.io.File

/**
 * Centralized echo directory structure management.
 *
 * All paths are evaluated on every access via the [base] lambda,
 * so settings changes (e.g. [getBaseOutputDir]) propagate automatically.
 */
class EchoDirectories(private val base: () -> File) {

    val tracks: File   get() = File(base(), "tracks").also { it.mkdirs() }
    val albums: File   get() = File(base(), "albums").also { it.mkdirs() }
    val metadata: File get() = File(base(), "metadata").also { it.mkdirs() }
    val lyrics: File   get() = File(base(), "lyrics").also { it.mkdirs() }

    /**
     * Get the artist-specific track folder.
     */
    fun trackArtist(artist: String): File =
        File(tracks, illegalReplace(artist)).also { it.mkdirs() }

    /**
     * Get the album-specific folder.
     */
    fun album(title: String): File =
        File(albums, illegalReplace(title)).also { it.mkdirs() }

    /**
     * Determine the output directory for a given download context,
     * respecting the album folder setting.
     *
     * If [useAlbumFolder] and the context item is an Album or Playlist,
     * returns the album-organized folder; otherwise returns the artist folder.
     */
    fun outputFor(context: DownloadContext, useAlbumFolder: Boolean): File {
        if (useAlbumFolder) {
            val contextItem = context.context
            val folderName = when (contextItem) {
                is Album    -> illegalReplace(contextItem.title)
                is Playlist -> illegalReplace(contextItem.title)
                else        -> null
            }
            if (folderName != null) return album(folderName)
        }
        val firstArtist = context.track.artists.firstOrNull()?.name ?: "Unknown Artist"
        return trackArtist(firstArtist)
    }
}
