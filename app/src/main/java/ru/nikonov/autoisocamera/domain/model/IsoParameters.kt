package ru.nikonov.autoisocamera.domain.model

/**
 * Camera exposure parameters to apply for the next capture.
 *
 * @param iso               Sensor gain in ISO units (e.g. 100–6400).
 * @param exposureTimeNs    Sensor exposure duration in nanoseconds.
 */
data class IsoParameters(
    val iso: Int,
    val exposureTimeNs: Long,
)
