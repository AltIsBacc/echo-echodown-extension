package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.SettingKeys
import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Android implementation of [ISettingsProvider].
 *
 * Reads from Echo's [Settings] object using the keys defined in [SettingKeys].
 * Using [SettingKeys] constants here ensures the keys match those registered
 * in [AndroidEDLExtension.getSettingItems] — no silent mismatches.
 */
class AndroidSettingsProvider(private val settings: Settings) : ISettingsProvider {

    override fun getConcurrentDownloads(): Int =
        settings.getInt(SettingKeys.CONCURRENT_DOWNLOADS) ?: 2

    /**
     * Maps the raw stored string ("0", "1", "2") to a [TrackQuality] enum.
     * Callers never see the raw string — they always work with the typed enum.
     */
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

    override fun shouldUseSubfolders(): Boolean =
        settings.getString(SettingKeys.S_FOLDER)?.isNotEmpty() == true

    override fun shouldUseAlbumFolders(): Boolean =
        settings.getBoolean(SettingKeys.A_FOLDER) ?: false

    override fun shouldPrefixTrackNumbers(): Boolean =
        settings.getBoolean(SettingKeys.TRACK_NUM) ?: false

    override fun getSubfolder(): String =
        settings.getString(SettingKeys.S_FOLDER) ?: "Echo/"
}
