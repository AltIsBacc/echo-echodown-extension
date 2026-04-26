package dev.brahmkshatriya.echo.extension.utils

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.extension.EDLDirectories
import dev.brahmkshatriya.echo.extension.models.ContextMetadata
import dev.brahmkshatriya.echo.extension.models.TrackQuality
import java.io.File

/**
 * Pure utility functions shared across the entire common module.
 */
object EDLUtils {
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

    fun EchoMediaItem.toManifestType(): ContextMetadata.ContextType = when (this) {
        is Album    -> ContextMetadata.ContextType.ALBUM
        is Radio    -> ContextMetadata.ContextType.RADIO
        else        -> ContextMetadata.ContextType.PLAYLIST
    }

    /**
     * Build a stable, filesystem-safe track key
     * used as the primary identifier in manifests and as the suffix of audio filenames.
     */
    fun trackKey(extensionId: String, trackId: String): String =
        "${illegalReplace(extensionId)}_${illegalReplace(trackId)}"

    /**
     * Returns true if a file whose name ends with `_{sanitizedTrackId}.{ext}`
     * already exists in the tracks directory and has non-zero size.
     * Used to skip re-downloading a track that was already fetched.
     */
    fun trackExists(directories: EDLDirectories, extensionId: String, trackId: String): Boolean =
        directories.tracks.walkTopDown().any {
            it.isFile &&
            it.nameWithoutExtension.endsWith(trackKey(extensionId, trackId)) &&
            it.length() > 0
        }
}
