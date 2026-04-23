package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Typed access to user preferences.
 *
 * All methods return concrete types — no raw Strings for enum-like values.
 *
 */
interface ISettingsProvider {
    fun getConcurrentDownloads(): Int
    fun getQualityPreference(): TrackQuality
    fun shouldDownloadLyrics(): Boolean
    fun shouldUseSyncedLyrics(): Boolean
    fun getFallbackLyricsExtensionId(): String
    fun getDownloadFolder(): String
    fun shouldUseSubfolders(): Boolean
    fun shouldUseAlbumFolders(): Boolean
    fun shouldPrefixTrackNumbers(): Boolean
    fun getSubfolder(): String
}
