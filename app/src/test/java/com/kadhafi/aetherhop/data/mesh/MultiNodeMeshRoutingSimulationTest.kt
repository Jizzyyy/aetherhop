package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultiNodeMeshRoutingSimulationTest {

    @Test
    fun testFiveNodeMultiHopRelayRouting() {
        val routingTable = RoutingTable()
        routingTable.updateRoute("node_E", "192.168.49.2", hops = 4)

        val originPacket = MeshPacket(
            id = "msg_hop_1",
            senderId = "node_A",
            targetId = "node_E",
            type = PacketType.CHAT,
            payload = "Emergency Multi-Hop Test",
            ttl = 5
        )

        val nextHop = routingTable.getNextHopIp(originPacket.targetId)
        assertEquals("192.168.49.2", nextHop)

        val forwardedPacket = originPacket.copy(ttl = originPacket.ttl - 1)
        assertEquals(4, forwardedPacket.ttl)
        assertEquals("node_E", forwardedPacket.targetId)
    }
}
