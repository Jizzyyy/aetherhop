package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.domain.model.GossipDigestPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpidemicGossipManagerTest {

    @Test
    fun testLocalDigestGeneration() {
        val manager = EpidemicGossipManager("node_test_1")
        val waypoints = listOf("wp_c", "wp_a", "wp_b")
        val digest = manager.createLocalDigest(waypoints)

        assertEquals("node_test_1", digest.nodeId)
        // Waypoint IDs should be sorted for canonical representation
        assertEquals(listOf("wp_a", "wp_b", "wp_c"), digest.knownWaypointIds)
        assertTrue(digest.timestamp > 0L)
    }

    @Test
    fun testDeltaComputationMutualDifferences() {
        val manager = EpidemicGossipManager("node_local")
        val localWaypointIds = setOf("wp_1", "wp_2", "wp_3")

        val remoteDigest = GossipDigestPayload(
            nodeId = "node_remote",
            knownWaypointIds = listOf("wp_2", "wp_3", "wp_4", "wp_5"),
            timestamp = 1000L
        )

        val delta = manager.computeDelta(localWaypointIds, remoteDigest)

        // Local has wp_1 which remote lacks
        assertEquals(listOf("wp_1"), delta.missingWaypointIdsForRemote)

        // Remote has wp_4 and wp_5 which local lacks
        assertEquals(listOf("wp_4", "wp_5"), delta.neededWaypointIdsFromRemote)
    }

    @Test
    fun testDeltaComputationIdenticalSets() {
        val manager = EpidemicGossipManager("node_local")
        val localIds = setOf("wp_alpha", "wp_beta")
        val remoteDigest = GossipDigestPayload(
            nodeId = "node_remote",
            knownWaypointIds = listOf("wp_alpha", "wp_beta")
        )

        val delta = manager.computeDelta(localIds, remoteDigest)
        assertTrue(delta.missingWaypointIdsForRemote.isEmpty())
        assertTrue(delta.neededWaypointIdsFromRemote.isEmpty())
    }

    @Test
    fun testFilterMissingWaypoints() {
        val manager = EpidemicGossipManager("node_local")
        val allLocal = listOf(
            TacticalWaypointEntity("wp_1", "Camp 1", -6.1, 106.1),
            TacticalWaypointEntity("wp_2", "Hazard 2", -6.2, 106.2),
            TacticalWaypointEntity("wp_3", "Rendezvous 3", -6.3, 106.3)
        )

        val missingIds = listOf("wp_1", "wp_3")
        val filtered = manager.filterMissingWaypoints(allLocal, missingIds)

        assertEquals(2, filtered.size)
        assertEquals("wp_1", filtered[0].id)
        assertEquals("wp_3", filtered[1].id)
    }
}
