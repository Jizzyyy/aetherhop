package com.kadhafi.aetherhop.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TacticalThemeContrastTest {

    private fun sRgbToLinear(c: Float): Double {
        return if (c <= 0.04045f) {
            c.toDouble() / 12.92
        } else {
            ((c.toDouble() + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun calculateRelativeLuminance(color: Color): Double {
        val r = sRgbToLinear(color.red)
        val g = sRgbToLinear(color.green)
        val b = sRgbToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    @Test
    fun testAllThemePresetsAreUnique() {
        val presets = ThemePreset.entries
        val uniqueNames = presets.map { it.name }.toSet()
        assertEquals(6, presets.size)
        assertEquals(6, uniqueNames.size)
    }

    @Test
    fun testTacticalRedCanvasIsUltraLowLux() {
        val lum = calculateRelativeLuminance(TacticalRedCanvas)
        // Must be less than 2% luminance to protect scotopic night adaptation
        assertTrue("TacticalRedCanvas luminance must be < 0.02, was $lum", lum < 0.02)
    }

    @Test
    fun testTacticalRedHasZeroBlueLightBreach() {
        // Light discipline: green and blue channels must be close to zero (< 0.25)
        assertTrue("Tactical red primary should minimize green spectrum", TacticalRedPrimary.green < 0.15f)
        assertTrue("Tactical red primary should minimize blue spectrum", TacticalRedPrimary.blue < 0.25f)
        assertTrue("Tactical red primary must have dominant red component", TacticalRedPrimary.red > 0.9f)
    }

    @Test
    fun testNvgCanvasIsUltraLowLux() {
        val lum = calculateRelativeLuminance(NvgCanvas)
        assertTrue("NvgCanvas luminance must be < 0.02, was $lum", lum < 0.02)
    }

    @Test
    fun testNvgPhosphorGreenSpectralDominance() {
        // PVS-14 green phosphor: green component is maximal, red and blue are minimal
        assertTrue(NvgPrimary.green > 0.9f)
        assertTrue(NvgPrimary.red < 0.1f)
        assertTrue(NvgPrimary.blue < 0.3f)
    }

    @Test
    fun testAmoledCanvasIsAbsoluteZeroLuminance() {
        val lum = calculateRelativeLuminance(AmoledCanvas)
        assertEquals(0.0, lum, 0.0001)
    }
}
