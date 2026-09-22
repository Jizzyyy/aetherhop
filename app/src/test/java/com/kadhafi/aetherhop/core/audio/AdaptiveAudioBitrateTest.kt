package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAudioBitrateTest {

    @Test
    fun testLqiThresholdBitrateStepping() {
        // Excellent LQI (score >= 80) -> 20ms frame interval, 16kHz
        val configExcellent = AdaptiveAudioBitrateController.calculateBitrateConfig(lqiScore = 90, packetLossPercentage = 0f)
        assertEquals(20L, configExcellent.frameIntervalMs)
        assertEquals(16000, configExcellent.sampleRateHz)
        assertFalse(configExcellent.isDownsampled)
        assertEquals(32, configExcellent.nominalBitrateKbps)
        assertTrue(configExcellent.label.contains("HD"))

        // Good LQI (60..79) -> 30ms frame interval, 16kHz
        val configGood = AdaptiveAudioBitrateController.calculateBitrateConfig(lqiScore = 70, packetLossPercentage = 2f)
        assertEquals(30L, configGood.frameIntervalMs)
        assertEquals(16000, configGood.sampleRateHz)
        assertFalse(configGood.isDownsampled)

        // Fair LQI (40..59) -> 40ms frame interval, 16kHz
        val configFair = AdaptiveAudioBitrateController.calculateBitrateConfig(lqiScore = 50, packetLossPercentage = 8f)
        assertEquals(40L, configFair.frameIntervalMs)
        assertEquals(16000, configFair.sampleRateHz)
        assertFalse(configFair.isDownsampled)

        // Poor LQI (< 40) -> 60ms frame interval, 8kHz low-bandwidth mode
        val configPoor = AdaptiveAudioBitrateController.calculateBitrateConfig(lqiScore = 30, packetLossPercentage = 15f)
        assertEquals(60L, configPoor.frameIntervalMs)
        assertEquals(8000, configPoor.sampleRateHz)
        assertTrue(configPoor.isDownsampled)
        assertEquals(16, configPoor.nominalBitrateKbps)
    }

    @Test
    fun testHighPacketLossForcesLowBandwidthModeRegardlessOfRssi() {
        // High packet loss (> 20%) triggers low-bandwidth 8kHz even if raw RSSI is strong
        val configHighLoss = AdaptiveAudioBitrateController.calculateBitrateConfig(lqiScore = 85, packetLossPercentage = 25f)
        assertEquals(8000, configHighLoss.sampleRateHz)
        assertTrue(configHighLoss.isDownsampled)
        assertEquals(60L, configHighLoss.frameIntervalMs)
    }

    @Test
    fun testDownsamplingAndUpsampling() {
        // 4 samples at 16-bit mono = 8 bytes
        val pcm16k = byteArrayOf(
            0x10, 0x01, // sample 0
            0x20, 0x02, // sample 1 (skipped in 2:1 downsampling)
            0x30, 0x03, // sample 2
            0x40, 0x04  // sample 3 (skipped in 2:1 downsampling)
        )

        val pcm8k = AdaptiveAudioBitrateController.downsample16kTo8k(pcm16k)
        assertEquals(4, pcm8k.size) // 2 samples = 4 bytes
        assertEquals(0x10.toByte(), pcm8k[0])
        assertEquals(0x01.toByte(), pcm8k[1])
        assertEquals(0x30.toByte(), pcm8k[2])
        assertEquals(0x03.toByte(), pcm8k[3])

        // Upsampling restores to 8 bytes by duplicating samples
        val restored16k = AdaptiveAudioBitrateController.upsample8kTo16k(pcm8k)
        assertEquals(8, restored16k.size)
        assertEquals(0x10.toByte(), restored16k[0])
        assertEquals(0x01.toByte(), restored16k[1])
        assertEquals(0x10.toByte(), restored16k[2])
        assertEquals(0x01.toByte(), restored16k[3])
    }

    @Test
    fun testJitterBufferQueueDepthAdaptation() {
        val jitterBuffer = AudioJitterBuffer(maxBufferSize = 10)

        // Low jitter (< 60ms) -> Low latency buffer depth (6 frames)
        jitterBuffer.adaptJitterDepth(30L)
        assertEquals(6, jitterBuffer.getMaxBufferSize())

        // Moderate jitter (60..150ms) -> Balanced buffer depth (10 frames)
        jitterBuffer.adaptJitterDepth(90L)
        assertEquals(10, jitterBuffer.getMaxBufferSize())

        // High jitter (> 150ms) -> Deep buffer depth (16 frames)
        jitterBuffer.adaptJitterDepth(200L)
        assertEquals(16, jitterBuffer.getMaxBufferSize())
    }

    @Test
    fun testUdpDatagramHeaderSampleRateNegotiation() {
        val datagram8k = UdpAudioDatagram(
            sessionId = "sess_adaptive",
            sequenceNumber = 1L,
            audioData = byteArrayOf(1, 2, 3),
            sampleRateHz = 8000,
            flags = 1.toByte()
        )

        val serialized = PttUdpSocketManager.serialize(datagram8k)
        val deserialized = PttUdpSocketManager.deserialize(serialized)

        assertEquals(8000, deserialized?.sampleRateHz)
        assertEquals(1.toByte(), deserialized?.flags)
    }
}
