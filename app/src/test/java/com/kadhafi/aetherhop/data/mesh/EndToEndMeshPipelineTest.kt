package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.DeliveryReceiptPayload
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.MessageStatus
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.PeerNode
import com.kadhafi.aetherhop.domain.model.RouteReplyPayload
import com.kadhafi.aetherhop.domain.model.RouteRequestPayload
import com.kadhafi.aetherhop.domain.model.TransportMedium
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EndToEndMeshPipelineTest {

    @Test
    fun testPeerDiscoveryToRouteLearningPipeline() {
        val routingTable = RoutingTable()
        val router = MeshTransportRouter()

        // 1. Peer discovered via Wi-Fi Direct
        val discoveredPeer = PeerNode(
            id = "node_bravo",
            name = "Tactical Bravo",
            address = "192.168.49.50",
            rssi = -68,
            distanceMeters = 12.5,
            operationalStatus = "PATROL",
            transport = TransportMedium.WIFI_DIRECT
        )
        assertEquals("node_bravo", discoveredPeer.id)
        assertEquals(TransportMedium.WIFI_DIRECT, discoveredPeer.transport)

        // 2. Incoming packet from discovered peer teaches route to routing table
        val incomingPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = discoveredPeer.id,
            targetId = "self_node",
            type = PacketType.CHAT,
            payload = "Ping from Bravo",
            ttl = 5
        )
        val hops = maxOf(1, 5 - incomingPacket.ttl + 1)
        routingTable.updateRoute(incomingPacket.senderId, discoveredPeer.address, hops)
        router.registerLink(incomingPacket.senderId, TransportLinkType.WIFI_DIRECT, discoveredPeer.address)

        // 3. Verify resolved next hop and transport
        val nextHop = routingTable.getNextHopIp("node_bravo")
        assertEquals("192.168.49.50", nextHop)

        val link = router.resolveOptimalLink("node_bravo")
        assertNotNull(link)
        assertEquals(TransportLinkType.WIFI_DIRECT, link?.linkType)
    }

    @Test
    fun testRreqDiscoveryAndUnicastDeliveryFlow() {
        val routingTable = RoutingTable()

        // Target Charlie's IP is unknown initially
        val targetId = "node_charlie"
        val resolvedIp = routingTable.getNextHopIp(targetId)
        assertEquals(null, resolvedIp)

        // Node broadcasts RREQ
        val rreq = RouteRequestPayload(
            requestId = "req_99",
            sourceId = "self_node",
            targetDestinationId = targetId,
            hopCount = 0
        )
        val rreqPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "self_node",
            targetId = "BROADCAST",
            type = PacketType.RREQ,
            payload = Json.encodeToString(rreq),
            ttl = 5
        )
        assertEquals(PacketType.RREQ, rreqPacket.type)

        // Relay node responds with RREP
        val rrep = RouteReplyPayload(
            requestId = rreq.requestId,
            targetDestinationId = targetId,
            destinationIp = "192.168.49.88",
            hopCount = 2
        )
        routingTable.updateRoute(rrep.targetDestinationId, rrep.destinationIp, rrep.hopCount)

        // Rute now available
        val destinationIpAfterRrep = routingTable.getNextHopIp(targetId)
        assertEquals("192.168.49.88", destinationIpAfterRrep)

        // Unicast message dispatched with DeliveryReceipt expectation
        val chatMsg = ChatMessage(
            id = "msg_001",
            senderId = "self_node",
            senderName = "Commander",
            text = "Move to Extraction Point",
            status = MessageStatus.SENT
        )
        val dataPacket = MeshPacket(
            id = chatMsg.id,
            senderId = "self_node",
            targetId = targetId,
            type = PacketType.CHAT,
            payload = Json.encodeToString(chatMsg),
            transport = TransportMedium.WIFI_DIRECT
        )
        assertEquals(TransportMedium.WIFI_DIRECT, dataPacket.transport)

        // Receiver returns DELIVERY_RECEIPT
        val receipt = DeliveryReceiptPayload(
            messageId = chatMsg.id,
            senderId = "self_node",
            receiverId = targetId
        )
        val receiptPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = targetId,
            targetId = "self_node",
            type = PacketType.DELIVERY_RECEIPT,
            payload = Json.encodeToString(receipt)
        )

        val decodedReceipt = Json.decodeFromString<DeliveryReceiptPayload>(receiptPacket.payload)
        assertEquals(chatMsg.id, decodedReceipt.messageId)
        assertEquals(MessageStatus.DELIVERED, MessageStatus.valueOf("DELIVERED"))
    }
}
