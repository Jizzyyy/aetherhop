package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformScrubberPlaybackTest {

    @Test
    fun testSeekFractionCalculationFromTouchPosition() {
        val waveformWidthPx = 400.0f

        fun calculateFraction(touchX: Float): Float {
            return (touchX / waveformWidthPx).coerceIn(0.0f, 1.0f)
        }

        // Exact center tap
        assertEquals(0.5f, calculateFraction(200.0f), 0.001f)

        // Quarter tap
        assertEquals(0.25f, calculateFraction(100.0f), 0.001f)

        // Start and end bounds
        assertEquals(0.0f, calculateFraction(0.0f), 0.001f)
        assertEquals(1.0f, calculateFraction(400.0f), 0.001f)

        // Over-drag to the left (< 0) clamps to 0.0
        assertEquals(0.0f, calculateFraction(-50.0f), 0.001f)

        // Over-drag to the right (> 400) clamps to 1.0
        assertEquals(1.0f, calculateFraction(550.0f), 0.001f)
    }

    @Test
    fun testTargetSeekMillisCalculation() {
        val totalDurationMs = 12000L // 12-second voice note

        fun calculateSeekMs(fraction: Float): Int {
            return (totalDurationMs * fraction.coerceIn(0f, 1f)).toInt()
        }

        assertEquals(0, calculateSeekMs(0.0f))
        assertEquals(3000, calculateSeekMs(0.25f))
        assertEquals(6000, calculateSeekMs(0.50f))
        assertEquals(9000, calculateSeekMs(0.75f))
        assertEquals(12000, calculateSeekMs(1.0f))
    }

    @Test
    fun testActiveWaveformBarIndexMapping() {
        val totalBars = 32

        fun getActiveBarIndex(progressFraction: Float): Int {
            return (progressFraction * totalBars).toInt().coerceIn(0, totalBars - 1)
        }

        // At beginning
        assertEquals(0, getActiveBarIndex(0.0f))

        // Halfway through 32 bars
        assertEquals(16, getActiveBarIndex(0.5f))

        // Three quarters
        assertEquals(24, getActiveBarIndex(0.75f))

        // At completion
        assertEquals(31, getActiveBarIndex(1.0f))
    }
}
