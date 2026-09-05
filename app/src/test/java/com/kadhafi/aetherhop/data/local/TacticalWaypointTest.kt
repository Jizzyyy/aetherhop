package com.kadhafi.aetherhop.data.local

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TacticalWaypointTest {

    @Test
    fun testTacticalWaypointEntityCreation() {
        val waypoint = TacticalWaypointEntity(
            id = "wp_001",
            label = "Posko Utama SAR",
            latitude = -6.2088,
            longitude = 106.8456,
            type = "MEDICAL"
        )

        assertNotNull(waypoint)
        assertEquals("wp_001", waypoint.id)
        assertEquals("Posko Utama SAR", waypoint.label)
        assertEquals("MEDICAL", waypoint.type)
        assertEquals(-6.2088, waypoint.latitude, 0.0001)
    }
}
