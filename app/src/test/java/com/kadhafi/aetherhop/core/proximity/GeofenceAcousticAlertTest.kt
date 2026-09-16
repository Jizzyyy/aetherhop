package com.kadhafi.aetherhop.core.proximity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GeofenceAcousticAlertTest {

    @Test
    fun testAcousticPulseTriggerOnHazardPerimeterCrossing() {
        val hazardZone = GeofenceZone(
            id = "hazard_minefield",
            centerLat = -6.2000,
            centerLon = 106.8000,
            radiusMeters = 100.0,
            type = GeofenceType.HAZARD_PERIMETER,
            label = "Exclusion Zone Alpha"
        )

        val alarmTriggered = AtomicBoolean(false)
        val alarmCount = AtomicInteger(0)
        var lastStatus = GeofenceBreachStatus.SECURE

        fun updateLocationAndCheckAlert(lat: Double, lon: Double) {
            val status = GeofenceBeaconEvaluator.evaluateBreachStatus(lat, lon, hazardZone)
            if (status == GeofenceBreachStatus.HAZARD_ZONE_BREACH && lastStatus != GeofenceBreachStatus.HAZARD_ZONE_BREACH) {
                alarmTriggered.set(true)
                alarmCount.incrementAndGet()
            } else if (status == GeofenceBreachStatus.SECURE) {
                alarmTriggered.set(false)
            }
            lastStatus = status
        }

        // Step 1: Outside hazard zone (250m north) -> No alarm
        updateLocationAndCheckAlert(-6.1977, 106.8000)
        assertFalse(alarmTriggered.get())
        assertEquals(0, alarmCount.get())

        // Step 2: Cross boundary into hazard zone (50m from center) -> Trigger alarm pulse
        updateLocationAndCheckAlert(-6.1995, 106.8000)
        assertTrue(alarmTriggered.get())
        assertEquals(1, alarmCount.get())

        // Step 3: Still inside hazard zone -> Alarm shouldn't re-trigger edge count
        updateLocationAndCheckAlert(-6.2000, 106.8000)
        assertTrue(alarmTriggered.get())
        assertEquals(1, alarmCount.get())

        // Step 4: Step outside perimeter -> Alarm resets
        updateLocationAndCheckAlert(-6.1977, 106.8000)
        assertFalse(alarmTriggered.get())
        assertEquals(1, alarmCount.get())

        // Step 5: Breach again -> Trigger alarm edge count 2
        updateLocationAndCheckAlert(-6.2000, 106.8000)
        assertTrue(alarmTriggered.get())
        assertEquals(2, alarmCount.get())
    }

    @Test
    fun testMultiZonePrecedenceHazardOverSafeZone() {
        val safeBase = GeofenceZone(
            id = "fob_base",
            centerLat = -6.2000,
            centerLon = 106.8000,
            radiusMeters = 500.0,
            type = GeofenceType.SAFE_ZONE,
            label = "FOB Base"
        )

        val embeddedHazard = GeofenceZone(
            id = "fuel_depot_hazard",
            centerLat = -6.2010,
            centerLon = 106.8010,
            radiusMeters = 50.0,
            type = GeofenceType.HAZARD_PERIMETER,
            label = "Contaminated Pit"
        )

        // Inside base safe zone but ALSO inside embedded hazard
        val statusSafe = GeofenceBeaconEvaluator.evaluateBreachStatus(-6.2010, 106.8010, safeBase)
        val statusHazard = GeofenceBeaconEvaluator.evaluateBreachStatus(-6.2010, 106.8010, embeddedHazard)

        assertEquals(GeofenceBreachStatus.SECURE, statusSafe)
        assertEquals(GeofenceBreachStatus.HAZARD_ZONE_BREACH, statusHazard)

        // Resolve priority: Hazard breach takes precedence over safe status
        val effectiveStatus = if (statusHazard == GeofenceBreachStatus.HAZARD_ZONE_BREACH) {
            statusHazard
        } else {
            statusSafe
        }
        assertEquals(GeofenceBreachStatus.HAZARD_ZONE_BREACH, effectiveStatus)
    }
}
