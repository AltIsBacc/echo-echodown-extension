package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.extension.utils.EDLUtils.illegalReplace
import java.io.File

/**
 * Centralized directory structure management.
 *
 * All paths are evaluated on every access via the param lambdas,
 * so settings changes (e.g. [getBaseOutputDir]) propagate automatically.
 */
class EDLDirectories(
    private val publicBase: () -> File,
    private val privateBase: () -> File
) {

    val tracks: File    get() = File(publicBase(), "tracks").also { it.mkdirs() }
    val albums: File    get() = File(publicBase(), "albums").also { it.mkdirs() }
    val playlists: File get() = File(publicBase(), "playlists").also { it.mkdirs() }
    val radios: File    get() = File(publicBase(), "radios").also { it.mkdirs() }
    val metadata: File  get() = File(privateBase(), "metadata").also { it.mkdirs() }
    val lyrics: File    get() = File(privateBase(), "lyrics").also { it.mkdirs() }

    /**
     * Get the artist-specific track folder.
     */
    fun trackArtist(artist: String): File =
        File(tracks, illegalReplace(artist)).also { it.mkdirs() }

    /**
     * Determine the output directory for a given download context.
     * Audio files always go under tracks/{Artist}/ regardless of context.
     */
    fun outputFor(context: DownloadContext, useAlbumFolder: Boolean): File {
        val firstArtist = context.track.artists.firstOrNull()?.name ?: "Unknown Artist"
        return trackArtist(firstArtist)
    }

    /**
     * Returns the named subfolder for a context under the matching type directory.
     * Used only for writing metadata.json — never for audio output.
     *
     *   albums/{sanitizedTitle}/
     *   playlists/{sanitizedTitle}/
     *   radios/{sanitizedTitle}/
     */
    fun contextDirFor(context: DownloadContext): File? {
        val contextItem = context.context ?: return null
        val sanitizedTitle = illegalReplace(contextItem.title)
        val parent = when (contextItem) {
            is Album    -> albums
            is Radio    -> radios
            is Playlist -> playlists
            else        -> return null
        }
        return File(parent, sanitizedTitle).also { it.mkdirs() }
    }
}
