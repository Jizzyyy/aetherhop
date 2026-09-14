package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class TacticalNavigationCalculatorTest {

    @Test
    fun testRelativeBearingDeflectionAgainstAzimuth() {
        // Target is directly East (90 degrees forward bearing)
        val targetBearing = 90.0

        // User compass is facing North (0 degrees azimuth) -> relative bearing is 90 degrees
        val relBearingNorthFacing = (targetBearing - 0.0 + 360.0) % 360.0
        assertEquals(90.0, relBearingNorthFacing, 0.001)

        // User turns 45 degrees North-East -> target should appear at 45 degrees relative
        val relBearingNeFacing = (targetBearing - 45.0 + 360.0) % 360.0
        assertEquals(45.0, relBearingNeFacing, 0.001)

        // User turns directly East (90 degrees) -> target should be directly straight ahead (0 degrees)
        val relBearingEastFacing = (targetBearing - 90.0 + 360.0) % 360.0
        assertEquals(0.0, relBearingEastFacing, 0.001)

        // User turns South (180 degrees) -> target is behind user (270 degrees relative)
        val relBearingSouthFacing = (targetBearing - 180.0 + 360.0) % 360.0
        assertEquals(270.0, relBearingSouthFacing, 0.001)
    }

    @Test
    fun testCardinalDirectionBearings() {
        val originLat = 0.0
        val originLon = 0.0

        // Due North
        val northBearing = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, 1.0, 0.0)
        assertEquals(0.0, northBearing, 0.5)

        // Due East
        val eastBearing = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, 0.0, 1.0)
        assertEquals(90.0, eastBearing, 0.5)

        // Due South
        val southBearing = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, -1.0, 0.0)
        assertEquals(180.0, southBearing, 0.5)

        // Due West
        val westBearing = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, 0.0, -1.0)
        assertEquals(270.0, westBearing, 0.5)
    }

    @Test
    fun testTacticalRangeRingScaleMapping() {
        fun mapDistanceToNormalizedRing(distanceMeters: Double, maxScaleMeters: Double = 200.0): Float {
            return (distanceMeters / maxScaleMeters).toFloat().coerceIn(0.05f, 1.0f)
        }

        // 25m on 200m scale -> 0.125
        assertEquals(0.125f, mapDistanceToNormalizedRing(25.0), 0.001f)

        // 50m on 200m scale -> 0.25
        assertEquals(0.25f, mapDistanceToNormalizedRing(50.0), 0.001f)

        // 100m on 200m scale -> 0.5
        assertEquals(0.5f, mapDistanceToNormalizedRing(100.0), 0.001f)

        // 200m on 200m scale -> 1.0
        assertEquals(1.0f, mapDistanceToNormalizedRing(200.0), 0.001f)

        // Beyond scale clamped to 1.0
        assertEquals(1.0f, mapDistanceToNormalizedRing(500.0), 0.001f)
    }

    @Test
    fun testSlantRangeWithAltitudeDelta() {
        val horizontalDist = 400.0 // 400 meters horizontal
        val altitudeDelta = 300.0  // 300 meters climb
        // 3-4-5 right triangle -> 500m slant range
        val slantRange = sqrt(horizontalDist * horizontalDist + altitudeDelta * altitudeDelta)
        assertEquals(500.0, slantRange, 0.001)
    }

    @Test
    fun testDistanceFormattingTacticalLabels() {
        assertEquals("45 m", GeodesicCalculator.formatDistance(45.2))
        assertEquals("999 m", GeodesicCalculator.formatDistance(999.0))
        assertEquals("1.5 km", GeodesicCalculator.formatDistance(1500.0))
        assertEquals("12.0 km", GeodesicCalculator.formatDistance(12040.0))
    }
}
