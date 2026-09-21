package com.kadhafi.aetherhop.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

data class DeadReckoningState(
    val isDeadReckoningActive: Boolean = false,
    val currentLatitude: Double = -6.2088,
    val currentLongitude: Double = 106.8456,
    val accumulatedSteps: Int = 0,
    val accumulatedDistanceMeters: Double = 0.0,
    val estimatedDriftRadiusMeters: Float = 5.0f,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

class DeadReckoningEngine(
    private var strideLengthMeters: Double = InertialStepDetector.DEFAULT_STRIDE_LENGTH_METERS
) {
    private val lock = Any()
    private val _state = MutableStateFlow(DeadReckoningState())
    val state: StateFlow<DeadReckoningState> = _state.asStateFlow()

    fun setStrideLength(lengthMeters: Double) = synchronized(lock) {
        if (lengthMeters in 0.3..2.0) {
            strideLengthMeters = lengthMeters
        }
    }

    fun getStrideLength(): Double = strideLengthMeters

    fun recalibrateWithGps(lat: Double, lon: Double, accuracyMeters: Float = 5.0f) = synchronized(lock) {
        _state.value = DeadReckoningState(
            isDeadReckoningActive = false,
            currentLatitude = lat,
            currentLongitude = lon,
            accumulatedSteps = 0,
            accumulatedDistanceMeters = 0.0,
            estimatedDriftRadiusMeters = accuracyMeters.coerceAtLeast(3.0f),
            lastUpdateTimestamp = System.currentTimeMillis()
        )
    }

    fun setDeadReckoningActive(active: Boolean) = synchronized(lock) {
        if (_state.value.isDeadReckoningActive != active) {
            _state.value = _state.value.copy(
                isDeadReckoningActive = active,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun onStepTaken(headingDegrees: Float): DeadReckoningState = synchronized(lock) {
        val current = _state.value
        val headingRad = Math.toRadians(headingDegrees.toDouble())

        val dLat = (strideLengthMeters * cos(headingRad)) / 111320.0
        val cosLat = cos(Math.toRadians(current.currentLatitude)).coerceAtLeast(0.01)
        val dLon = (strideLengthMeters * sin(headingRad)) / (111320.0 * cosLat)

        val newLat = current.currentLatitude + dLat
        val newLon = current.currentLongitude + dLon
        val newSteps = current.accumulatedSteps + 1
        val newDistance = current.accumulatedDistanceMeters + strideLengthMeters

        // Inertial drift models: 1.5% drift of distance traveled plus base sensor uncertainty
        val newDrift = (current.estimatedDriftRadiusMeters + (strideLengthMeters * 0.015).toFloat())
            .coerceAtMost(250.0f)

        val newState = current.copy(
            isDeadReckoningActive = true,
            currentLatitude = newLat,
            currentLongitude = newLon,
            accumulatedSteps = newSteps,
            accumulatedDistanceMeters = newDistance,
            estimatedDriftRadiusMeters = newDrift,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
        _state.value = newState
        newState
    }
}
