package dev.brahmkshatriya.echo.extension

import android.annotation.SuppressLint
import android.app.Application
import android.os.Environment
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSlider
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.downloaders.HttpDownload
import dev.brahmkshatriya.echo.extension.downloaders.InputStreamDownload
import dev.brahmkshatriya.echo.extension.tasks.Merge
import dev.brahmkshatriya.echo.extension.tasks.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import java.io.File

@SuppressLint("PrivateApi")
class AndroidEntrypoint : EDLExtension() {

    val manifestManager by lazy {
        ManifestManager(File(contextApp.cacheDir, "Echo"))
    }

    private val contextApp by lazy {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication")
            .invoke(null) as Application
    }

    override suspend fun onInitialize() {
        
    }

    override suspend fun getDownloadTracks(
        extensionId: String,
        item: EchoMediaItem,
        context: EchoMediaItem?
    ): List<DownloadContext> {
        val all = super.getDownloadTracks(extensionId, item, context)
        if (item is EchoMediaItem.Lists) {
            return all.filter { ctx ->
                val alreadyHave = manifestManager.trackExists(extensionId, ctx.track.id)
                if (alreadyHave) {
                    val contextItem = ctx.context
                    if (contextItem != null) {
                        manifestManager.recordTrackInManifest(
                            extensionId = extensionId,
                            contextId = contextItem.id,
                            contextTitle = contextItem.title,
                            contextType = contextItem.toManifestType(),
                            trackKey = DownloadManifest.trackKey(extensionId, ctx.track.id),
                            sortOrder = ctx.sortOrder
                        )
                    }
                }
                !alreadyHave
            }
        }
        return all
    }

    private fun getDownloadFile(extensionId: String, trackId: String): File {
        return File(manifestManager.tracksDir, "tmp_${DownloadManifest.trackKey(extensionId, trackId)}")
    }

    override suspend fun selectServer(context: DownloadContext): Streamable {
        return context.track.servers.select(setQuality)
    }

    override suspend fun selectSources(
        context: DownloadContext, server: Streamable.Media.Server
    ): List<Streamable.Source> {
        val sources = mutableListOf<Streamable.Source>()
        sources.add(server.sources.select(setQuality))
        return sources
    }

    private val inputStreamDownload by lazy { InputStreamDownload() }
    private val httpDownload by lazy { HttpDownload() }

    private var isVideo: Boolean = false

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source
    ): File {
        isVideo = source.isVideo
        val file = getDownloadFile(context.extensionId, context.track.id)
        return when (source) {
            is Streamable.Source.Raw -> {
                val preFile = File(file.parent, "${source.hashCode()}.${if (isVideo) "mp4" else "mp3"}")
                val streamProvider = source.streamProvider?.provide(0, -1)
                if (streamProvider != null) {
                    inputStreamDownload.inputStreamDownload(
                        preFile,
                        progressFlow,
                        streamProvider.first,
                        streamProvider.second
                    )
                } else {
                    throw Exception("Streamprover is null")
                }
            }

            is Streamable.Source.Http -> {
                if(source.isLive) throw ClientException.NotSupported("Streams aren't supported")
                when (val decryption = source.decryption) {
                    null -> {
                        val preFile = File(manifestManager.tracksDir, "${source.request.hashCode()}.mp4")
                        httpDownload.httpDownload(
                            preFile,
                            progressFlow,
                            source
                        )
                    }

                    is Streamable.Decryption.Widevine -> {
                        TODO("Not shown for this repos & my safety")
                    }
                }
            }
        }
    }

    private val merge by lazy { Merge() }
    private val tag by lazy { Tag(this) }

    override suspend fun merge(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        files: List<File>
    ): File = merge.merge(progressFlow, context, files, trackNum, isVideo)

    override suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            when (downFolder) {
                in "music" -> Environment.DIRECTORY_MUSIC
                in "podcasts" -> Environment.DIRECTORY_PODCASTS
                else -> Environment.DIRECTORY_DOWNLOADS
            }
        )
        val finalFile = tag.tag(progressFlow, context, file, downloadsDir)

        val contextItem = context.context
        val extensionId = context.extensionId
        val trackKey = DownloadManifest.trackKey(extensionId, context.track.id)

        if (contextItem != null) {
            manifestManager.recordTrackInManifest(
                extensionId = extensionId,
                contextId = contextItem.id,
                contextTitle = contextItem.title,
                contextType = contextItem.toManifestType(),
                trackKey = trackKey,
                sortOrder = context.sortOrder
            )
        }

        return finalFile
    }

    private var _settings: Settings? = null
    private val setting: Settings
        get() = _settings ?: throw IllegalStateException("Settings have not been loaded.")
    override fun setSettings(settings: Settings) {
        _settings = settings
    }

    override suspend fun getSettingItems(): List<Setting> =
        mutableListOf(
            SettingCategory(
                "General",
                "general",
                mutableListOf(
                    SettingSlider(
                        "Concurrent Downloads",
                        CONCURRENT_DOWNLOADS,
                        "Number of concurrent downloads",
                        2,
                        1,
                        10,
                        1
                    ),
                    SettingList(
                        "Download Quality",
                        DOWN_QUALITY,
                        "Quality of your downloads",
                        mutableListOf("Highest", "Medium", "Lowest"),
                        mutableListOf("0", "1", "2"),
                        1
                    )
                )
            ),
            SettingCategory(
                "Folders",
                "folders",
                mutableListOf(
                    SettingList(
                        "Download Main-Folder",
                        M_FOLDER,
                        "Select the main folder for downloaded music (e.g. Download, Music etc.)",
                        mutableListOf("Download", "Music", "Podcasts"),
                        mutableListOf("download", "music", "podcasts"),
                        0
                    ),
                    SettingTextInput(
                        "Download Subfolder",
                        S_FOLDER,
                        "Set your preferred sub folder for downloaded music (Use \"/\" for more folders e.g. \"Echo/Your folder name\")",
                        "Echo/"
                    ),
                    SettingSwitch(
                        "Put in Album folder",
                        A_FOLDER,
                        "Put songs inside Album folder when Downloaded as single",
                        false
                    )
                )
            ),
            SettingCategory(
                "Lyrics",
                "lyrics",
                mutableListOf<Setting>(
                    SettingSwitch(
                        "Download Lyrics",
                        DOWNLOAD_LYRICS,
                        "Whether to download the lyrics for downloaded track or not",
                        true
                    )
                ).apply {
                    if (lyricsExtensionList.isNotEmpty()) {
                        addAll(
                            mutableListOf(
                                SettingSwitch(
                                    "Synced Lyrics",
                                    SYNC_LYRICS,
                                    "Use lyrics extension to get synced lyrics. Regardless of the music extension having synced lyrics",
                                    true
                                ),
                                SettingList(
                                    "Fallback Lyrics Extension",
                                    FALLBACK_LYRICS_EXT,
                                    "The lyrics extension to use, when no lyrics are found",
                                    lyricsExtensionList.map { it.name },
                                    lyricsExtensionList.map { it.id },
                                    0
                                )
                            )
                        )
                    }
                }
            ),
            SettingCategory(
                "Customization",
                "customization",
                mutableListOf<Setting>(
                    SettingSwitch(
                        "Track Number In Title",
                        TRACK_NUM,
                        "Tracks have the order number in their title when downloading Playlists/Albums",
                        false
                    )
                )
            )
        )

    override val concurrentDownloads: Int
        get() = setting.getInt(CONCURRENT_DOWNLOADS) ?: 2

    private val setQuality: String
        get() = setting.getString(DOWN_QUALITY) ?: "1"

    private val trackNum: Boolean
        get() = setting.getBoolean(TRACK_NUM) ?: false

    private val downFolder: String
        get() =  setting.getString(M_FOLDER) ?: "download"

    val folderStructure: String
        get() =  setting.getString(S_FOLDER) ?: "Echo/"

    val albumFolder: Boolean
        get() = setting.getBoolean(A_FOLDER) ?: false

    val downLyrics: Boolean
        get() = setting.getBoolean(DOWNLOAD_LYRICS) ?: true

    val downFallbackLyrics: String
        get() = setting.getString(FALLBACK_LYRICS_EXT) ?: ""

    val syncLyrics: Boolean
        get() = setting.getBoolean(SYNC_LYRICS) ?: true

    companion object {
        private const val DOWN_QUALITY = "quality"
        private const val DOWNLOAD_LYRICS = "download_lyrics"
        private const val FALLBACK_LYRICS_EXT = "fallback_lyrics_ext"
        private const val SYNC_LYRICS = "synced_lyrics"
        private const val S_FOLDER = "sfolder"
        private const val M_FOLDER = "mfolder"
        private const val A_FOLDER = "afolder"
        private const val TRACK_NUM = "tracknum"
        private const val CONCURRENT_DOWNLOADS = "download_num"

        private val illegalChars = "[/\\\\:*?\"<>|]".toRegex()

        private val disabledReq = Request.Builder().url("https://raw.githubusercontent.com/LuftVerbot/echo-echodown-extension/refs/heads/main/disabledExt.txt").build()

        fun illegalReplace(w: String): String = illegalChars.replace(w, "_")

        fun EchoMediaItem.toManifestType(): DownloadManifest.ContextType = when (this) {
            is Album -> DownloadManifest.ContextType.ALBUM
            else -> DownloadManifest.ContextType.PLAYLIST
        }

        // For Shivam: Copy my homework but don't make it obvious
        private fun <E> List<E>.selectQuality(setQuality: String, quality: (E) -> Int): E {
            return when (setQuality) {
                in "0" -> this.maxByOrNull { quality(it) } ?: first()
                in "1" -> sortedBy { quality(it) }[size / 2]
                in "2" -> this.minByOrNull { quality(it) } ?: first()
                else -> first()
            }
        }

        private fun List<Streamable>.select(setQuality: String) =
            selectQuality(setQuality) { it.quality }

        private fun List<Streamable.Source>.select(setQuality: String) =
            selectQuality(setQuality) { it.quality }
    }
}