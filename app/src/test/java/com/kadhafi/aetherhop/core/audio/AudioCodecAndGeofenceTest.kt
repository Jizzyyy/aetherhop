package com.kadhafi.aetherhop.core.audio

import com.kadhafi.aetherhop.core.proximity.GeofenceBeaconEvaluator
import com.kadhafi.aetherhop.core.proximity.GeofenceZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCodecAndGeofenceTest {

    @Test
    fun testAdpcmCompressionRatioIsQuarterSize() {
        val pcm = ByteArray(640) { 0.toByte() }
        val adpcm = AdpcmCodec.encodePcmToAdpcm(pcm)
        assertEquals(160, adpcm.size)

        val restoredPcm = AdpcmCodec.decodeAdpcmToPcm(adpcm)
        assertEquals(640, restoredPcm.size)
    }

    @Test
    fun testGeofenceInsideCalculation() {
        val centerLat = -6.2088
        val centerLon = 106.8456
        val zone = GeofenceZone("zone_1", centerLat, centerLon, radiusMeters = 500.0)

        val isInside = GeofenceBeaconEvaluator.isInsideGeofence(centerLat, centerLon, zone)
        assertTrue(isInside)
    }
}
