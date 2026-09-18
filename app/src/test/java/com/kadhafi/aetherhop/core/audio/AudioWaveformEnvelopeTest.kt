package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioWaveformEnvelopeTest {

    @Test
    fun testWaveformExtractionDownsamplesToExactBarCount() {
        val audioBytes = ByteArray(3200) { ((it % 256) - 128).toByte() }
        val barCount = 32

        val waveform = AudioWaveformExtractor.extractWaveformFromBytes(audioBytes, barCount)
        assertEquals(barCount, waveform.size)

        waveform.forEach { amp ->
            assertTrue("Amplitude must be normalized between 0.15 and 1.0, was $amp", amp in 0.15f..1.0f)
        }
    }

    @Test
    fun testEmptyBytesReturnsDefaultPaddedEnvelope() {
        val emptyBytes = ByteArray(0)
        val waveform = AudioWaveformExtractor.extractWaveformFromBytes(emptyBytes, 32)
        assertEquals(32, waveform.size)
        assertEquals(0.15f, waveform[0], 0.001f)
    }

    @Test
    fun testWaveformSerializationAndDeserializationRoundtrip() {
        val sampleBars = listOf(0.15f, 0.45f, 0.85f, 1.0f, 0.35f, 0.70f)
        val serialized = AudioWaveformExtractor.serializeWaveform(sampleBars)
        assertNotNull(serialized)
        assertTrue(serialized.contains(","))

        val deserialized = AudioWaveformExtractor.deserializeWaveform(serialized)
        assertEquals(sampleBars.size, deserialized.size)
        for (i in sampleBars.indices) {
            assertEquals(sampleBars[i], deserialized[i], 0.01f)
        }
    }

    @Test
    fun testSineWaveEnvelopePeakLocalization() {
        // Create an audio buffer where amplitude peaks in the middle (bars 14..18)
        val samples = 3200
        val pcm = ByteArray(samples)
        for (i in 0 until samples) {
            val window = sin(Math.PI * i / samples).toFloat() // window envelope 0 -> 1 -> 0
            val raw = (window * 120.0f).toInt().toByte()
            pcm[i] = raw
        }

        val waveform = AudioWaveformExtractor.extractWaveformFromBytes(pcm, 32)
        val centerIndex = 16
        val peakAmp = waveform[centerIndex]
        val edgeAmp = waveform[0]

        assertTrue("Peak at center ($peakAmp) must be higher than edge ($edgeAmp)", peakAmp > edgeAmp)
        assertEquals(1.0f, peakAmp, 0.05f)
    }

    @Test
    fun testDeserializeBlankStringReturnsEmpty() {
        assertTrue(AudioWaveformExtractor.deserializeWaveform("").isEmpty())
        assertTrue(AudioWaveformExtractor.deserializeWaveform("   ").isEmpty())
    }
}
