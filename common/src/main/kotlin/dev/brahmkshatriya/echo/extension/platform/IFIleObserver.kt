package dev.brahmkshatriya.echo.extension.platform

abstract class IFileObserver(
    dir: File
) {
    abstract fun start()
    abstract fun stop()
}
