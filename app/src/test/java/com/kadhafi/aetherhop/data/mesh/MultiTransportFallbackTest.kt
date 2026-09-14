package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.TransportMedium
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MultiTransportFallbackTest {

    @Test
    fun testRegisterAndResolveTransportLink() {
        val router = MeshTransportRouter()
        val peerId = "peer_charlie_101"

        router.registerLink(peerId, TransportLinkType.WIFI_DIRECT, "192.168.49.12")
        val link = router.resolveOptimalLink(peerId)

        assertNotNull(link)
        assertEquals(peerId, link?.peerId)
        assertEquals(TransportLinkType.WIFI_DIRECT, link?.linkType)
        assertEquals("192.168.49.12", link?.ipAddress)
    }

    @Test
    fun testFailoverProgressionDirectToAwareToBle() {
        val router = MeshTransportRouter()
        val peerId = "peer_delta_202"

        // 1. Initial link is Wi-Fi Direct
        router.registerLink(peerId, TransportLinkType.WIFI_DIRECT, "192.168.49.20")
        val fallbackFromDirect = router.getFallbackTransport(peerId)
        assertEquals(TransportLinkType.WIFI_AWARE_NAN, fallbackFromDirect)

        // 2. Failover to Wi-Fi Aware NAN link
        router.registerLink(peerId, TransportLinkType.WIFI_AWARE_NAN, "fe80::1")
        val fallbackFromAware = router.getFallbackTransport(peerId)
        assertEquals(TransportLinkType.BLE_GATT, fallbackFromAware)

        // 3. Fallback when no active link exists defaults to Direct
        router.invalidateLink(peerId)
        assertNull(router.resolveOptimalLink(peerId))
        assertEquals(TransportLinkType.WIFI_DIRECT, router.getFallbackTransport(peerId))
    }

    @Test
    fun testMeshPacketTransportMediumTaggingAndSerialization() {
        val directPacket = MeshPacket(
            id = "pkt_direct_01",
            senderId = "node_a",
            targetId = "node_b",
            type = PacketType.CHAT,
            payload = "Direct payload",
            transport = TransportMedium.WIFI_DIRECT
        )
        val jsonDirect = Json.encodeToString(directPacket)
        val decodedDirect = Json.decodeFromString<MeshPacket>(jsonDirect)
        assertEquals(TransportMedium.WIFI_DIRECT, decodedDirect.transport)

        // Fallback packet tagged with WIFI_AWARE
        val awarePacket = directPacket.copy(
            id = "pkt_aware_02",
            transport = TransportMedium.WIFI_AWARE
        )
        val jsonAware = Json.encodeToString(awarePacket)
        val decodedAware = Json.decodeFromString<MeshPacket>(jsonAware)
        assertEquals(TransportMedium.WIFI_AWARE, decodedAware.transport)

        // BLE packet
        val blePacket = directPacket.copy(
            id = "pkt_ble_03",
            transport = TransportMedium.BLE
        )
        val jsonBle = Json.encodeToString(blePacket)
        val decodedBle = Json.decodeFromString<MeshPacket>(jsonBle)
        assertEquals(TransportMedium.BLE, decodedBle.transport)
    }
}
