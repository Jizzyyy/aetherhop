package com.kadhafi.aetherhop.data.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCollectorTest {

    @Test
    fun testRssiHistoryRingBufferLimitsToFifteenSamples() {
        val peerId = "peer_node_test_1"
        for (i in 1..25) {
            TelemetryCollector.recordRssi(peerId, -50 - i)
        }

        val telemetry = TelemetryCollector.getTelemetryForPeer(peerId)
        assertEquals(15, telemetry.rssiHistory.size)
        // Last recorded should be -75
        assertEquals(-75, telemetry.rssiHistory.last())
    }

    @Test
    fun testPacketLossCalculation() {
        val peerId = "peer_node_loss_test"
        for (i in 1..10) {
            TelemetryCollector.incrementSent(peerId)
        }
        for (i in 1..8) {
            TelemetryCollector.incrementReceived(peerId)
        }

        val telemetry = TelemetryCollector.getTelemetryForPeer(peerId)
        assertEquals(10L, telemetry.totalPacketsSent)
        assertEquals(8L, telemetry.totalPacketsReceived)
        assertEquals(20.0f, telemetry.packetLossPercentage, 0.01f)
    }
}
