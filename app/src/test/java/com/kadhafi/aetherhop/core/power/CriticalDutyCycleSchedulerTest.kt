package com.kadhafi.aetherhop.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalDutyCycleSchedulerTest {

    @Test
    fun testSurvivalModeActivationThresholds() {
        // Battery > 15% and not charging -> No survival mode
        assertFalse(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(50, isCharging = false, manualOverride = false))
        assertFalse(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(16, isCharging = false, manualOverride = false))

        // Battery <= 15% and not charging -> Activate survival mode automatically
        assertTrue(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(15, isCharging = false, manualOverride = false))
        assertTrue(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(5, isCharging = false, manualOverride = false))
        assertTrue(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(1, isCharging = false, manualOverride = false))

        // Charging overrides survival mode (even at 5% battery)
        assertFalse(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(5, isCharging = true, manualOverride = false))
        assertFalse(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(5, isCharging = true, manualOverride = true))

        // Manual override triggers survival mode on high battery when not charging
        assertTrue(CriticalDutyCycleScheduler.shouldActivateSurvivalMode(80, isCharging = false, manualOverride = true))
    }

    @Test
    fun testDutyCycleRadioTimingWindows() {
        // Active window: 0..9s of each 60s cycle
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(0L))
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(5L))
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(9L))

        // Dormant sleep window: 10..59s of each 60s cycle
        assertFalse(CriticalDutyCycleScheduler.isRadioActive(10L))
        assertFalse(CriticalDutyCycleScheduler.isRadioActive(30L))
        assertFalse(CriticalDutyCycleScheduler.isRadioActive(59L))

        // Cycle 2 (at 60s, 65s active; at 70s dormant)
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(60L))
        assertTrue(CriticalDutyCycleScheduler.isRadioActive(65L))
        assertFalse(CriticalDutyCycleScheduler.isRadioActive(70L))
    }

    @Test
    fun testRemainingWindowCountdownCalculations() {
        // At 0s, remaining active window is 10s
        assertEquals(10L, CriticalDutyCycleScheduler.getRemainingWindowSeconds(0L))

        // At 4s, remaining active window is 6s
        assertEquals(6L, CriticalDutyCycleScheduler.getRemainingWindowSeconds(4L))

        // At 10s, dormant window starts with 50s remaining (60 - 10 = 50)
        assertEquals(50L, CriticalDutyCycleScheduler.getRemainingWindowSeconds(10L))

        // At 55s, remaining dormant window is 5s (60 - 55 = 5)
        assertEquals(5L, CriticalDutyCycleScheduler.getRemainingWindowSeconds(55L))
    }

    @Test
    fun testDutyCycleRatio() {
        // 10s active / 60s total = 0.1666... (16.7% duty cycle)
        val ratio = CriticalDutyCycleScheduler.getDutyCycleRatio()
        assertEquals(0.1666f, ratio, 0.001f)
    }
}
