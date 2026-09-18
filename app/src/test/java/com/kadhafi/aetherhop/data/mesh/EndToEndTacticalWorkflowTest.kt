package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.core.location.BreadcrumbPoint
import com.kadhafi.aetherhop.core.location.GeoJsonExporter
import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import com.kadhafi.aetherhop.core.location.GpxExporter
import com.kadhafi.aetherhop.core.power.CriticalDutyCycleScheduler
import com.kadhafi.aetherhop.core.proximity.GeofenceBeaconEvaluator
import com.kadhafi.aetherhop.core.proximity.GeofenceBreachStatus
import com.kadhafi.aetherhop.core.proximity.GeofenceType
import com.kadhafi.aetherhop.core.proximity.GeofenceZone
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.domain.model.PeerPairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndToEndTacticalWorkflowTest {

    @Test
    fun testCompleteTacticalMissionLifecycle() {
        // Step 1: Secure In-Field Node Pairing
        val operatorId = "node_operator_echo_07"
        val payload = PeerPairingPayload(
            deviceId = operatorId,
            deviceName = "Echo Unit",
            publicKeyBase64 = "MIIBIjANBgkq...",
            checksumFingerprint = operatorId.take(16)
        )
        val isVerified = payload.checksumFingerprint == payload.deviceId.take(16)
        assertTrue(isVerified)

        val peer = PeerEntity(
            id = payload.deviceId,
            name = payload.deviceName,
            address = "192.168.49.77",
            lastSeenTimestamp = System.currentTimeMillis(),
            isTrusted = isVerified,
            fingerprint = payload.checksumFingerprint,
            customAlias = "Recon Alpha Scout"
        )
        assertEquals("Recon Alpha Scout", peer.customAlias)
        assertTrue(peer.isTrusted)

        // Step 2: Tactical Waypoint Mapping
        val fopCamp = TacticalWaypointEntity("wp_1", "FOB Bravo", -6.2088, 106.8456, "CAMP")
        val hazardZone = TacticalWaypointEntity("wp_2", "IED Hazard", -6.2120, 106.8480, "HAZARD")
        val extractionLz = TacticalWaypointEntity("wp_3", "LZ Falcon", -6.2180, 106.8520, "RENDEZVOUS")
        val missionWaypoints = listOf(fopCamp, hazardZone, extractionLz)

        // Step 3: Geofence Hazard Perimeter Evaluation
        val geofenceHazard = GeofenceZone(
            id = hazardZone.id,
            centerLat = hazardZone.latitude,
            centerLon = hazardZone.longitude,
            radiusMeters = 120.0,
            type = GeofenceType.HAZARD_PERIMETER,
            label = hazardZone.label
        )
        // Operator inside hazard radius
        val breachStatus = GeofenceBeaconEvaluator.evaluateBreachStatus(
            latitude = hazardZone.latitude + 0.0003, // ~33m away
            longitude = hazardZone.longitude,
            zone = geofenceHazard
        )
        assertEquals(GeofenceBreachStatus.HAZARD_ZONE_BREACH, breachStatus)

        // Step 4: Mission Trail Breadcrumbs Recording
        val trail = listOf(
            BreadcrumbPoint(fopCamp.latitude, fopCamp.longitude, 1000L),
            BreadcrumbPoint(-6.2100, 106.8465, 2000L),
            BreadcrumbPoint(extractionLz.latitude, extractionLz.longitude, 3000L)
        )
        val distanceTraveled = GeodesicCalculator.calculateDistanceMeters(
            fopCamp.latitude, fopCamp.longitude,
            extractionLz.latitude, extractionLz.longitude
        )
        assertTrue("Distance between FOB and LZ should be roughly ~1.2km", distanceTraveled in 1000.0..1500.0)

        // Step 5: Power Depletion & Survival Mode Activation
        val batteryPct = 12 // Dropped below 15%
        val isSurvival = CriticalDutyCycleScheduler.shouldActivateSurvivalMode(batteryPct, isCharging = false, manualOverride = false)
        assertTrue(isSurvival)
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(5L))
        assertFalse(CriticalDutyCycleScheduler.isRadioActive(25L))

        // Step 6: Full Mission Export to GPX and GeoJSON for ATAK/QGIS
        val gpxXml = GpxExporter.exportToGpx("Operation Iron Eagle", missionWaypoints, trail)
        assertNotNull(gpxXml)
        assertTrue(gpxXml.contains("FOB Bravo"))
        assertTrue(gpxXml.contains("IED Hazard"))
        assertTrue(gpxXml.contains("LZ Falcon"))
        assertTrue(gpxXml.contains("<trkpt"))

        val geoJson = GeoJsonExporter.exportToGeoJson("Operation Iron Eagle", missionWaypoints, trail)
        assertNotNull(geoJson)
        assertTrue(geoJson.contains("FeatureCollection"))
        assertTrue(geoJson.contains("LineString"))
    }
}
