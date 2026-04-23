package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Typed access to user preferences.
 *
 * All methods return concrete types — no raw Strings for enum-like values.
 *
 * Android implementation: [AndroidSettingsProvider] (reads Echo's Settings object).
 * Desktop implementation: [DesktopSettingsProvider] (hardcoded defaults; config file later).
 */
interface ISettingsProvider {
    fun getConcurrentDownloads(): Int
    /** Returns a [TrackQuality] enum — never a raw String. */
    fun getQualityPreference(): TrackQuality
    fun shouldDownloadLyrics(): Boolean
    fun shouldUseSyncedLyrics(): Boolean
    fun getFallbackLyricsExtensionId(): String
    /** One of "download", "music", "podcasts" — only used on Android to pick the OS directory. */
    fun getDownloadFolder(): String
    fun shouldUseSubfolders(): Boolean
    fun shouldUseAlbumFolders(): Boolean
    fun shouldPrefixTrackNumbers(): Boolean
    fun getSubfolder(): String
}
