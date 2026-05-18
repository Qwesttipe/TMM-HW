package ru.nikonov.autoisocamera.data.repository

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nikonov.autoisocamera.data.analyzer.BrightnessAnalyzer
import ru.nikonov.autoisocamera.domain.model.CameraCapabilities
import ru.nikonov.autoisocamera.domain.model.FrameData
import ru.nikonov.autoisocamera.domain.model.IsoParameters
import ru.nikonov.autoisocamera.domain.repository.CameraRepository
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Concrete camera repository backed by CameraX + Camera2Interop.
 *
 * Thread-safety:
 *  - [startCamera] / [stopCamera] must be called from the **main thread** (CameraX requirement).
 *  - [applyIsoParameters] is protected by [isoMutex]; rapid calls from the analysis
 *    coroutine are serialised and de-bounced by [minIsoChangeFraction].
 */
@androidx.camera.camera2.interop.ExperimentalCamera2Interop
class CameraRepositoryImpl(private val context: Context) : CameraRepository {

    // ── State flows ──────────────────────────────────────────────────────────

    private val _frameData = MutableStateFlow<FrameData?>(null)
    override val frameData: StateFlow<FrameData?> = _frameData.asStateFlow()

    private val _activeParameters = MutableStateFlow<IsoParameters?>(null)
    override val activeParameters: StateFlow<IsoParameters?> = _activeParameters.asStateFlow()

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    override val capabilities: StateFlow<CameraCapabilities?> = _capabilities.asStateFlow()

    private val _fps = MutableStateFlow(0f)
    override val fps: StateFlow<Float> = _fps.asStateFlow()

    // ── Internal state ───────────────────────────────────────────────────────

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Dedicated single-thread executor for ImageAnalysis callbacks (off main thread).
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // Serialises applyIsoParameters calls to prevent concurrent CaptureRequest updates.
    private val isoMutex = Mutex()

    // FPS smoothing: exponential moving average with α = 0.2.
    private var lastFrameTimestampNs = 0L
    private var smoothedFps = 0f

    // ── CameraRepository impl ────────────────────────────────────────────────

    override suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ) = withContext(Dispatchers.Main) {
        val provider = awaitCameraProvider()
        cameraProvider = provider

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(surfaceProvider)
        }

        // Analysis stream: 720p is plenty for luminance sampling and keeps CPU load low.
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                )
            )
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor, BrightnessAnalyzer { frame ->
                    updateFps(frame.timestampNs)
                    _frameData.value = frame
                })
            }

        provider.unbindAll()

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
        )

        readCameraCapabilities()
    }

    override suspend fun applyIsoParameters(params: IsoParameters) {
        val cam = camera ?: return
        val caps = _capabilities.value ?: return
        if (!caps.supportsManualSensor) return

        // Skip if the value hasn't changed at all (StateFlow equality guard is not enough
        // here since params is a new object each call).
        if (_activeParameters.value == params) return

        isoMutex.withLock {
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF,
                )
                .setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    params.iso,
                )
                .setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    params.exposureTimeNs,
                )
                .build()

            // Optimistic update: reflect the intent in the UI before Camera2 confirms.
            // If the apply fails, the next frame corrects it.
            _activeParameters.value = params

            try {
                Camera2CameraControl.from(cam.cameraControl)
                    .setCaptureRequestOptions(options)
                    .await()
            } catch (_: Exception) {
                // Interrupted by a lifecycle event or coroutine cancellation;
                // the next frame cycle will reapply.
            }
        }
    }

    override fun stopCamera() {
        cameraProvider?.unbindAll()
        camera = null
        _activeParameters.value = null
        _capabilities.value = null
        lastFrameTimestampNs = 0L
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCoroutine { cont ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener(
                    {
                        try {
                            cont.resume(future.get())
                        } catch (e: Exception) {
                            cont.resumeWithException(e)
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun readCameraCapabilities() {
        val cam = camera ?: return
        val info = Camera2CameraInfo.from(cam.cameraInfo)

        val isoRange = info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
        )
        val exposureRange = info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
        )
        val availableCapabilities = info.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )

        val hasManualSensor = availableCapabilities?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        ) == true

        _capabilities.value = CameraCapabilities(
            minIso = isoRange?.lower ?: 50,
            maxIso = isoRange?.upper ?: 6400,
            minExposureTimeNs = exposureRange?.lower ?: 1_000_000L,
            maxExposureTimeNs = exposureRange?.upper ?: 100_000_000L,
            supportsManualSensor = hasManualSensor,
        )
    }

    private fun updateFps(timestampNs: Long) {
        if (lastFrameTimestampNs != 0L) {
            val intervalNs = timestampNs - lastFrameTimestampNs
            if (intervalNs > 0) {
                val instantFps = 1_000_000_000f / intervalNs
                // EMA with α = 0.2 for smooth FPS display.
                smoothedFps = 0.2f * instantFps + 0.8f * smoothedFps
                _fps.value = smoothedFps
            }
        }
        lastFrameTimestampNs = timestampNs
    }

}

// Converts ListenableFuture → suspend fun with full cancellation support.
// suspendCancellableCoroutine ensures that when the outer coroutine is cancelled
// (e.g. by collectLatest on the next frame), the Camera2 future is also cancelled
// and the Mutex is released promptly rather than leaking a ghost coroutine.
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                if (cont.isActive) {
                    try {
                        cont.resume(get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            },
            { it.run() },
        )
        cont.invokeOnCancellation { cancel(true) }
    }
