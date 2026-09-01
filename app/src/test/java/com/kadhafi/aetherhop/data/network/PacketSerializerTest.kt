package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PacketSerializerTest {

    @Test
    fun testWriteAndReadPacketRoundtrip() {
        val originalPacket = MeshPacket(
            id = "test_msg_123",
            senderId = "node_a",
            targetId = "node_b",
            type = PacketType.CHAT,
            payload = "Hello P2P Mesh!",
            ttl = 5
        )

        val outputStream = ByteArrayOutputStream()
        PacketSerializer.writePacket(outputStream, originalPacket)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val deserializedPacket = PacketSerializer.readPacket(inputStream)

        assertNotNull(deserializedPacket)
        assertEquals(originalPacket.id, deserializedPacket.id)
        assertEquals(originalPacket.senderId, deserializedPacket.senderId)
        assertEquals(originalPacket.targetId, deserializedPacket.targetId)
        assertEquals(originalPacket.type, deserializedPacket.type)
        assertEquals(originalPacket.payload, deserializedPacket.payload)
        assertEquals(originalPacket.ttl, deserializedPacket.ttl)
    }

    @Test(expected = IOException::class)
    fun testReadInvalidPacketLengthThrowsException() {
        val invalidBytes = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F) // Extremely large length
        val inputStream = ByteArrayInputStream(invalidBytes)
        PacketSerializer.readPacket(inputStream)
    }
}
