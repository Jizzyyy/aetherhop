package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

class AltitudeGeodesicTest {

    @Test
    fun testTacticalLocationSnapshotFields() {
        val snapshot = TacticalLocationSnapshot(
            latitude = -6.2088,
            longitude = 106.8456,
            altitudeMeters = 150.0,
            accuracyMeters = 3.5f,
            verticalAccuracyMeters = 1.8f,
            speedMps = 1.4f
        )

        assertEquals(-6.2088, snapshot.latitude, 0.0001)
        assertEquals(106.8456, snapshot.longitude, 0.0001)
        assertEquals(150.0, snapshot.altitudeMeters ?: 0.0, 0.001)
        assertEquals(3.5f, snapshot.accuracyMeters, 0.01f)
        assertEquals(1.8f, snapshot.verticalAccuracyMeters ?: 0f, 0.01f)
        assertEquals(1.4f, snapshot.speedMps, 0.01f)
    }

    @Test
    fun testElevationDeltaAndClimbAngle() {
        val observerAlt = 50.0
        val targetAlt = 350.0
        val horizontalDist = 400.0

        val deltaAlt = targetAlt - observerAlt
        assertEquals(300.0, deltaAlt, 0.001)

        // 3-4-5 triangle slant range = 500m
        val slantRange = sqrt(horizontalDist * horizontalDist + deltaAlt * deltaAlt)
        assertEquals(500.0, slantRange, 0.001)

        // Elevation inclination angle
        val climbAngleRad = atan2(deltaAlt, horizontalDist)
        val climbAngleDeg = Math.toDegrees(climbAngleRad)
        assertEquals(36.87, climbAngleDeg, 0.1)
    }

    @Test
    fun testNegativeElevationDeltaDescent() {
        val observerAlt = 400.0
        val targetAlt = 100.0
        val deltaAlt = targetAlt - observerAlt
        assertEquals(-300.0, deltaAlt, 0.001)

        val horizontalDist = 400.0
        val slantRange = sqrt(horizontalDist * horizontalDist + deltaAlt * deltaAlt)
        assertEquals(500.0, slantRange, 0.001)
    }

    @Test
    fun testSpeedConversionKmh() {
        val speedMps = 10.0f // 10 m/s
        val speedKmh = speedMps * 3.6f
        assertEquals(36.0f, speedKmh, 0.01f)
    }
}
