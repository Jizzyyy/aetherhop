package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.RouteReplyPayload
import com.kadhafi.aetherhop.domain.model.RouteRequestPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicRouteLearningTest {

    @Test
    fun testDynamicRouteLearningFromPacketTraffic() {
        val routingTable = RoutingTable()
        val senderId = "peer_charlie"
        val senderIp = "192.168.49.5"
        val packetTtl = 4 // started at 5, decremented to 4 (1 hop)
        val hops = maxOf(1, 5 - packetTtl + 1)

        routingTable.updateRoute(senderId, senderIp, hops)

        val nextHop = routingTable.getNextHopIp(senderId)
        assertEquals(senderIp, nextHop)

        val routes = routingTable.getAllRoutes()
        assertEquals(1, routes.size)
        assertEquals(2, routes[0].hops)
    }

    @Test
    fun testRouteOptimizesWithFewerHops() {
        val routingTable = RoutingTable()
        routingTable.updateRoute("node_delta", "192.168.49.10", hops = 4)
        assertEquals("192.168.49.10", routingTable.getNextHopIp("node_delta"))

        // Faster path discovered with only 2 hops
        routingTable.updateRoute("node_delta", "192.168.49.20", hops = 2)
        assertEquals("192.168.49.20", routingTable.getNextHopIp("node_delta"))

        // Slower path ignored
        routingTable.updateRoute("node_delta", "192.168.49.30", hops = 5)
        assertEquals("192.168.49.20", routingTable.getNextHopIp("node_delta"))
    }

    @Test
    fun testRouteAgingAndPruning() {
        val routingTable = RoutingTable()
        routingTable.updateRoute("node_expired", "192.168.49.100", hops = 1)
        
        // Instant prune with 0 maxAge should remove it
        routingTable.removeStaleRoutes(maxAgeMs = -1)
        assertNull(routingTable.getNextHopIp("node_expired"))
    }

    @Test
    fun testRreqRrepSerialization() {
        val rreq = RouteRequestPayload(
            requestId = "req_123",
            sourceId = "node_alpha",
            targetDestinationId = "node_omega",
            hopCount = 1
        )
        val jsonRreq = Json.encodeToString(rreq)
        val decodedRreq = Json.decodeFromString<RouteRequestPayload>(jsonRreq)
        assertEquals("req_123", decodedRreq.requestId)
        assertEquals("node_alpha", decodedRreq.sourceId)
        assertEquals("node_omega", decodedRreq.targetDestinationId)

        val rrep = RouteReplyPayload(
            requestId = "req_123",
            targetDestinationId = "node_omega",
            destinationIp = "192.168.49.88",
            hopCount = 2
        )
        val jsonRrep = Json.encodeToString(rrep)
        val decodedRrep = Json.decodeFromString<RouteReplyPayload>(jsonRrep)
        assertEquals("req_123", decodedRrep.requestId)
        assertEquals("192.168.49.88", decodedRrep.destinationIp)
        assertEquals(2, decodedRrep.hopCount)
    }
}
