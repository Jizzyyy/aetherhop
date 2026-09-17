package com.kadhafi.aetherhop.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalThrottleManagerTest {

    @Test
    fun testNominalTemperatureEvaluation() {
        val levelNormal = ThermalThrottleManager.evaluateThermalLevel(28.5f)
        assertEquals(ThermalLevel.NOMINAL, levelNormal)

        val levelBorderline = ThermalThrottleManager.evaluateThermalLevel(37.9f)
        assertEquals(ThermalLevel.NOMINAL, levelBorderline)
    }

    @Test
    fun testWarmTemperatureEvaluation() {
        val levelWarm = ThermalThrottleManager.evaluateThermalLevel(38.0f)
        assertEquals(ThermalLevel.WARM, levelWarm)

        val levelWarmUpper = ThermalThrottleManager.evaluateThermalLevel(42.9f)
        assertEquals(ThermalLevel.WARM, levelWarmUpper)
    }

    @Test
    fun testHotAndCriticalThresholdEvaluation() {
        val levelHot = ThermalThrottleManager.evaluateThermalLevel(43.0f)
        assertEquals(ThermalLevel.HOT, levelHot)

        val levelSevere = ThermalThrottleManager.evaluateThermalLevel(47.5f)
        assertEquals(ThermalLevel.HOT, levelSevere)

        val levelCritical = ThermalThrottleManager.evaluateThermalLevel(48.0f)
        assertEquals(ThermalLevel.CRITICAL, levelCritical)

        val levelExtreme = ThermalThrottleManager.evaluateThermalLevel(55.0f)
        assertEquals(ThermalLevel.CRITICAL, levelExtreme)
    }

    @Test
    fun testThrottledScanIntervalScaling() {
        val baseIntervalMs = 15000L // 15 seconds default balanced interval

        // Nominal: 1.0x -> 15s
        assertEquals(15000L, ThermalThrottleManager.getThrottledInterval(baseIntervalMs, ThermalLevel.NOMINAL))

        // Warm: 1.5x -> 22.5s
        assertEquals(22500L, ThermalThrottleManager.getThrottledInterval(baseIntervalMs, ThermalLevel.WARM))

        // Hot: 2.0x -> 30s
        assertEquals(30000L, ThermalThrottleManager.getThrottledInterval(baseIntervalMs, ThermalLevel.HOT))

        // Critical: 4.0x -> 60s
        assertEquals(60000L, ThermalThrottleManager.getThrottledInterval(baseIntervalMs, ThermalLevel.CRITICAL))
    }

    @Test
    fun testThermalStateDataStructure() {
        val nominalState = ThermalState(31.2f, ThermalLevel.NOMINAL, isThrottled = false)
        assertFalse(nominalState.isThrottled)
        assertEquals(31.2f, nominalState.temperatureCelsius, 0.01f)

        val throttledState = ThermalState(45.0f, ThermalLevel.HOT, isThrottled = true)
        assertTrue(throttledState.isThrottled)
        assertEquals(ThermalLevel.HOT, throttledState.thermalLevel)
    }
}
