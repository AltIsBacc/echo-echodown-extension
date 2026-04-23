package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.extension.models.TrackQualities

interface SettingsProvider {
    fun getConcurrentDownloads(): Int
    fun getQualityPreference(): TrackQualities
    fun shouldDownloadLyrics(): Boolean
    fun getDownloadFolder(): String // "Download", "Music", "Podcasts"
    fun shouldUseSubfolders(): Boolean
    fun shouldUseAlbumFolders(): Boolean
    fun shouldPrefixTrackNumbers(): Boolean
    fun getSubfolder(): String
}