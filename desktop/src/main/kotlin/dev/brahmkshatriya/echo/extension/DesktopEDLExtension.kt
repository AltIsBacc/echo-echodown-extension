package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.downloaders.FfmpegDownloader
import dev.brahmkshatriya.echo.extension.downloaders.HttpDownloader
import dev.brahmkshatriya.echo.extension.downloaders.StreamDownloader
import dev.brahmkshatriya.echo.extension.platform.DesktopManifestStore
import dev.brahmkshatriya.echo.extension.platform.DesktopSettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ProcessCodecEngine
import dev.brahmkshatriya.echo.extension.tasks.LyricsTask
import dev.brahmkshatriya.echo.extension.tasks.MergeTask
import dev.brahmkshatriya.echo.extension.tasks.TagTask
import java.io.File

/**
 * Desktop concrete extension.
 */
class DesktopEDLExtension : EDLExtension() {

    override suspend fun onInitialize() {
        val settings = DesktopSettingsProvider()
        val userHome = System.getProperty("user.home")
        val baseDir = File(userHome, "Downloads")
        val subDir = if (settings.shouldUseSubfolders()) File(baseDir, settings.getSubfolder()) else baseDir
        val playlistsDir = File(subDir, "playlists")
        val store = DesktopManifestStore(playlistsDir)

        initPlatform(ProcessCodecEngine, store, settings)

        // Downloaders
        downloadRegistry.register("http",   HttpDownloader())
        downloadRegistry.register("stream", StreamDownloader())
        downloadRegistry.register("ffmpeg", FfmpegDownloader(ProcessCodecEngine))

        // Pipeline tasks — order matters
        taskRegistry.register(MergeTask(ProcessCodecEngine, settings, ::isVideo))
        taskRegistry.register(
            TagTask(
                codecEngine     = ProcessCodecEngine,
                settings        = settings,
                manifestStore   = manifestStore,
                musicExtensions = { musicExtensionList },
                outputDir       = { subDir },
                isVideo         = ::isVideo
            )
        )
        taskRegistry.register(
            LyricsTask(
                settings         = settings,
                musicExtensions  = { musicExtensionList },
                lyricsExtensions = { lyricsExtensionList }
            )
        )
    }

    override val concurrentDownloads: Int
        get() = settingsProvider.getConcurrentDownloads()
}
