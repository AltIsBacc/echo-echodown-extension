package dev.brahmkshatriya.echo.extension.models

data class TrackManifest(
    val trackId: String,  // stable key: "{extensionId}_{sanitizedTrackId}"
    val sortOrder: Int?,
    val addedAt: Long     // epoch ms
)