package dev.brahmkshatriya.echo.extension.platform

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * A discrete step in the post-download pipeline.
 *
 * Tasks are registered (in order) in [TaskRegistry] and executed sequentially
 * by [DownloadPipeline] after the initial file download completes.
 *
 * Concrete implementations in common/tasks/:
 *   - [MergeTask]   — probe format, rename file to sanitised title
 *   - [TagTask]     — embed metadata + cover art via FFmpeg
 *   - [LyricsTask]  — fetch & write .lrc / .txt
 *
 * Platform subclasses may add additional tasks by calling
 * `taskRegistry.register(MyTask(...))` in their `init` / `onInitialize`.
 */
interface ITask {
    /**
     * Execute this task on [file].
     *
     * @param progressFlow Report fine-grained progress here.
     * @param context      Full download context (track, extension, sort order, …).
     * @param file         The file produced by the previous pipeline step.
     * @return             The file to pass to the next pipeline step
     *                     (may be the same file, renamed, or a new file).
     */
    suspend fun execute(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File
}
