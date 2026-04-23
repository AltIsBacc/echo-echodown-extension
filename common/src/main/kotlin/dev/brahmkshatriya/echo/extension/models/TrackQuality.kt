package dev.brahmkshatriya.echo.extension.models

/**
 * Canonical quality enum used across the entire pipeline.
 * Replaces raw String "0"/"1"/"2" quality values.
 */
enum class TrackQuality {
    HIGH,   // "0" — highest bitrate / best source
    MEDIUM, // "1"
    LOW     // "2" — smallest / lowest bitrate
}
