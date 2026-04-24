package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Typed access to user preferences.
 */
interface ISettingsProvider {
    fun getConcurrentDownloads(): Int
    fun getQualityPreference(): TrackQuality

    // getDownloadFolder + getSubfolder
    fun getDownloadFolder(): String
    fun getSubfolder(): String

    fun shouldDownloadLyrics(): Boolean
    fun shouldUseSyncedLyrics(): Boolean
    fun getFallbackLyricsExtensionId(): String

    fun shouldPrefixTrackNumbers(): Boolean
}
