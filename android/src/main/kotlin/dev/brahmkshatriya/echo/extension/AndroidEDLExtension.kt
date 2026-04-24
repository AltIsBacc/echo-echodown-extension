package dev.brahmkshatriya.echo.extension

import android.annotation.SuppressLint
import android.app.Application
import android.os.Environment
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSlider
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.SettingKeys
import dev.brahmkshatriya.echo.extension.platform.AndroidCodecEngine
import dev.brahmkshatriya.echo.extension.platform.AndroidManifestStore
import dev.brahmkshatriya.echo.extension.platform.AndroidSettingsProvider
import java.io.File

@SuppressLint("PrivateApi")
class AndroidEDLExtension : EDLExtension() {

    private val contextApp: Application by lazy {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Application
    }

    private var _settings: Settings? = null
    override fun setSettings(settings: Settings) {
        _settings = settings
    }

    private val androidSettings: AndroidSettingsProvider by lazy {
        AndroidSettingsProvider(_settings ?: error("Settings have not been loaded"))
    }

    private val androidManifestStore: AndroidManifestStore by lazy {
        AndroidManifestStore(File(contextApp.cacheDir, "Echo"))
    }

    override fun getOutputDir(): File =
        Environment.getExternalStoragePublicDirectory(
            when (androidSettings.getDownloadFolder()) {
                "music"    -> Environment.DIRECTORY_MUSIC
                "podcasts" -> Environment.DIRECTORY_PODCASTS
                else       -> Environment.DIRECTORY_DOWNLOADS
            }
        ).let { base ->
            File(base, androidSettings.getSubfolder()).also { it.mkdirs() }
        }

    override suspend fun onInitialize() {
        initPlatform(AndroidCodecEngine, androidManifestStore, androidSettings)
    }

    override suspend fun getSettingItems(): List<Setting> = mutableListOf(
        SettingCategory(
            "General", "general",
            mutableListOf(
                SettingSlider(
                    "Concurrent Downloads", SettingKeys.CONCURRENT_DOWNLOADS,
                    "Number of concurrent downloads", 2, 1, 10, 1
                ),
                SettingList(
                    "Download Quality", SettingKeys.DOWN_QUALITY,
                    "Quality of your downloads",
                    mutableListOf("Highest", "Medium", "Lowest"),
                    mutableListOf("0", "1", "2"),
                    1
                )
            )
        ),
        SettingCategory(
            "Folders", "folders",
            mutableListOf(
                SettingList(
                    "Download Main-Folder", SettingKeys.M_FOLDER,
                    "Select the main folder for downloaded music",
                    mutableListOf("Download", "Music", "Podcasts"),
                    mutableListOf("download", "music", "podcasts"),
                    0
                ),
                SettingTextInput(
                    "Download Subfolder", SettingKeys.S_FOLDER,
                    "Set your preferred sub folder (use \"/\" for nesting, e.g. \"Echo/My Music\")",
                    "Echo"
                ),
                SettingSwitch(
                    "Put in Album folder", SettingKeys.A_FOLDER,
                    "Put songs inside album folder when downloaded as single", false
                ) // not implemented yet
            )
        ),
        SettingCategory(
            "Lyrics", "lyrics",
            mutableListOf<Setting>(
                SettingSwitch(
                    "Download Lyrics", SettingKeys.DOWNLOAD_LYRICS,
                    "Download lyrics for each track", true
                )
            ).apply {
                if (lyricsExtensionList.isNotEmpty()) {
                    addAll(listOf(
                        SettingSwitch(
                            "Synced Lyrics", SettingKeys.SYNC_LYRICS,
                            "Prefer synced lyrics from the lyrics extension", true
                        ),
                        SettingList(
                            "Fallback Lyrics Extension", SettingKeys.FALLBACK_LYRICS_EXT,
                            "Lyrics extension to use when none is found",
                            lyricsExtensionList.map { it.name },
                            lyricsExtensionList.map { it.id },
                            0
                        )
                    ))
                }
            }
        ),
        SettingCategory(
            "Customisation", "customisation",
            mutableListOf(
                SettingSwitch(
                    "Track Number in Title", SettingKeys.TRACK_NUM,
                    "Prefix track number to the title when downloading playlists/albums", false
                )
            )
        )
    )
}
