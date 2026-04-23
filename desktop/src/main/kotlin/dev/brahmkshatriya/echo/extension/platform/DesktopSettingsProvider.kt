package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Desktop implementation of [ISettingsProvider].
 *
 * Returns hardcoded defaults for now; a config-file backend will be added later.
 * Returns [TrackQuality] enum — never raw Strings.
 */
class DesktopSettingsProvider : ISettingsProvider {
    override fun getConcurrentDownloads(): Int = 2
    override fun getQualityPreference(): TrackQuality = TrackQuality.MEDIUM
    override fun shouldDownloadLyrics(): Boolean = false
    override fun shouldUseSyncedLyrics(): Boolean = false
    override fun getFallbackLyricsExtensionId(): String = ""
    override fun getDownloadFolder(): String = "download"
    override fun shouldUseSubfolders(): Boolean = true
    override fun shouldUseAlbumFolders(): Boolean = false
    override fun shouldPrefixTrackNumbers(): Boolean = false
    override fun getSubfolder(): String = "Echo/"
}
