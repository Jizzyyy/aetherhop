package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.network.PayloadCompressionManager
import com.kadhafi.aetherhop.data.network.withDecompression
import com.kadhafi.aetherhop.data.network.withOptimalCompression
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.TransportMedium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CompressedMeshTrafficSimulationTest {

    @Test
    fun testMultiHopCompressedPacketForwarding() {
        val largeMessage = buildString {
            append("SITREP 1400Z: Force Recon reporting sector 3 clear. ")
            repeat(20) {
                append("Observed no enemy movements along ridge line grid 48M ZC 12345 67890. ")
            }
        }
        assertTrue(largeMessage.length > PayloadCompressionManager.COMPRESSION_THRESHOLD_BYTES)

        // 1. Originating node creates packet and compresses
        val originPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "scout_node_1",
            targetId = "hq_node_command",
            type = PacketType.CHAT,
            payload = largeMessage,
            ttl = 5,
            transport = TransportMedium.WIFI_DIRECT
        ).withOptimalCompression()

        assertTrue(originPacket.isCompressed)
        assertTrue(originPacket.payload.length < largeMessage.length)

        // 2. Intermediate Relay Hop 1 (does not decompress, simply decrements TTL and forwards)
        val hop1 = originPacket.copy(ttl = originPacket.ttl - 1)
        assertEquals(4, hop1.ttl)
        assertTrue("Hop 1 payload must remain compressed across air", hop1.isCompressed)

        // 3. Intermediate Relay Hop 2
        val hop2 = hop1.copy(ttl = hop1.ttl - 1)
        assertEquals(3, hop2.ttl)
        assertTrue(hop2.isCompressed)

        // 4. Destination HQ Node receives and decompresses
        val deliveredPacket = hop2.withDecompression()
        assertFalse(deliveredPacket.isCompressed)
        assertEquals(largeMessage, deliveredPacket.payload)
    }

    @Test
    fun testAlreadyCompressedPacketNotDoubleCompressed() {
        val text = "A".repeat(500)
        val packet = MeshPacket(
            id = "pkt_id",
            senderId = "s",
            targetId = "t",
            type = PacketType.CHAT,
            payload = text
        ).withOptimalCompression()

        assertTrue(packet.isCompressed)
        val originalCompressedPayload = packet.payload

        // Second compression attempt on already compressed packet
        val doubleAttempt = packet.withOptimalCompression()
        assertEquals(originalCompressedPayload, doubleAttempt.payload)
    }
}
