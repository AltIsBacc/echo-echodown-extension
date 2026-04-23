package dev.brahmkshatriya.echo.extension.platform

import android.os.Environment
import java.io.File

class AndroidPlatformPaths(private val settings: AndroidSettingsProvider) : PlatformPaths {
    private val baseDir: File
        get() = when (settings.getDownloadFolder()) {
            "music" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            "podcasts" -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }

    private val subfolder: String = settings.getSubfolder()

    override fun getTracksDir(): File = if (settings.shouldUseSubfolders()) File(baseDir, subfolder) else baseDir
    override fun getPlaylistsDir(): File = File(baseDir, "$subfolder/playlists")
    override fun getTempDir(): File = File(baseDir, "$subfolder/tmp")
}