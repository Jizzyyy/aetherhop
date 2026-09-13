package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {

    @Test
    fun testSilenceHasZeroRms() {
        val silence = ByteArray(640)
        val rms = VoiceActivityDetector.calculateRms(silence)
        assertEquals(0.0, rms, 0.001)
        assertFalse(VoiceActivityDetector.isSpeechDetected(silence))
    }

    @Test
    fun testEmptyPcmReturnsZero() {
        assertEquals(0.0, VoiceActivityDetector.calculateRms(ByteArray(0)), 0.001)
        assertEquals(0.0, VoiceActivityDetector.calculateRms(ByteArray(1)), 0.001)
        assertFalse(VoiceActivityDetector.isSpeechDetected(ByteArray(0)))
    }

    @Test
    fun testHighAmplitudeSpeechDetected() {
        val samples = 320
        val pcm = ByteArray(samples * 2)
        val loudSample: Short = 2500 // Loud vocalization
        for (i in 0 until samples) {
            pcm[i * 2] = (loudSample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((loudSample.toInt() shr 8) and 0xFF).toByte()
        }

        val rms = VoiceActivityDetector.calculateRms(pcm)
        assertEquals(2500.0, rms, 1.0)
        assertTrue(VoiceActivityDetector.isSpeechDetected(pcm))
    }

    @Test
    fun testLowNoiseBelowThresholdIgnored() {
        val samples = 320
        val pcm = ByteArray(samples * 2)
        val quietNoise: Short = 150 // Gentle background hiss
        for (i in 0 until samples) {
            pcm[i * 2] = (quietNoise.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((quietNoise.toInt() shr 8) and 0xFF).toByte()
        }

        val rms = VoiceActivityDetector.calculateRms(pcm)
        assertEquals(150.0, rms, 1.0)
        assertFalse(VoiceActivityDetector.isSpeechDetected(pcm))
    }

    @Test
    fun testCustomThresholdSupport() {
        val samples = 100
        val pcm = ByteArray(samples * 2)
        val sample: Short = 300
        for (i in 0 until samples) {
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        // 300 is above threshold 200, but below threshold 400
        assertTrue(VoiceActivityDetector.isSpeechDetected(pcm, threshold = 200.0))
        assertFalse(VoiceActivityDetector.isSpeechDetected(pcm, threshold = 400.0))
    }
}
