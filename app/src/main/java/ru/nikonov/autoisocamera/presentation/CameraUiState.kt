package ru.nikonov.autoisocamera.presentation

/**
 * All data the UI needs to render the camera screen.
 * Immutable snapshot emitted by [CameraViewModel] each processing cycle.
 */
data class CameraUiState(
    val currentLuminance: Float = 0f,
    val targetLuminance: Float = 128f,
    val currentIso: Int = 0,
    val targetIso: Int = 0,
    val exposureTimeMs: Float = 0f,
    val fps: Float = 0f,
    val isAdaptiveEnabled: Boolean = true,
    val supportsManualSensor: Boolean = false,
    val error: String? = null,
)
