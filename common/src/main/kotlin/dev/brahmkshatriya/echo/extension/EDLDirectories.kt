package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.extension.utils.EDLUtils.illegalReplace
import java.io.File

/**
 * Centralized directory structure management.
 *
 * All paths are evaluated on every access via thel] lambda,
 * so settings changes (e.g. [getBaseOutputDir]) propagate automatically.
 */
class EDLDirectories(
    private val publicBase: () -> File,
    private val privateBase: () -> File
) {

    val tracks: File     get() = File(publicBase(), "tracks").also { it.mkdirs() }
    val playlists: File  get() = File(publicBase(), "playlists").also { it.mkdirs() }
    val metadata: File   get() = File(privateBase(), "metadata").also { it.mkdirs() }
    val lyrics: File     get() = File(privateBase(), "lyrics").also { it.mkdirs() }

    /**
     * Get the artist-specific track folder.
     */
    fun trackArtist(artist: String): File =
        File(tracks, illegalReplace(artist)).also { it.mkdirs() }

    /**
     * Get the playlist-specific folder.
     */
    fun trackPlaylist(title: String): File =
        File(playlists, illegalReplace(title)).also { it.mkdirs() }

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
            if (folderName != null) return trackPlaylist(folderName)
        }
        val firstArtist = context.track.artists.firstOrNull()?.name ?: "Unknown Artist"
        return trackArtist(firstArtist)
    }
}
