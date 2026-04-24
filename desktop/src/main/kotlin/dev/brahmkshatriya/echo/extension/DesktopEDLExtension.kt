package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.downloaders.FFmpegDownloader
import dev.brahmkshatriya.echo.extension.platform.DesktopManifestStore
import dev.brahmkshatriya.echo.extension.platform.DesktopSettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ProcessCodecEngine
import java.io.File

/**
 * Desktop concrete extension.
 */
class DesktopEDLExtension : EDLExtension() {

    override suspend fun onInitialize() {
        val playlistsDir = File(getOutputDir(), "playlists")
        val store = DesktopManifestStore(playlistsDir)

        initPlatform(ProcessCodecEngine, store, settings)
    }

    override fun getOutputDir(): File {
        val settings = DesktopSettingsProvider()
        val userHome = System.getProperty("user.home")
        val baseDir = File(userHome, "Downloads")
        return File(baseDir, settings.getSubfolder())
    }
}
