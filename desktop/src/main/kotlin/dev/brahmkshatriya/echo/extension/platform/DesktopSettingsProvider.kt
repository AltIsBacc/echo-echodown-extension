package dev.brahmkshatriya.echo.extension.platform

class DesktopSettingsProvider : SettingsProvider {
    override fun getConcurrentDownloads(): Int = 2
    override fun getQualityPreference(): String = "1"
    override fun shouldDownloadLyrics(): Boolean = false
    override fun getDownloadFolder(): String = "download"
    override fun shouldUseSubfolders(): Boolean = true
    override fun shouldUseAlbumFolders(): Boolean = false
    override fun shouldPrefixTrackNumbers(): Boolean = false
    override fun getSubfolder(): String = "Echo/"
}