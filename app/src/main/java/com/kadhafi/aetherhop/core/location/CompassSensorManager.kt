package com.kadhafi.aetherhop.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class CompassSensorManager(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    companion object {
        fun smoothAzimuth(previousDeg: Float, newDeg: Float, alpha: Float = 0.20f): Float {
            val prevRad = Math.toRadians(previousDeg.toDouble())
            val newRad = Math.toRadians(newDeg.toDouble())
            val smoothedSin = (1.0 - alpha) * kotlin.math.sin(prevRad) + alpha * kotlin.math.sin(newRad)
            val smoothedCos = (1.0 - alpha) * kotlin.math.cos(prevRad) + alpha * kotlin.math.cos(newRad)
            val smoothedRad = kotlin.math.atan2(smoothedSin, smoothedCos)
            return ((Math.toDegrees(smoothedRad).toFloat() + 360f) % 360f)
        }
    }

    fun observeAzimuthDegrees(enableSmoothing: Boolean = true): Flow<Float> = callbackFlow {
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            trySend(0f)
            close()
            return@callbackFlow
        }

        var currentSmoothed = 0f
        var isFirstReading = true

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuthRad = orientation[0]
                    val rawAzimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f

                    val filteredAzimuth = if (!enableSmoothing || isFirstReading) {
                        isFirstReading = false
                        currentSmoothed = rawAzimuthDeg
                        rawAzimuthDeg
                    } else {
                        currentSmoothed = smoothAzimuth(currentSmoothed, rawAzimuthDeg)
                        currentSmoothed
                    }
                    trySend(filteredAzimuth)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
