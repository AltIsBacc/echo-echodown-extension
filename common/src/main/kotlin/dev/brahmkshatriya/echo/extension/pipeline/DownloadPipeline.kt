package dev.brahmkshatriya.echo.extension.pipeline

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Orchestrates a full single-track download pipeline:
 *
 * 1. [DownloadRegistry.download]  — fetch the raw file
 * 2. [TaskRegistry.executeAll]    — run MergeTask → TagTask → LyricsTask (… + any extras)
 */
class DownloadPipeline(
    private val downloadRegistry: DownloadRegistry,
    private val taskRegistry: TaskRegistry
) {

    /**
     * Execute the full pipeline for one [context] + [source].
     *
     * @param tempFile  Where to write the raw download before tasks run.
     *                  Typically `{tracksDir}/tmp_{trackKey}`.
     * @return          The final processed file, as returned by the last task.
     */
    suspend fun execute(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source,
        tempFile: File
    ): File {
        val rawFile = downloadRegistry.download(progressFlow, context, source, tempFile)
        return taskRegistry.executeAll(progressFlow, context, rawFile)
    }
}
