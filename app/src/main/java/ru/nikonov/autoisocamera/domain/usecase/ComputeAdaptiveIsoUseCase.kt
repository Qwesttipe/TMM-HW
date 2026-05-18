package ru.nikonov.autoisocamera.domain.usecase

import ru.nikonov.autoisocamera.domain.model.CameraCapabilities
import ru.nikonov.autoisocamera.domain.model.FrameData
import ru.nikonov.autoisocamera.domain.model.IsoParameters
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Computes the next ISO / exposure parameters from the latest frame luminance.
 *
 * Algorithm (photometric EMA):
 *  1. Convert luminance error to EV stops: ΔEV = log2(target / measured)
 *  2. Translate ΔEV to an ideal ISO: isoIdeal = isoPrev * 2^ΔEV
 *  3. Apply EMA smoothing to prevent flickering: isoSmoothed = α·isoIdeal + (1−α)·isoPrev
 *  4. Clamp to [minIso, maxIso] from CameraCapabilities.
 *  5. When ISO is saturated (maxed out), extend exposure time up to 1/30 s.
 *
 * This class is stateful and NOT thread-safe; always call [execute] on the same
 * coroutine context (the ViewModel's scope already serialises calls).
 */
class ComputeAdaptiveIsoUseCase {

    // EMA factor: 0.15 → ~6-frame lag, enough to kill frame-to-frame flicker.
    private val smoothingAlpha = 0.15f

    // Target mid-gray luminance (0–255 scale). Scene is "correctly exposed" here.
    val targetLuminance: Float = TARGET_LUMINANCE

    private var smoothedIso: Float = FALLBACK_ISO.toFloat()
    private var initialized = false

    /**
     * @param frameData   Result from the brightness analyzer for the current frame.
     * @param capabilities Hardware limits from [CameraCapabilities].
     * @return Parameters to pass to [ru.nikonov.autoisocamera.domain.repository.CameraRepository.applyIsoParameters].
     */
    fun execute(frameData: FrameData, capabilities: CameraCapabilities): IsoParameters {
        // Guard: luminance must be > 0 to avoid log(0).
        val luminance = frameData.luminance.coerceAtLeast(1f)

        // 1. EV stops needed to reach target.
        val deltaEv = log2(targetLuminance / luminance)

        // 2. Ideal ISO at full-correction step.
        val isoIdeal = (smoothedIso * 2f.pow(deltaEv))
            .coerceIn(capabilities.minIso.toFloat(), capabilities.maxIso.toFloat())

        // 3. EMA smoothing.
        smoothedIso = if (initialized) {
            smoothingAlpha * isoIdeal + (1f - smoothingAlpha) * smoothedIso
        } else {
            isoIdeal.also { initialized = true }
        }

        val isoInt = smoothedIso.roundToInt()
            .coerceIn(capabilities.minIso, capabilities.maxIso)

        // 4. Exposure strategy: hold 1/60 s for motion clarity; only open shutter
        //    to 1/30 s when ISO is already at maximum and scene is still dark.
        val isoSaturation = smoothedIso / capabilities.maxIso
        val exposureNs = when {
            isoSaturation > ISO_SATURATION_THRESHOLD ->
                EXPOSURE_30FPS_NS.coerceIn(capabilities.minExposureTimeNs, capabilities.maxExposureTimeNs)
            else ->
                EXPOSURE_60FPS_NS.coerceIn(capabilities.minExposureTimeNs, capabilities.maxExposureTimeNs)
        }

        return IsoParameters(iso = isoInt, exposureTimeNs = exposureNs)
    }

    /** Resets internal state. Call when the camera session restarts. */
    fun reset() {
        smoothedIso = FALLBACK_ISO.toFloat()
        initialized = false
    }

    companion object {
        const val TARGET_LUMINANCE = 128f
        const val FALLBACK_ISO = 200

        // 1/60 s in nanoseconds — baseline for 60 fps video.
        const val EXPOSURE_60FPS_NS = 16_666_667L

        // 1/30 s in nanoseconds — fallback for very dark scenes.
        const val EXPOSURE_30FPS_NS = 33_333_333L

        // ISO > 85 % of max triggers exposure time extension.
        private const val ISO_SATURATION_THRESHOLD = 0.85f
    }
}
