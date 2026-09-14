package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.local.entity.ChannelMessageEntity
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.TransportMedium
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChannelFloodRoutingTest {

    @Test
    fun testChannelMessagePacketSerializationAndIdentification() {
        val channelId = "#ops-tactical"
        val chatMsg = ChatMessage(
            id = "msg_ch_001",
            senderId = "node_bravo",
            senderName = "Bravo Leader",
            text = "Patrol unit reached waypoint Alfa",
            isMine = false
        )

        val packet = MeshPacket(
            id = chatMsg.id,
            senderId = chatMsg.senderId,
            targetId = channelId,
            type = PacketType.CHAT,
            payload = Json.encodeToString(chatMsg),
            ttl = 5,
            transport = TransportMedium.WIFI_DIRECT
        )

        assertTrue("Target ID should identify as channel broadcast", packet.targetId.startsWith("#"))
        assertEquals(channelId, packet.targetId)

        val json = Json.encodeToString(packet)
        val decoded = Json.decodeFromString<MeshPacket>(json)
        assertEquals(packet.id, decoded.id)
        assertEquals(5, decoded.ttl)

        val decodedMsg = Json.decodeFromString<ChatMessage>(decoded.payload)
        assertEquals("Patrol unit reached waypoint Alfa", decodedMsg.text)
        assertEquals("Bravo Leader", decodedMsg.senderName)
    }

    @Test
    fun testMultiHopTtlDecrementAndRelayCondition() {
        val channelId = "#emergency"
        val originalPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "node_alpha",
            targetId = channelId,
            type = PacketType.CHAT,
            payload = "Mayday in Sector 4",
            ttl = 4
        )

        // Hop 1 forward
        assertTrue(originalPacket.ttl > 1)
        val hop1Packet = originalPacket.copy(ttl = originalPacket.ttl - 1)
        assertEquals(3, hop1Packet.ttl)

        // Hop 2 forward
        assertTrue(hop1Packet.ttl > 1)
        val hop2Packet = hop1Packet.copy(ttl = hop1Packet.ttl - 1)
        assertEquals(2, hop2Packet.ttl)

        // Hop 3 forward
        assertTrue(hop2Packet.ttl > 1)
        val hop3Packet = hop2Packet.copy(ttl = hop2Packet.ttl - 1)
        assertEquals(1, hop3Packet.ttl)

        // Final hop: ttl is 1, packet must NOT be forwarded further to prevent infinite loops
        assertFalse(hop3Packet.ttl > 1)
    }

    @Test
    fun testFloodTargetEchoFilter() {
        val incomingSenderIp = "192.168.49.2"
        val allKnownNeighbors = setOf("192.168.49.2", "192.168.49.3", "192.168.49.4")

        // Filter out the sender IP to avoid echoing back to source
        val forwardTargets = allKnownNeighbors.filter { it.isNotBlank() && it != incomingSenderIp }
        assertEquals(2, forwardTargets.size)
        assertFalse(forwardTargets.contains(incomingSenderIp))
        assertTrue(forwardTargets.contains("192.168.49.3"))
        assertTrue(forwardTargets.contains("192.168.49.4"))
    }

    @Test
    fun testDeduplicationCachePreventsReloop() {
        val processedIds = LinkedHashMap<String, Boolean>()
        val packetId = "packet_broadcast_storm_check"

        // First arrival: not processed yet
        val isFirstDuplicate = processedIds.containsKey(packetId)
        assertFalse(isFirstDuplicate)
        processedIds[packetId] = true

        // Second arrival from a different neighbor: detected as duplicate
        val isSecondDuplicate = processedIds.containsKey(packetId)
        assertTrue(isSecondDuplicate)
    }

    @Test
    fun testChannelMessageEntityMapping() {
        val entity = ChannelMessageEntity(
            messageId = "entity_id_42",
            channelId = "#recon",
            senderId = "recon_01",
            senderName = "Scout",
            text = "Area clear",
            timestamp = 1788555000000L,
            isMine = false
        )

        assertEquals("entity_id_42", entity.messageId)
        assertEquals("#recon", entity.channelId)
        assertEquals("Scout", entity.senderName)
        assertFalse(entity.isMine)
    }
}
