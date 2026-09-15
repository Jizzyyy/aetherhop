package com.kadhafi.aetherhop.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MgrsUtmConversionTest {

    @Test
    fun testJakartaMonasUtmConversion() {
        // Jakarta Monas coordinates: -6.1754, 106.8272
        val lat = -6.1754
        val lon = 106.8272

        val utm = UtmConverter.toUtm(lat, lon)
        assertEquals(48, utm.zoneNumber)
        assertEquals('M', utm.zoneLetter)

        // Easting around 702,000m (+/- 1000m)
        assertEquals(702167.0, utm.easting, 1000.0)

        // Northing around 9,317,000m (+/- 1000m) with 10M false northing
        assertEquals(9317042.0, utm.northing, 1000.0)

        val formatted = utm.toFormattedString()
        assertTrue(formatted.contains("48M"))
        assertTrue(formatted.contains("E"))
        assertTrue(formatted.contains("N"))
    }

    @Test
    fun testJakartaMonasMgrsConversion() {
        val lat = -6.1754
        val lon = 106.8272

        val mgrs = MgrsConverter.toMgrs(lat, lon)
        assertEquals(48, mgrs.zoneNumber)
        assertEquals('M', mgrs.zoneLetter)
        assertNotNull(mgrs.square100kId)
        assertEquals(2, mgrs.square100kId.length)

        val formatted10 = mgrs.toFormatted10DigitString()
        assertTrue(formatted10.startsWith("48M "))
        // Check 10-digit format structure "[zone][letter] [square] [5-digits] [5-digits]"
        val parts = formatted10.split(" ")
        assertEquals(4, parts.size)
        assertEquals(5, parts[2].length)
        assertEquals(5, parts[3].length)

        val compact = mgrs.toCompactString()
        assertTrue(compact.startsWith("48M"))
        assertEquals(15, compact.length)
    }

    @Test
    fun testEquatorZeroPointUtmConversion() {
        // Prime meridian on equator (0.0, 0.0)
        val lat = 0.0
        val lon = 0.0

        val utm = UtmConverter.toUtm(lat, lon)
        // lon 0.0 falls in zone 31 (central meridian 3°E)
        assertEquals(31, utm.zoneNumber)
        assertEquals('N', utm.zoneLetter)
        assertEquals(0.0, utm.northing, 1.0)
        assertTrue(utm.easting in 160000.0..170000.0)
    }

    @Test
    fun testNorthernHemisphereLondon() {
        // Central London: 51.5074, -0.1278
        val lat = 51.5074
        val lon = -0.1278

        val utm = UtmConverter.toUtm(lat, lon)
        assertEquals(30, utm.zoneNumber)
        assertEquals('U', utm.zoneLetter)
        assertTrue("Northing should be ~5.7M meters", utm.northing in 5600000.0..5800000.0)

        val mgrs = MgrsConverter.toMgrs(lat, lon)
        assertEquals(30, mgrs.zoneNumber)
        assertEquals('U', mgrs.zoneLetter)
        assertTrue(mgrs.toFormatted10DigitString().startsWith("30U "))
    }

    @Test
    fun testCoordinateFormatManagerFormats() {
        val lat = -6.2088
        val lon = 106.8456

        val decimal = CoordinateFormatManager.formatCoordinates(lat, lon, CoordinateFormat.DECIMAL)
        assertEquals("-6.2088, 106.8456", decimal)

        val mgrs = CoordinateFormatManager.formatCoordinates(lat, lon, CoordinateFormat.MGRS)
        assertTrue(mgrs.startsWith("48M "))

        val utm = CoordinateFormatManager.formatCoordinates(lat, lon, CoordinateFormat.UTM)
        assertTrue(utm.contains("48M"))
        assertTrue(utm.contains("E"))
        assertTrue(utm.contains("N"))
    }
}
