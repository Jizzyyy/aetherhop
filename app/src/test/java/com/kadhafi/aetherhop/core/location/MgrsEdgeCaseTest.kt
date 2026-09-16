package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MgrsEdgeCaseTest {

    @Test
    fun testEquatorialCrossingFalseNorthingTransition() {
        // Just north of equator
        val northLat = 0.0001
        val lon = 106.8
        val utmNorth = UtmConverter.toUtm(northLat, lon)
        assertEquals('N', utmNorth.zoneLetter)
        assertTrue("Northing just north of equator should be small positive", utmNorth.northing in 0.0..100.0)

        // Just south of equator
        val southLat = -0.0001
        val utmSouth = UtmConverter.toUtm(southLat, lon)
        assertEquals('M', utmSouth.zoneLetter)
        assertTrue("Northing just south of equator should have 10,000,000 false northing", utmSouth.northing in 9999900.0..10000000.0)

        // MGRS generation for both should succeed and produce valid formats
        val mgrsNorth = MgrsConverter.toMgrs(northLat, lon)
        val mgrsSouth = MgrsConverter.toMgrs(southLat, lon)
        assertEquals('N', mgrsNorth.zoneLetter)
        assertEquals('M', mgrsSouth.zoneLetter)
        assertEquals(15, mgrsNorth.toCompactString().length)
        assertEquals(15, mgrsSouth.toCompactString().length)
    }

    @Test
    fun testAntimeridianCrossingZones() {
        // Longitude 179.9°E -> Zone 60
        val utmZone60 = UtmConverter.toUtm(10.0, 179.9)
        assertEquals(60, utmZone60.zoneNumber)

        // Longitude 179.9°W (-179.9) -> Zone 1
        val utmZone1 = UtmConverter.toUtm(10.0, -179.9)
        assertEquals(1, utmZone1.zoneNumber)

        val mgrs60 = MgrsConverter.toMgrs(10.0, 179.9)
        val mgrs1 = MgrsConverter.toMgrs(10.0, -179.9)
        assertEquals(60, mgrs60.zoneNumber)
        assertEquals(1, mgrs1.zoneNumber)
    }

    @Test
    fun testSouthernHemisphereSydneyAustralia() {
        // Sydney, Australia: -33.8688, 151.2093
        val lat = -33.8688
        val lon = 151.2093

        val utm = UtmConverter.toUtm(lat, lon)
        assertEquals(56, utm.zoneNumber)
        assertEquals('H', utm.zoneLetter) // -33.8 is in letter H (-40 to -32)
        assertTrue(utm.northing in 6200000.0..6300000.0)

        val mgrs = MgrsConverter.toMgrs(lat, lon)
        assertEquals(56, mgrs.zoneNumber)
        assertEquals('H', mgrs.zoneLetter)
        assertNotNull(mgrs.square100kId)
        assertTrue(mgrs.toFormatted10DigitString().startsWith("56H "))
    }

    @Test
    fun testPolarBoundsDesignator() {
        // Latitude > 84 or < -80 returns 'Z' (UPS zone)
        assertEquals('Z', UtmConverter.getUtmLetterDesignator(85.0))
        assertEquals('Z', UtmConverter.getUtmLetterDesignator(-82.0))
        assertEquals('X', UtmConverter.getUtmLetterDesignator(80.0))
        assertEquals('C', UtmConverter.getUtmLetterDesignator(-75.0))
    }

    @Test
    fun testTenDigitPaddingConsistency() {
        // Test coordinate where offsets could be small (e.g. 7 meters)
        val mgrs = MgrsCoordinate(
            zoneNumber = 48,
            zoneLetter = 'M',
            square100kId = "ZC",
            eastingMeter = 7,
            northingMeter = 42
        )
        val formatted = mgrs.toFormatted10DigitString()
        assertEquals("48M ZC 00007 00042", formatted)
        assertEquals("48MZC0000700042", mgrs.toCompactString())
    }
}
