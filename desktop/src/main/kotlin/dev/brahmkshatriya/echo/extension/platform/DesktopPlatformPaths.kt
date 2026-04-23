package dev.brahmkshatriya.echo.extension.platform

import java.io.File

class DesktopPlatformPaths(private val settings: DesktopSettingsProvider) : PlatformPaths {
    private val userHome = System.getProperty("user.home")
    private val baseDir = File(userHome, "Downloads") // For now, hardcoded to Downloads, but can be made configurable later
    private val subfolder: String = settings.getSubfolder()

    override fun getTracksDir(): File = if (settings.shouldUseSubfolders()) File(baseDir, subfolder) else baseDir
    override fun getPlaylistsDir(): File = File(baseDir, "$subfolder/playlists")
    override fun getTempDir(): File = File(baseDir, "$subfolder/tmp")
}