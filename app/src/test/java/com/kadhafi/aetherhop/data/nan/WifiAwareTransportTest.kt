package com.kadhafi.aetherhop.data.nan

import com.kadhafi.aetherhop.data.mesh.MeshTransportRouter
import com.kadhafi.aetherhop.data.mesh.TransportLinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WifiAwareTransportTest {

    @Test
    fun testMeshTransportRouterLinkRegistrationAndFallback() {
        val router = MeshTransportRouter()
        router.registerLink("peer_1", TransportLinkType.WIFI_DIRECT, "192.168.49.2")

        val link = router.resolveOptimalLink("peer_1")
        assertNotNull(link)
        assertEquals(TransportLinkType.WIFI_DIRECT, link?.linkType)
        assertEquals("192.168.49.2", link?.ipAddress)

        val fallback = router.getFallbackTransport("peer_1")
        assertEquals(TransportLinkType.WIFI_AWARE_NAN, fallback)
    }
}
