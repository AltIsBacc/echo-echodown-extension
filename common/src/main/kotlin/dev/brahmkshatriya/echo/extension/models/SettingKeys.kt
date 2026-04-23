package dev.brahmkshatriya.echo.extension.models

/**
 * Shared setting-key constants.
 *
 * Both [AndroidEDLExtension] (which registers the UI) and [AndroidSettingsProvider]
 * (which reads the values) must refer to exactly the same string keys.
 * Centralising them here eliminates the risk of a typo causing a silent mismatch.
 */
object SettingKeys {
    const val DOWN_QUALITY        = "quality"
    const val DOWNLOAD_LYRICS     = "download_lyrics"
    const val FALLBACK_LYRICS_EXT = "fallback_lyrics_ext"
    const val SYNC_LYRICS         = "synced_lyrics"
    const val S_FOLDER            = "sfolder"
    const val M_FOLDER            = "mfolder"
    const val A_FOLDER            = "afolder"
    const val TRACK_NUM           = "tracknum"
    const val CONCURRENT_DOWNLOADS = "download_num"
}
