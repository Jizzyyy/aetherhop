package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeodesicCalculatorTest {

    @Test
    fun testHaversineDistanceCalculation() {
        // Monas Jakarta (-6.1754, 106.8272) to Bundaran HI Jakarta (-6.1950, 106.8230) ~ 2.2 km
        val distMeters = GeodesicCalculator.calculateDistanceMeters(
            -6.1754, 106.8272,
            -6.1950, 106.8230
        )
        assertTrue("Distance should be approximately 2.2km", distMeters in 2100.0..2300.0)
    }

    @Test
    fun testForwardBearingCalculationNorthToSouth() {
        // Direct North to South bearing should be ~ 180 degrees
        val bearing = GeodesicCalculator.calculateForwardBearingDegrees(
            -6.0, 106.0,
            -7.0, 106.0
        )
        assertEquals(180.0, bearing, 1.0)
    }

    @Test
    fun testFormatDistanceMetersAndKilometers() {
        assertEquals("450 m", GeodesicCalculator.formatDistance(450.0))
        assertEquals("2.5 km", GeodesicCalculator.formatDistance(2500.0))
    }
}
