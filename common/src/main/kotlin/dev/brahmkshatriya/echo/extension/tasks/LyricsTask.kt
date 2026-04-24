package dev.brahmkshatriya.echo.extension.tasks

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.LyricsExtension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.EchoDirectories
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.get
import dev.brahmkshatriya.echo.extension.EDLExtension.Companion.getExtension
import dev.brahmkshatriya.echo.extension.platform.ISettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ITask
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Fetch lyrics and write them next to the audio file.
 *
 * Writes:
 *   - `{trackKey}.lrc`  for timed (synced) lyrics
 *   - `{trackKey}.txt`  for simple (unsynchronised) lyrics
 */
class LyricsTask(
    private val settings: ISettingsProvider,
    private val directories: EchoDirectories,
    private val musicExtensions: () -> List<MusicExtension>,
    private val lyricsExtensions: () -> List<LyricsExtension>
) : ITask {

    override suspend fun execute(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        if (!settings.shouldDownloadLyrics()) return file

        val extension = musicExtensions().getExtension(context.extensionId)
        val lyrics = getActualLyrics(context, extension) ?: return file

        val trackKey = file.nameWithoutExtension
        when (val lyric = lyrics.lyrics) {
            is Lyrics.Timed -> {
                val lrc = lyric.list.joinToString("\n") { item ->
                    "${formatTime(item.startTime)}${item.text}"
                }
                File(directories.lyrics, "$trackKey.lrc").writeText(lrc)
            }
            is Lyrics.Simple -> {
                File(directories.lyrics, "$trackKey.txt").writeText(lyric.text)
            }
            else -> Unit
        }
        return file
    }

    private suspend fun getActualLyrics(
        context: DownloadContext,
        extension: Extension<*>?
    ): Lyrics? = runCatching {
        if (extension == null) return null

        val extensionLyrics = getLyrics(extension, context.track, context.extensionId)
        val needsSynced = settings.shouldUseSyncedLyrics() && lyricsExtensions().isNotEmpty()

        if (extensionLyrics != null
            && (extensionLyrics.lyrics is Lyrics.Timed || extensionLyrics.lyrics is Lyrics.Simple)
            && !needsSynced
        ) return extensionLyrics

        val fallbackId = settings.getFallbackLyricsExtensionId()
        val lyricsExtension = lyricsExtensions().getExtension(fallbackId) ?: return extensionLyrics

        val syncedLyrics = getLyrics(lyricsExtension, context.track, context.extensionId)
        if (syncedLyrics?.lyrics is Lyrics.Timed) return syncedLyrics
        return extensionLyrics
    }.getOrElse {
        it.printStackTrace()
        null
    }

    private suspend fun getLyrics(
        extension: Extension<*>,
        track: Track,
        clientId: String
    ): Lyrics? {
        val feed = extension.get<LyricsClient, dev.brahmkshatriya.echo.common.models.Feed<Lyrics>> {
            searchTrackLyrics(clientId, track)
        }.getOrNull() ?: return null

        val value = feed.loadAll().firstOrNull() ?: return null
        return extension.get<LyricsClient, Lyrics> { loadLyrics(value) }
            .getOrNull()?.fillGaps()
    }

    private fun Lyrics.fillGaps(): Lyrics {
        val timed = lyrics as? Lyrics.Timed ?: return this
        if (!timed.fillTimeGaps) return this
        val filled = mutableListOf<Lyrics.Item>()
        var last = 0L
        timed.list.forEach { item ->
            if (item.startTime > last) filled.add(Lyrics.Item("", last, item.startTime))
            filled.add(item)
            last = item.endTime
        }
        return copy(lyrics = Lyrics.Timed(filled))
    }

    private fun formatTime(millis: Long): String {
        val mm = millis / 60000
        val remainder = millis % 60000
        val ss = remainder / 1000
        val hundredths = (remainder % 1000) / 10
        return String.format("[%02d:%02d.%02d]", mm, ss, hundredths)
    }
}
