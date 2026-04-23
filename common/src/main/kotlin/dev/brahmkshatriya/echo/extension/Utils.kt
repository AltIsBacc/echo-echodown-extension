package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.models.DownloadManifest

object Utils {
    private val illegalChars = "[/\\\\:*?\"<>|]".toRegex()

    fun illegalReplace(w: String): String = illegalChars.replace(w, "_")

    fun <E> List<E>.selectQuality(setQuality: String, quality: (E) -> Int): E {
        return when (setQuality) {
            "0" -> this.maxByOrNull { quality(it) } ?: first()
            "1" -> sortedBy { quality(it) }[size / 2]
            "2" -> this.minByOrNull { quality(it) } ?: first()
            else -> first()
        }
    }

    fun List<Streamable>.select(setQuality: String) =
        this.selectQuality(setQuality) { it.quality }

    fun List<Streamable.Source>.select(setQuality: String) =
        this.selectQuality(setQuality) { it.quality }

    fun EchoMediaItem.toManifestType(): DownloadManifest.ContextType = when (this) {
        is dev.brahmkshatriya.echo.common.models.Album -> DownloadManifest.ContextType.ALBUM
        else -> DownloadManifest.ContextType.PLAYLIST
    }
}