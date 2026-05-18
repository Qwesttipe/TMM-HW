package ru.nikonov.autoisocamera.data.analyzer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import ru.nikonov.autoisocamera.domain.model.FrameData
import java.nio.ByteBuffer

/**
 * CameraX ImageAnalysis.Analyzer that extracts average scene luminance
 * from the YUV_420_888 Y-plane without any heap allocations per frame.
 *
 * Subsampling: every [SAMPLE_STEP]th pixel is read, giving a 64× speed-up
 * over full-resolution sampling while producing accurate average luminance.
 *
 * The analyzer runs on the executor supplied to ImageAnalysis.Builder; keep
 * it off the main thread (use Dispatchers.Default or a single-thread executor).
 */
class BrightnessAnalyzer(
    private val onFrameAnalyzed: (FrameData) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        // Y plane is always plane[0] in YUV_420_888.
        val yPlane: ImageProxy.PlaneProxy = image.planes[0]
        val buffer: ByteBuffer = yPlane.buffer
        val pixelStride: Int = yPlane.pixelStride
        val rowStride: Int = yPlane.rowStride

        val width = image.width
        val height = image.height

        var sum = 0L
        var count = 0

        // Walk the Y plane with pixel-stride awareness to handle padding rows correctly.
        var row = 0
        while (row < height) {
            val rowOffset = row * rowStride
            var col = 0
            while (col < width) {
                val index = rowOffset + col * pixelStride
                // ByteBuffer pixels are signed; AND with 0xFF to get unsigned [0,255].
                sum += buffer[index].toInt() and 0xFF
                count++
                col += SAMPLE_STEP
            }
            row += SAMPLE_STEP
        }

        val luminance = if (count > 0) sum.toFloat() / count else 0f

        val frameData = FrameData(
            luminance = luminance,
            timestampNs = image.imageInfo.timestamp,
            width = width,
            height = height,
        )

        // Always close before returning — CameraX recycles the buffer immediately after.
        image.close()

        onFrameAnalyzed(frameData)
    }

    companion object {
        // Sample every 8th pixel in both axes → 1/64 of all pixels, still accurate.
        private const val SAMPLE_STEP = 8
    }
}
