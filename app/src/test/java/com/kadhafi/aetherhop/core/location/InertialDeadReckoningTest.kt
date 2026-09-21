package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InertialDeadReckoningTest {

    @Test
    fun testStepDetectorPeakZeroCrossingCycle() {
        val detector = InertialStepDetector()

        // 1. Static gravity baseline (~9.81 m/s^2) -> no step
        assertFalse(detector.processSample(9.81f, 1000L))

        // 2. High acceleration peak (> 11.2 m/s^2)
        assertFalse(detector.processSample(12.5f, 1050L))

        // 3. Valley dip (< 8.8 m/s^2) after 300ms -> Step registered!
        assertTrue(detector.processSample(7.5f, 1360L))

        // 4. Another peak immediately within 100ms (< 260ms refractory period)
        detector.processSample(12.8f, 1400L)
        assertFalse("Rapid refractory bounce must be suppressed", detector.processSample(7.2f, 1450L))

        // 5. Valid step after 270ms interval
        detector.processSample(12.0f, 1640L)
        assertTrue("Step after refractory period should succeed", detector.processSample(8.0f, 1680L))
    }

    @Test
    fun testDeadReckoningEngineVectorProjectionNorth() {
        val engine = DeadReckoningEngine(strideLengthMeters = 1.0)
        val initialLat = -6.2088
        val initialLon = 106.8456
        engine.recalibrateWithGps(initialLat, initialLon, 5.0f)

        // Take 10 steps due North (0°)
        for (i in 1..10) {
            engine.onStepTaken(0f)
        }

        val state = engine.state.value
        assertTrue(state.isDeadReckoningActive)
        assertEquals(10, state.accumulatedSteps)
        assertEquals(10.0, state.accumulatedDistanceMeters, 0.001)
        assertTrue("Latitude must increase moving north", state.currentLatitude > initialLat)
        assertEquals(initialLon, state.currentLongitude, 0.000001)
        // Drift should grow
        assertTrue(state.estimatedDriftRadiusMeters > 5.0f)
    }

    @Test
    fun testDeadReckoningEngineVectorProjectionEast() {
        val engine = DeadReckoningEngine(strideLengthMeters = 0.8)
        val initialLat = 0.0 // Equator
        val initialLon = 100.0
        engine.recalibrateWithGps(initialLat, initialLon, 4.0f)

        // Take 5 steps due East (90°)
        for (i in 1..5) {
            engine.onStepTaken(90f)
        }

        val state = engine.state.value
        assertEquals(5, state.accumulatedSteps)
        assertEquals(4.0, state.accumulatedDistanceMeters, 0.001)
        assertEquals(initialLat, state.currentLatitude, 0.000001)
        assertTrue("Longitude must increase moving east", state.currentLongitude > initialLon)
    }

    @Test
    fun testRecalibrateWithGpsResetsDriftAndPosition() {
        val engine = DeadReckoningEngine(strideLengthMeters = 0.75)
        engine.recalibrateWithGps(-6.0, 106.0, 3.0f)

        // Move 20 steps
        for (i in 1..20) {
            engine.onStepTaken(45f)
        }
        assertTrue(engine.state.value.isDeadReckoningActive)
        assertEquals(20, engine.state.value.accumulatedSteps)

        // GPS Lock acquired at exact coordinates
        val freshGpsLat = -5.9980
        val freshGpsLon = 106.0020
        engine.recalibrateWithGps(freshGpsLat, freshGpsLon, 2.5f)

        val recoveredState = engine.state.value
        assertFalse(recoveredState.isDeadReckoningActive)
        assertEquals(0, recoveredState.accumulatedSteps)
        assertEquals(0.0, recoveredState.accumulatedDistanceMeters, 0.001)
        assertEquals(freshGpsLat, recoveredState.currentLatitude, 0.000001)
        assertEquals(freshGpsLon, recoveredState.currentLongitude, 0.000001)
        assertEquals(3.0f, recoveredState.estimatedDriftRadiusMeters, 0.001f)
    }

    @Test
    fun testCompassHeadingFilterAcrossZeroBoundary() {
        // Smoothing between 355° and 5° across the 0° north wrap
        val smoothed = CompassSensorManager.smoothAzimuth(previousDeg = 355f, newDeg = 5f, alpha = 0.5f)
        // Average of 355° (-5°) and +5° is 0° (North)
        assertTrue("Smoothed angle should be near 0° or 360°, was $smoothed", smoothed in 359f..360f || smoothed in 0f..1f)
    }
}
