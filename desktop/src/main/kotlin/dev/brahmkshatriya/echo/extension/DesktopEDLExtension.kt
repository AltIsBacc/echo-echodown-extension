package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.extension.platform.DesktopManifestStore
import dev.brahmkshatriya.echo.extension.platform.DesktopSettingsProvider
import dev.brahmkshatriya.echo.extension.platform.ProcessCodecEngine
import java.io.File

/**
 * Desktop concrete extension.
 */
class DesktopEDLExtension : EDLExtension() {

    override suspend fun onInitialize() {
        val baseDir = getBaseOutputDir()
        val playlistsDir = File(baseDir, "playlists")
        val dirs = EchoDirectories { getBaseOutputDir() }
        val store = DesktopManifestStore(playlistsDir, dirs)

        initPlatform(ProcessCodecEngine, store, DesktopSettingsProvider())
    }

    override fun getBaseOutputDir(): File {
        val settings = DesktopSettingsProvider()
        val userHome = System.getProperty("user.home")
        val baseDir = File(userHome, "Downloads")
        return File(baseDir, settings.getSubfolder())
    }
}
