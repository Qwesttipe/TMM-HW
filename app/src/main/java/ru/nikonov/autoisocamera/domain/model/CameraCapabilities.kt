package ru.nikonov.autoisocamera.domain.model

/**
 * Hardware limits read from CameraCharacteristics at startup.
 * Used to clamp computed ISO / exposure values to valid ranges.
 */
data class CameraCapabilities(
    val minIso: Int,
    val maxIso: Int,
    val minExposureTimeNs: Long,
    val maxExposureTimeNs: Long,
    /** True when SENSOR_INFO_SENSITIVITY_RANGE and REQUEST_AVAILABLE_CAPABILITIES include MANUAL_SENSOR. */
    val supportsManualSensor: Boolean,
)
