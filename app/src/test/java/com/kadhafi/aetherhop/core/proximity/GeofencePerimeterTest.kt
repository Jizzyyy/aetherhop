package com.kadhafi.aetherhop.core.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePerimeterTest {

    @Test
    fun testSafeZoneEntryAndExit() {
        val baseCenterLat = -6.2088
        val baseCenterLon = 106.8456
        val baseRadius = 200.0 // 200m safe base perimeter

        val safeZone = GeofenceZone(
            id = "base_safe_01",
            centerLat = baseCenterLat,
            centerLon = baseCenterLon,
            radiusMeters = baseRadius,
            type = GeofenceType.SAFE_ZONE,
            label = "Forward Operating Base"
        )

        // Inside safe base -> SECURE
        val statusInside = GeofenceBeaconEvaluator.evaluateBreachStatus(baseCenterLat, baseCenterLon, safeZone)
        assertEquals(GeofenceBreachStatus.SECURE, statusInside)

        // Close to center (50m away) -> SECURE
        val statusNear = GeofenceBeaconEvaluator.evaluateBreachStatus(baseCenterLat + 0.0004, baseCenterLon, safeZone)
        assertEquals(GeofenceBreachStatus.SECURE, statusNear)

        // Wandered 500m away outside base -> SAFE_ZONE_EXIT
        val statusOutside = GeofenceBeaconEvaluator.evaluateBreachStatus(baseCenterLat + 0.005, baseCenterLon, safeZone)
        assertEquals(GeofenceBreachStatus.SAFE_ZONE_EXIT, statusOutside)
    }

    @Test
    fun testHazardZoneBreachTrigger() {
        val minefieldLat = -6.3000
        val minefieldLon = 106.9000
        val hazardRadius = 150.0 // 150m exclusion radius

        val hazardZone = GeofenceZone(
            id = "hazard_exclusion_01",
            centerLat = minefieldLat,
            centerLon = minefieldLon,
            radiusMeters = hazardRadius,
            type = GeofenceType.HAZARD_PERIMETER,
            label = "Minefield Sector 7"
        )

        // Outside hazard area (1 km away) -> SECURE
        val statusFar = GeofenceBeaconEvaluator.evaluateBreachStatus(-6.3100, 106.9000, hazardZone)
        assertEquals(GeofenceBreachStatus.SECURE, statusFar)

        // Inside hazard exclusion perimeter -> HAZARD_ZONE_BREACH
        val statusInside = GeofenceBeaconEvaluator.evaluateBreachStatus(minefieldLat, minefieldLon, hazardZone)
        assertEquals(GeofenceBreachStatus.HAZARD_ZONE_BREACH, statusInside)
        assertTrue(GeofenceBeaconEvaluator.isInsideGeofence(minefieldLat, minefieldLon, hazardZone))

        // Right on the edge border check
        val borderLat = minefieldLat + (140.0 / 111320.0) // approx 140m north
        val statusBorderInside = GeofenceBeaconEvaluator.evaluateBreachStatus(borderLat, minefieldLon, hazardZone)
        assertEquals(GeofenceBreachStatus.HAZARD_ZONE_BREACH, statusBorderInside)
    }

    @Test
    fun testGeofenceTypeDefaults() {
        val defaultZone = GeofenceZone("zone_default", -6.0, 106.0, 100.0)
        assertEquals(GeofenceType.HAZARD_PERIMETER, defaultZone.type)
        assertEquals("", defaultZone.label)
    }
}
