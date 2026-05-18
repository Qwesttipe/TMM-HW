package ru.nikonov.autoisocamera.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps the ambient light sensor as a cold [Flow] of lux values.
 *
 * The sensor is registered lazily when the flow is collected and unregistered
 * automatically when the collector cancels (follows structured concurrency).
 *
 * Returns null when the device has no light sensor — callers should treat null
 * as "sensor unavailable" and rely solely on the camera-derived luminance.
 */
class AmbientLightSensorManager(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** True when the device exposes a hardware ambient-light sensor. */
    val isAvailable: Boolean get() = lightSensor != null

    /**
     * Emits lux readings at [SensorManager.SENSOR_DELAY_NORMAL] rate (~5 Hz).
     * Completes without emitting when no sensor is present.
     */
    fun luxFlow(): Flow<Float> = callbackFlow {
        val sensor = lightSensor ?: run {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
