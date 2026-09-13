package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdpcmCodecExtendedTest {

    @Test
    fun testCompressionRatioIsExactFourToOne() {
        val pcmSize = 640 // 20ms frame
        val pcm = ByteArray(pcmSize)
        val adpcm = AdpcmCodec.encodePcmToAdpcm(pcm)
        assertEquals(pcmSize / 4, adpcm.size)

        val decodedPcm = AdpcmCodec.decodeAdpcmToPcm(adpcm)
        assertEquals(pcmSize, decodedPcm.size)
    }

    @Test
    fun testSilenceRoundtrip() {
        val pcm = ByteArray(320) // 160 zero samples
        val adpcm = AdpcmCodec.encodePcmToAdpcm(pcm)
        val decoded = AdpcmCodec.decodeAdpcmToPcm(adpcm)

        var maxDiff = 0
        for (i in pcm.indices step 2) {
            val orig = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val dec = (decoded[i].toInt() and 0xFF) or (decoded[i + 1].toInt() shl 8)
            maxDiff = maxOf(maxDiff, abs(orig.toShort() - dec.toShort()))
        }
        assertTrue("Max difference for silence should be minimal", maxDiff < 100)
    }

    @Test
    fun testBoundaryAndClippingMaxValues() {
        val pcm = ByteArray(8)
        // Set sample 0 to Short.MAX_VALUE, sample 1 to Short.MIN_VALUE
        val maxVal = Short.MAX_VALUE.toInt()
        val minVal = Short.MIN_VALUE.toInt()

        pcm[0] = (maxVal and 0xFF).toByte()
        pcm[1] = ((maxVal shr 8) and 0xFF).toByte()
        pcm[2] = (minVal and 0xFF).toByte()
        pcm[3] = ((minVal shr 8) and 0xFF).toByte()

        val adpcm = AdpcmCodec.encodePcmToAdpcm(pcm)
        val decoded = AdpcmCodec.decodeAdpcmToPcm(adpcm)
        assertEquals(pcm.size, decoded.size)
    }

    @Test
    fun testSineWaveRoundtripFidelity() {
        // Generate a 1kHz sine wave at 16kHz sample rate
        val samples = 160
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val angle = 2.0 * Math.PI * 1000.0 * i / 16000.0
            val value = (Math.sin(angle) * 10000.0).toInt().toShort()
            pcm[i * 2] = (value.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
        }

        val adpcm = AdpcmCodec.encodePcmToAdpcm(pcm)
        val decoded = AdpcmCodec.decodeAdpcmToPcm(adpcm)

        // Calculate Mean Absolute Error (MAE)
        var totalDiff = 0.0
        for (i in 0 until samples) {
            val orig = ((pcm[i * 2].toInt() and 0xFF) or (pcm[i * 2 + 1].toInt() shl 8)).toShort()
            val dec = ((decoded[i * 2].toInt() and 0xFF) or (decoded[i * 2 + 1].toInt() shl 8)).toShort()
            totalDiff += abs(orig - dec)
        }
        val mae = totalDiff / samples
        // ADPCM lossy coding typically has MAE well under 1500 on full-scale 10000 amplitude sine wave
        assertTrue("Mean absolute error should be reasonable for ADPCM (actual: $mae)", mae < 2000.0)
    }
}
