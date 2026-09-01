package com.kadhafi.aetherhop.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingTableTest {

    @Test
    fun testUpdateAndGetNextHopIp() {
        val routingTable = RoutingTable()
        routingTable.updateRoute("node_target_1", "192.168.49.2", hops = 2)

        val nextHop = routingTable.getNextHopIp("node_target_1")
        assertEquals("192.168.49.2", nextHop)
    }

    @Test
    fun testUnregisteredRouteReturnsNull() {
        val routingTable = RoutingTable()
        val nextHop = routingTable.getNextHopIp("non_existent_node")
        assertNull(nextHop)
    }
}
