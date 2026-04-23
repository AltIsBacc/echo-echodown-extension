package dev.brahmkshatriya.echo.extension.pipeline

import dev.brahmkshatriya.echo.common.models.DownloadContext
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.extension.platform.ITask
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Ordered list of [ITask] instances that form the post-download pipeline.
 *
 * Platform subclasses register tasks once at startup:
 *
 * ```kotlin
 * taskRegistry.register(MergeTask(codecEngine, settings))
 * taskRegistry.register(TagTask(codecEngine, settings, musicExtensions, lyricsExtensions))
 * taskRegistry.register(LyricsTask(settings, lyricsExtensions))
 * ```
 *
 * Adding a new pipeline step is just implementing [ITask] and calling [register].
 */
class TaskRegistry {

    private val tasks = mutableListOf<ITask>()

    /** Append [task] to the end of the pipeline. */
    fun register(task: ITask) {
        tasks.add(task)
    }

    /**
     * Execute every registered task in registration order, threading the output
     * [File] of each step into the input of the next.
     *
     * @return The file produced by the last task.
     */
    suspend fun executeAll(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        initial: File
    ): File = tasks.fold(initial) { file, task ->
        task.execute(progressFlow, context, file)
    }
}
