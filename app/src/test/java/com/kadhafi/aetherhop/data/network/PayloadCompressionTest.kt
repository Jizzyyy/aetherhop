package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PayloadCompressionTest {

    @Before
    fun setUp() {
        PayloadCompressionManager.resetStats()
    }

    @Test
    fun testLosslessRoundtripCompression() {
        val originalPayload = buildString {
            append("AetherHop Tactical Offline Encrypted Mesh Telemetry Bundle: ")
            repeat(30) { idx ->
                append("node_$idx=[lat=-6.2088,lon=106.8456,alt=120,status=PATROL]; ")
            }
        }

        assertTrue(originalPayload.length > PayloadCompressionManager.COMPRESSION_THRESHOLD_BYTES)

        val compressedBase64 = PayloadCompressionManager.compressString(originalPayload)
        assertNotEquals(originalPayload, compressedBase64)
        assertTrue("Compressed representation should be significantly smaller than raw payload", compressedBase64.length < originalPayload.length)

        val decompressed = PayloadCompressionManager.decompressString(compressedBase64)
        assertEquals(originalPayload, decompressed)
    }

    @Test
    fun testCompressionThresholdEvaluation() {
        val shortPayload = "Hello Peer"
        assertFalse(PayloadCompressionManager.shouldCompress(shortPayload))

        val exactBoundaryPayload = "A".repeat(256)
        assertTrue(PayloadCompressionManager.shouldCompress(exactBoundaryPayload))
    }

    @Test
    fun testMeshPacketWithOptimalCompression() {
        val largeMessageText = "URGENT EVACUATION: Sector 4 perimeter breach detected. ".repeat(15)

        val rawPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "node_hq",
            targetId = "BROADCAST",
            type = PacketType.CHAT,
            payload = largeMessageText,
            isCompressed = false
        )

        val optimizedPacket = rawPacket.withOptimalCompression()
        assertTrue(optimizedPacket.isCompressed)
        assertTrue(optimizedPacket.payload.length < rawPacket.payload.length)

        // Decompress packet
        val restoredPacket = optimizedPacket.withDecompression()
        assertFalse(restoredPacket.isCompressed)
        assertEquals(largeMessageText, restoredPacket.payload)
    }

    @Test
    fun testShortPacketUntouchedByOptimalCompression() {
        val shortPacket = MeshPacket(
            id = "pkt_short_01",
            senderId = "node_a",
            targetId = "node_b",
            type = PacketType.PING,
            payload = "1788500000000",
            isCompressed = false
        )

        val resultPacket = shortPacket.withOptimalCompression()
        assertFalse(resultPacket.isCompressed)
        assertEquals(shortPacket.payload, resultPacket.payload)
    }

    @Test
    fun testCumulativeSavingsTracking() {
        assertEquals(0L, PayloadCompressionManager.getCumulativeSavingsBytes())

        val repetitiveText = "TACTICAL DATA PACKET STREAM #42. ".repeat(40)
        PayloadCompressionManager.compressString(repetitiveText)

        assertTrue(PayloadCompressionManager.getCumulativeRawBytes() > 0)
        assertTrue(PayloadCompressionManager.getCumulativeCompressedBytes() > 0)
        assertTrue(PayloadCompressionManager.getCumulativeSavingsBytes() > 0)
    }

    @Test
    fun testCorruptedCompressedStreamDoesNotCrash() {
        val corruptedPacket = MeshPacket(
            id = "pkt_corrupted",
            senderId = "bad_node",
            targetId = "self",
            type = PacketType.CHAT,
            payload = "InvalidCorruptedBase64GzipData!@#$%",
            isCompressed = true
        )

        // withDecompression gracefully catches exception and keeps payload intact without crashing
        val fallback = corruptedPacket.withDecompression()
        assertEquals(corruptedPacket.payload, fallback.payload)
    }
}
