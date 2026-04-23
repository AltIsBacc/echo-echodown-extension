package dev.brahmkshatriya.echo.extension.platform

import java.io.File

interface PlatformPaths {
    fun getTracksDir(): File
    fun getPlaylistsDir(): File
    fun getTempDir(): File
}