package com.kadhafi.aetherhop.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

class InertialStepDetector(context: Context? = null) {
    private val appContext = context?.applicationContext
    private val sensorManager = appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    companion object {
        const val STEP_THRESHOLD_HIGH = 11.2f // m/s^2 upper threshold
        const val STEP_THRESHOLD_LOW = 8.8f  // m/s^2 lower threshold
        const val MIN_STEP_INTERVAL_MS = 260L // Minimum time between consecutive steps
        const val DEFAULT_STRIDE_LENGTH_METERS = 0.75 // 75 cm average human tactical stride
    }

    private var aboveHighThreshold = false
    private var lastStepTimestampMs = 0L

    /**
     * Pure evaluator for testing and decoupled algorithmic processing.
     * Returns true when a complete zero-crossing step peak-to-valley cycle is detected.
     */
    fun processSample(magnitude: Float, timestampMs: Long): Boolean {
        if (magnitude > STEP_THRESHOLD_HIGH) {
            aboveHighThreshold = true
        } else if (magnitude < STEP_THRESHOLD_LOW && aboveHighThreshold) {
            if (timestampMs - lastStepTimestampMs >= MIN_STEP_INTERVAL_MS) {
                lastStepTimestampMs = timestampMs
                aboveHighThreshold = false
                return true
            }
            aboveHighThreshold = false
        }
        return false
    }

    fun reset() {
        aboveHighThreshold = false
        lastStepTimestampMs = 0L
    }

    fun observeSteps(): Flow<Long> = callbackFlow {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || accelerometer == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = sqrt(x * x + y * y + z * z)
                    val timestampMs = System.currentTimeMillis()

                    if (processSample(magnitude, timestampMs)) {
                        trySend(timestampMs)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
