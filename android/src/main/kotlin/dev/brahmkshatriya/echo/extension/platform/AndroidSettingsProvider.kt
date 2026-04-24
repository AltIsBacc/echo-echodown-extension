package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.SettingKeys
import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Android implementation of [ISettingsProvider].
 */
class AndroidSettingsProvider(private val settings: Settings) : ISettingsProvider {

    override fun getConcurrentDownloads(): Int =
        settings.getInt(SettingKeys.CONCURRENT_DOWNLOADS) ?: 2

    override fun getQualityPreference(): TrackQuality = when (
        settings.getString(SettingKeys.DOWN_QUALITY) ?: "1"
    ) {
        "0"  -> TrackQuality.HIGH
        "2"  -> TrackQuality.LOW
        else -> TrackQuality.MEDIUM
    }

    override fun shouldDownloadLyrics(): Boolean =
        settings.getBoolean(SettingKeys.DOWNLOAD_LYRICS) ?: true

    override fun shouldUseSyncedLyrics(): Boolean =
        settings.getBoolean(SettingKeys.SYNC_LYRICS) ?: true

    override fun getFallbackLyricsExtensionId(): String =
        settings.getString(SettingKeys.FALLBACK_LYRICS_EXT) ?: ""

    override fun getDownloadFolder(): String =
        settings.getString(SettingKeys.M_FOLDER) ?: "download"

    override fun shouldPrefixTrackNumbers(): Boolean =
        settings.getBoolean(SettingKeys.TRACK_NUM) ?: false

    override fun getSubfolder(): String =
        settings.getString(SettingKeys.S_FOLDER) ?: "Echo"
}
