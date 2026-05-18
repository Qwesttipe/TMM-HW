package ru.nikonov.autoisocamera.domain.repository

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import ru.nikonov.autoisocamera.domain.model.CameraCapabilities
import ru.nikonov.autoisocamera.domain.model.FrameData
import ru.nikonov.autoisocamera.domain.model.IsoParameters

interface CameraRepository {

    /** Latest analyzed frame; null before the first frame arrives. */
    val frameData: StateFlow<FrameData?>

    /** ISO/exposure parameters currently active on the sensor. */
    val activeParameters: StateFlow<IsoParameters?>

    /** Hardware limits; null until camera is opened and characteristics are read. */
    val capabilities: StateFlow<CameraCapabilities?>

    /** Smoothed FPS based on successive frame timestamps. */
    val fps: StateFlow<Float>

    /**
     * Binds Preview + ImageAnalysis use-cases to [lifecycleOwner].
     * Must be called from the main thread.
     *
     * @param surfaceProvider Obtained from PreviewView.surfaceProvider inside the Compose tree.
     */
    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    )

    /**
     * Pushes new ISO / exposure time to the sensor via Camera2Interop.
     * No-op when [CameraCapabilities.supportsManualSensor] is false.
     * Thread-safe; internally serialised onto the camera thread.
     */
    suspend fun applyIsoParameters(params: IsoParameters)

    /** Unbinds all use-cases and releases the camera. */
    fun stopCamera()
}
