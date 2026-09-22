package com.kadhafi.aetherhop.core.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsystemPowerProfilerTest {

    @Test
    fun testDrainRateCalculationAcrossModes() {
        // Base normal mode with all hardware running
        val normalDrain = SubsystemPowerProfiler.calculateTotalDrainRate(
            isBleAdvertising = true,
            isWifiDirectConnected = true,
            isGpsActive = true,
            isPowerSaver = false,
            isTransmittingAudio = false
        )
        // 120 (Screen) + 12 (BLE Adv) + 18 (BLE Scan) + 85 (Wi-Fi Direct) + 45 (GPS High) = 280 mA
        assertEquals(280.0f, normalDrain, 0.5f)

        // Power saver mode (reduced BLE, reduced Wi-Fi duty, GPS saver)
        val saverDrain = SubsystemPowerProfiler.calculateTotalDrainRate(
            isBleAdvertising = true,
            isWifiDirectConnected = true,
            isGpsActive = true,
            isPowerSaver = true,
            isTransmittingAudio = false
        )
        // Screen(120) + BLE adv(4.8) + BLE scan(5.4) + WiFi(51.0) + GPS saver(15.0) = ~196.2 mA
        assertTrue("Saver drain must be significantly lower than normal drain", saverDrain < normalDrain)
        assertEquals(196.2f, saverDrain, 0.5f)

        // With PTT audio transmission active
        val pttDrain = SubsystemPowerProfiler.calculateTotalDrainRate(
            isBleAdvertising = true,
            isWifiDirectConnected = true,
            isGpsActive = true,
            isPowerSaver = false,
            isTransmittingAudio = true
        )
        assertEquals(normalDrain + SubsystemPowerProfiler.DRAIN_AUDIO_PTT_MA, pttDrain, 0.1f)
    }

    @Test
    fun testRemainingHoursEstimation() {
        val batteryCapacity = 4000.0f
        val drainRate = 200.0f // 200 mA per hour

        // 100% battery -> 4000 / 200 = 20.0 hours
        val fullHours = SubsystemPowerProfiler.estimateRemainingHours(
            batteryPercent = 100,
            totalDrainRateMahPerHour = drainRate,
            batteryCapacityMah = batteryCapacity
        )
        assertEquals(20.0f, fullHours, 0.1f)

        // 50% battery -> 2000 / 200 = 10.0 hours
        val halfHours = SubsystemPowerProfiler.estimateRemainingHours(
            batteryPercent = 50,
            totalDrainRateMahPerHour = drainRate,
            batteryCapacityMah = batteryCapacity
        )
        assertEquals(10.0f, halfHours, 0.1f)

        // Zero drain rate defaults gracefully
        val zeroRate = SubsystemPowerProfiler.estimateRemainingHours(50, 0.0f)
        assertEquals(24.0f, zeroRate, 0.1f)
    }

    @Test
    fun testDischargeSlopeAndSpikeDetection() {
        val t0 = 1000000L
        val t1 = t0 + (1000L * 1800L) // 0.5 hours later

        // Case 1: 5% drop in 30 minutes -> 10% / hour (Normal drain)
        val normalSlope = SubsystemPowerProfiler.calculateDischargeSlope(
            prevPercent = 80,
            prevTimestampMs = t0,
            currPercent = 75,
            currTimestampMs = t1
        )
        assertEquals(10.0f, normalSlope, 0.1f)
        assertFalse(SubsystemPowerProfiler.isHighDrainSpike(normalSlope))

        // Case 2: 15% drop in 30 minutes -> 30% / hour (Severe drain spike!)
        val spikeSlope = SubsystemPowerProfiler.calculateDischargeSlope(
            prevPercent = 80,
            prevTimestampMs = t0,
            currPercent = 65,
            currTimestampMs = t1
        )
        assertEquals(30.0f, spikeSlope, 0.1f)
        assertTrue(SubsystemPowerProfiler.isHighDrainSpike(spikeSlope))
    }
}
