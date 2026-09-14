package com.kadhafi.aetherhop.presentation.diagnostics

import com.kadhafi.aetherhop.data.mesh.LinkQualityCalculator
import com.kadhafi.aetherhop.data.mesh.RoutingTable
import com.kadhafi.aetherhop.data.mesh.TelemetryCollector
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MeshDiagnosticsTelemetryTest {

    @Test
    fun testRoutingTableRoutesFlowEmission() {
        val table = RoutingTable()
        assertEquals(0, table.routesFlow.value.size)

        // Adding route emits into routesFlow
        table.updateRoute("target_node_1", "192.168.49.5", hops = 1)
        assertEquals(1, table.routesFlow.value.size)
        assertEquals("target_node_1", table.routesFlow.value[0].destinationId)
        assertEquals("192.168.49.5", table.routesFlow.value[0].nextHopIp)

        // Adding second route
        table.updateRoute("target_node_2", "192.168.49.6", hops = 2)
        assertEquals(2, table.routesFlow.value.size)

        // Prune routes with negative max age to force eviction
        table.removeStaleRoutes(maxAgeMs = -1)
        assertEquals(0, table.routesFlow.value.size)
    }

    @Test
    fun testTelemetryCollectorRttRecording() {
        val peerId = "diag_test_peer"
        TelemetryCollector.recordRtt(peerId, 45L)
        TelemetryCollector.recordRtt(peerId, 55L)

        val telemetry = TelemetryCollector.getTelemetryForPeer(peerId)
        assertNotNull(telemetry)
        assertEquals(55L, telemetry.rttMs)
    }

    @Test
    fun testLinkQualityCalculatorRatings() {
        // High RSSI, low RTT, zero loss -> Excellent (score >= 80)
        val excellentScore = LinkQualityCalculator.calculateLqi(rssi = -55, rttMs = 20, packetLossPercentage = 0f)
        assertEquals("EXCELLENT", LinkQualityCalculator.getLqiRating(excellentScore))

        // Moderate RSSI, medium RTT -> Good (60 <= score < 80)
        val goodScore = LinkQualityCalculator.calculateLqi(rssi = -80, rttMs = 180, packetLossPercentage = 10f)
        assertEquals("GOOD", LinkQualityCalculator.getLqiRating(goodScore))

        // Poor RSSI, high RTT, high loss -> Poor (score < 40)
        val poorScore = LinkQualityCalculator.calculateLqi(rssi = -95, rttMs = 800, packetLossPercentage = 30f)
        assertEquals("POOR", LinkQualityCalculator.getLqiRating(poorScore))
    }

    @Test
    fun testPingPacketPayloadTimestampIntegrity() {
        val timestamp = 1788500000000L
        val pingPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "self_node",
            targetId = "peer_target",
            type = PacketType.PING,
            payload = timestamp.toString()
        )

        val payloadTimestamp = pingPacket.payload.toLongOrNull()
        assertNotNull(payloadTimestamp)
        assertEquals(timestamp, payloadTimestamp)

        val arrivalTime = timestamp + 35L
        val calculatedRtt = arrivalTime - (payloadTimestamp ?: 0L)
        assertEquals(35L, calculatedRtt)
    }
}
