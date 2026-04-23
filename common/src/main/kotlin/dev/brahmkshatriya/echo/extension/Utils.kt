package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.models.DownloadManifest
import dev.brahmkshatriya.echo.extension.models.TrackQuality

/**
 * Pure utility functions shared across the entire common module.
 *
 * Rules:
 *  - No Android imports.
 *  - No desktop-specific imports.
 *  - No reference to any concrete extension class.
 */
object Utils {
    /** Characters that are illegal in most filesystem paths. */
    private val illegalChars = "[/\\\\:*?\"<>|]".toRegex()

    /**
     * Replace all filesystem-illegal characters in [w] with underscores.
     * Previously duplicated in AndroidEDLExtension — canonical copy is here.
     */
    fun illegalReplace(w: String): String = illegalChars.replace(w, "_")

    // ── Quality selection ────────────────────────────────────────────────────

    /**
     * Pick one element from this list based on [quality].
     * [qualityOf] extracts the comparable integer quality from each element.
     *
     * [TrackQuality.HIGH]   → highest quality element
     * [TrackQuality.MEDIUM] → element closest to the middle
     * [TrackQuality.LOW]    → lowest quality element
     */
    fun <E> List<E>.selectQuality(quality: TrackQuality, qualityOf: (E) -> Int): E =
        when (quality) {
            TrackQuality.HIGH   -> maxByOrNull { qualityOf(it) } ?: first()
            TrackQuality.MEDIUM -> sortedBy { qualityOf(it) }[size / 2]
            TrackQuality.LOW    -> minByOrNull { qualityOf(it) } ?: first()
        }

    /** Select a [Streamable] from the list using [TrackQuality]. */
    fun List<Streamable>.select(quality: TrackQuality): Streamable =
        selectQuality(quality) { it.quality }

    /** Select a [Streamable.Source] from the list using [TrackQuality]. */
    fun List<Streamable.Source>.select(quality: TrackQuality): Streamable.Source =
        selectQuality(quality) { it.quality }

    // ── Media-item helpers ───────────────────────────────────────────────────

    fun EchoMediaItem.toManifestType(): DownloadManifest.ContextType = when (this) {
        is dev.brahmkshatriya.echo.common.models.Album -> DownloadManifest.ContextType.ALBUM
        else -> DownloadManifest.ContextType.PLAYLIST
    }
}
