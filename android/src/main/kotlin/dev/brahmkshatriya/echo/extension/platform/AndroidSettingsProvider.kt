package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.settings.Settings

class AndroidSettingsProvider(private val settings: Settings) : SettingsProvider {
    override fun getConcurrentDownloads(): Int = settings.getInt("CONCURRENT_DOWNLOADS") ?: 2
    override fun getQualityPreference(): String = settings.getString("DOWN_QUALITY") ?: "1"
    override fun shouldDownloadLyrics(): Boolean = settings.getBoolean("DOWNLOAD_LYRICS") ?: true
    override fun getDownloadFolder(): String = settings.getString("M_FOLDER") ?: "download"
    override fun shouldUseSubfolders(): Boolean = settings.getString("S_FOLDER")?.isNotEmpty() == true
    override fun shouldUseAlbumFolders(): Boolean = settings.getBoolean("A_FOLDER") ?: false
    override fun shouldPrefixTrackNumbers(): Boolean = settings.getBoolean("TRACK_NUM") ?: false
    override fun getSubfolder(): String = settings.getString("S_FOLDER") ?: "Echo/"
}