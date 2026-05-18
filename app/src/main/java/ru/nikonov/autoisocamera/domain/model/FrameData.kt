package ru.nikonov.autoisocamera.domain.model

/**
 * Snapshot of a single analyzed video frame.
 *
 * @param luminance     Average Y-plane brightness in [0, 255].
 * @param timestampNs   System.nanoTime() at capture, used for FPS math.
 * @param width         Analysis-stream frame width in pixels.
 * @param height        Analysis-stream frame height in pixels.
 */
data class FrameData(
    val luminance: Float,
    val timestampNs: Long,
    val width: Int,
    val height: Int,
)
