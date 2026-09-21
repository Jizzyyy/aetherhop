package com.kadhafi.aetherhop.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualWatchPriorityTest {

    @Test
    fun testDualWatchDisabledIgnoresPriorityTraffic() {
        val scanner = DualWatchScanner()
        scanner.setDualWatchEnabled(false)

        val triggered = scanner.onPacketReceived(
            channelOrTargetId = DualWatchState.PRIORITY_EMERGENCY_CHANNEL,
            senderName = "Operator X",
            isEmergency = true
        )
        assertFalse("Disabled scanner must not trigger priority", triggered)
        assertFalse(scanner.shouldDuckNormalAudio())
        assertFalse(scanner.shouldPreemptNormalAudio(isIncomingSos = false))
    }

    @Test
    fun testDualWatchTriggersOnEmergencyChannel() {
        val scanner = DualWatchScanner()
        scanner.setDualWatchEnabled(true)
        scanner.setPrimaryChannel("#recon-team")
        scanner.setPriorityChannel("#emergency")

        // Normal traffic on primary channel -> No ducking
        val normalHit = scanner.onPacketReceived("#recon-team", "Operator A", isEmergency = false)
        assertFalse(normalHit)
        assertFalse(scanner.shouldDuckNormalAudio())

        // Emergency packet on priority channel -> Triggers priority & ducking
        val priorityHit = scanner.onPacketReceived("#emergency", "SOS Unit", isEmergency = false)
        assertTrue(priorityHit)
        assertTrue(scanner.shouldDuckNormalAudio())
        assertTrue(scanner.state.value.isPriorityActive)
        assertEquals("SOS Unit", scanner.state.value.activePrioritySender)
    }

    @Test
    fun testDualWatchEmergencyPreemptionOnSosAlert() {
        val scanner = DualWatchScanner()
        scanner.setDualWatchEnabled(true)

        // Incoming packet marked with SOS alert flag on any target ID
        val sosTriggered = scanner.onPacketReceived("BROADCAST", "Trapped Medic", isEmergency = true)
        assertTrue(sosTriggered)
        assertTrue(scanner.shouldPreemptNormalAudio(isIncomingSos = true))
    }

    @Test
    fun testPriorityTimeoutRestoresNormalAudio() {
        val scanner = DualWatchScanner()
        scanner.setDualWatchEnabled(true)

        val triggerTime = 100000L
        scanner.onPacketReceived("#emergency", "Caller", isEmergency = false, timestampMs = triggerTime)
        assertTrue(scanner.state.value.isPriorityActive)

        // Check timeout before expiration (1000ms < 2500ms)
        assertFalse(scanner.evaluateTimeout(currentTimeMs = triggerTime + 1000L))
        assertTrue(scanner.shouldDuckNormalAudio())

        // Check timeout after expiration (3000ms > 2500ms)
        assertTrue(scanner.evaluateTimeout(currentTimeMs = triggerTime + 3000L))
        assertFalse(scanner.shouldDuckNormalAudio())
        assertFalse(scanner.state.value.isPriorityActive)
        assertNull(scanner.state.value.activePrioritySender)
    }

    @Test
    fun testVolumeGainConstants() {
        assertEquals(0.20f, DualWatchScanner.DUCKED_VOLUME_GAIN, 0.001f)
        assertEquals(1.00f, DualWatchScanner.NORMAL_VOLUME_GAIN, 0.001f)
        assertEquals(0.20f, PttStreamManager.DUCKED_VOLUME, 0.001f)
        assertEquals(1.00f, PttStreamManager.NORMAL_VOLUME, 0.001f)
    }
}
