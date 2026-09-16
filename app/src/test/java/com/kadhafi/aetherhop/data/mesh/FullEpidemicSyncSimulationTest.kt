package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullEpidemicSyncSimulationTest {

    private class SimulatedMeshNode(val nodeId: String) {
        val gossipManager = EpidemicGossipManager(nodeId)
        val waypoints = mutableMapOf<String, TacticalWaypointEntity>()

        fun addLocalWaypoint(id: String, label: String, lat: Double, lon: Double, type: String = "CAMP") {
            waypoints[id] = TacticalWaypointEntity(id, label, lat, lon, type)
        }

        fun exchangeGossip(remoteNode: SimulatedMeshNode) {
            // Node A creates digest and sends to Node B
            val digestA = gossipManager.createLocalDigest(waypoints.keys.toList())
            val deltaAtB = remoteNode.gossipManager.computeDelta(remoteNode.waypoints.keys, digestA)

            // Node B sends missing waypoints to Node A
            val toSendToA = remoteNode.gossipManager.filterMissingWaypoints(remoteNode.waypoints.values.toList(), deltaAtB.missingWaypointIdsForRemote)
            toSendToA.forEach { waypoints[it.id] = it }

            // Node B also creates its digest and sends to Node A
            val digestB = remoteNode.gossipManager.createLocalDigest(remoteNode.waypoints.keys.toList())
            val deltaAtA = gossipManager.computeDelta(waypoints.keys, digestB)

            // Node A sends missing waypoints to Node B
            val toSendToB = gossipManager.filterMissingWaypoints(waypoints.values.toList(), deltaAtA.missingWaypointIdsForRemote)
            toSendToB.forEach { remoteNode.waypoints[it.id] = it }
        }
    }

    @Test
    fun testThreePartitionStoreCarryForwardDiffusion() {
        // Partition 1: Node 1 and Node 2 (Mule)
        val node1 = SimulatedMeshNode("node_1_command")
        val node2 = SimulatedMeshNode("node_2_patrol_mule")

        // Partition 2: Node 3 and Node 4
        val node3 = SimulatedMeshNode("node_3_relay_mule")
        val node4 = SimulatedMeshNode("node_4_outpost")

        // Partition 3: Node 5 (Isolated Extraction Team)
        val node5 = SimulatedMeshNode("node_5_extraction")

        // Initial State:
        // Node 1 originates Waypoint Alpha (FOB Command)
        node1.addLocalWaypoint("wp_alpha", "Command Base", -6.2088, 106.8456, "CAMP")

        // Node 4 originates Waypoint Bravo (Hazard Area)
        node4.addLocalWaypoint("wp_bravo", "Toxic Spill", -6.2200, 106.8500, "HAZARD")

        // Node 5 originates Waypoint Charlie (Extraction LZ)
        node5.addLocalWaypoint("wp_charlie", "LZ Eagle", -6.2300, 106.8600, "RENDEZVOUS")

        // Phase 1: Partition 1 syncs (Node 1 <-> Node 2)
        node1.exchangeGossip(node2)
        assertTrue(node2.waypoints.containsKey("wp_alpha"))
        assertFalse(node2.waypoints.containsKey("wp_bravo"))
        assertFalse(node2.waypoints.containsKey("wp_charlie"))

        // Phase 2: Node 4 syncs with Node 3 in Partition 2
        node4.exchangeGossip(node3)
        assertTrue(node3.waypoints.containsKey("wp_bravo"))

        // Phase 3: Node 2 travels physically (store-carry) and meets Node 3 (contacts Partition 2)
        node2.exchangeGossip(node3)
        // Now both mules have Alpha and Bravo
        assertTrue(node2.waypoints.containsKey("wp_alpha"))
        assertTrue(node2.waypoints.containsKey("wp_bravo"))
        assertTrue(node3.waypoints.containsKey("wp_alpha"))
        assertTrue(node3.waypoints.containsKey("wp_bravo"))

        // Phase 4: Node 3 travels to Partition 3 and encounters isolated Node 5
        node3.exchangeGossip(node5)

        // Verify Node 5 (never in direct contact with Node 1 or Node 4) now has all 3 waypoints!
        assertEquals(3, node5.waypoints.size)
        assertTrue(node5.waypoints.containsKey("wp_alpha"))
        assertTrue(node5.waypoints.containsKey("wp_bravo"))
        assertTrue(node5.waypoints.containsKey("wp_charlie"))

        // Node 3 also learned Charlie from Node 5
        assertEquals(3, node3.waypoints.size)
        assertTrue(node3.waypoints.containsKey("wp_charlie"))

        // Phase 5: Node 2 travels back to Node 1 and Node 3 travels back to Node 4
        node3.exchangeGossip(node2)
        node2.exchangeGossip(node1)
        node3.exchangeGossip(node4)

        // Complete epidemic convergence: All 5 nodes have all 3 waypoints
        assertEquals(3, node1.waypoints.size)
        assertEquals(3, node2.waypoints.size)
        assertEquals(3, node3.waypoints.size)
        assertEquals(3, node4.waypoints.size)
        assertEquals(3, node5.waypoints.size)
    }

    @Test
    fun testNoRedundantTransferWhenConverged() {
        val nodeA = SimulatedMeshNode("node_a")
        val nodeB = SimulatedMeshNode("node_b")

        nodeA.addLocalWaypoint("wp_1", "Point 1", -6.1, 106.1)
        nodeB.addLocalWaypoint("wp_1", "Point 1", -6.1, 106.1)

        val digestA = nodeA.gossipManager.createLocalDigest(nodeA.waypoints.keys.toList())
        val delta = nodeB.gossipManager.computeDelta(nodeB.waypoints.keys, digestA)

        assertTrue(delta.missingWaypointIdsForRemote.isEmpty())
        assertTrue(delta.neededWaypointIdsFromRemote.isEmpty())
    }
}
