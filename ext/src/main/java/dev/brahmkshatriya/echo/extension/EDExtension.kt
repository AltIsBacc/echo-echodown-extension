package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.LyricsExtension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.DownloadClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.RadioClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.providers.LyricsExtensionsProvider
import dev.brahmkshatriya.echo.common.providers.MusicExtensionsProvider

abstract class EDExtension : DownloadClient, MusicExtensionsProvider, LyricsExtensionsProvider {
    override val requiredMusicExtensions = listOf<String>()

    var musicExtensionList: List<MusicExtension> = emptyList()
    override fun setMusicExtensions(extensions: List<MusicExtension>) {
        musicExtensionList = extensions
    }

    override val requiredLyricsExtensions = listOf<String>()

    var lyricsExtensionList: List<LyricsExtension> = emptyList()
    override fun setLyricsExtensions(extensions: List<LyricsExtension>) {
        lyricsExtensionList = extensions
    }

    override suspend fun getDownloadTracks(
        extensionId: String, item: EchoMediaItem, context: EchoMediaItem?
    ) = when (item) {
        is Track -> listOf(DownloadContext(extensionId, item))
        is EchoMediaItem.Lists -> {
            val ext = musicExtensionList.getExtension(extensionId)!!
            val tracks = when (item) {
                is Album -> ext.get<AlbumClient, List<Track>> {
                    val album = loadAlbum(item)
                    loadTracks(album)?.loadAll() ?: emptyList()
                }

                is Playlist -> ext.get<PlaylistClient, List<Track>> {
                    val playlist = loadPlaylist(item)
                    loadTracks(playlist).loadAll()
                }

                is Radio -> ext.get<RadioClient, List<Track>> {
                    loadTracks(item).loadAll()
                }
            }.getOrThrow()
            tracks.mapIndexed { index, track ->
                DownloadContext(extensionId, track, index, item)
            }
        }
        else -> listOf()
    }

    companion object {
        fun List<Extension<*>>.getExtension(id: String?) = firstOrNull { it.id == id }

        suspend inline fun <reified T, R> Extension<*>.get(block: T.() -> R) = runCatching {
            val instance = instance.value().getOrThrow()
            if (instance !is T) throw ClientException.NotSupported("$name Extension: ${T::class.simpleName}")
            block.invoke(instance)
        }
    }
}
