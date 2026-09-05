package com.kadhafi.aetherhop.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkQualityCalculatorTest {

    @Test
    fun testCalculateLqiExcellentRating() {
        val lqi = LinkQualityCalculator.calculateLqi(rssi = -55, rttMs = 30, packetLossPercentage = 0f)
        val rating = LinkQualityCalculator.getLqiRating(lqi)

        assertEquals(100, lqi)
        assertEquals("EXCELLENT", rating)
    }

    @Test
    fun testCalculateLqiPoorRating() {
        val lqi = LinkQualityCalculator.calculateLqi(rssi = -98, rttMs = 700, packetLossPercentage = 50f)
        val rating = LinkQualityCalculator.getLqiRating(lqi)

        assertEquals(22, lqi)
        assertEquals("POOR", rating)
    }
}
