package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PttUdpStreamingTest {

    @Test
    fun testDatagramSerializationAndDeserialization() {
        val original = UdpAudioDatagram(
            sessionId = "sess_ptt_alpha_99",
            sequenceNumber = 42L,
            audioData = byteArrayOf(0x10, 0x20, -0x30, 0x4F, 0x00, 0x7F)
        )

        val serialized = PttUdpSocketManager.serialize(original)
        assertNotNull(serialized)

        val deserialized = PttUdpSocketManager.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(original.sessionId, deserialized?.sessionId)
        assertEquals(original.sequenceNumber, deserialized?.sequenceNumber)
        assertArrayEquals(original.audioData, deserialized?.audioData)
    }

    @Test
    fun testCorruptedMagicHeaderRejected() {
        val bytes = byteArrayOf(0x00, 0x00, 0x01, 0x02, 0x03, 0x04)
        val deserialized = PttUdpSocketManager.deserialize(bytes)
        assertNull(deserialized)
    }

    @Test
    fun testPacketLossConcealmentOnSequenceGap() {
        val manager = PttUdpSocketManager()
        val sessionId = "ptt_sess_plc"

        // First frame seq 1
        val frame1 = UdpAudioDatagram(sessionId, 1L, byteArrayOf(10, 20, 30))
        val output1 = manager.handlePacketLossConcealment(frame1)
        assertEquals(1, output1.size)
        assertEquals(1L, output1[0].sequenceNumber)

        // Second frame seq 4 (lost seq 2 and 3)
        val frame4 = UdpAudioDatagram(sessionId, 4L, byteArrayOf(40, 50, 60))
        val output2 = manager.handlePacketLossConcealment(frame4)
        // Should produce concealed frame 2, concealed frame 3, and actual frame 4
        assertEquals(3, output2.size)
        assertEquals(2L, output2[0].sequenceNumber)
        assertEquals(3L, output2[1].sequenceNumber)
        assertEquals(4L, output2[2].sequenceNumber)
    }

    @Test
    fun testConsecutiveFramesWithoutGap() {
        val manager = PttUdpSocketManager()
        val sessionId = "ptt_sess_consecutive"

        val frame1 = UdpAudioDatagram(sessionId, 10L, byteArrayOf(1, 2))
        val out1 = manager.handlePacketLossConcealment(frame1)
        assertEquals(1, out1.size)

        val frame2 = UdpAudioDatagram(sessionId, 11L, byteArrayOf(3, 4))
        val out2 = manager.handlePacketLossConcealment(frame2)
        assertEquals(1, out2.size)
        assertEquals(11L, out2[0].sequenceNumber)
    }
}
