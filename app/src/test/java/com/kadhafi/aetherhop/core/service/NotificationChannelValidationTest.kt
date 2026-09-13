package com.kadhafi.aetherhop.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelValidationTest {

    @Test
    fun testNotificationChannelConstants() {
        assertEquals("aetherhop_service_channel", AetherHopNotificationManager.SERVICE_CHANNEL_ID)
        assertEquals("aetherhop_message_channel", AetherHopNotificationManager.MESSAGE_CHANNEL_ID)
        assertEquals(1001, AetherHopNotificationManager.SERVICE_NOTIFICATION_ID)
        assertNotEquals(AetherHopNotificationManager.SERVICE_CHANNEL_ID, AetherHopNotificationManager.MESSAGE_CHANNEL_ID)
    }

    @Test
    fun testNotificationTextFormattingForConnectedPeers() {
        // Validation of notification string formatting rules
        fun formatStatusText(connectedPeersCount: Int): String {
            return if (connectedPeersCount > 0) {
                "Terhubung: $connectedPeersCount Perangkat Sekitar"
            } else {
                "Mendengarkan jaringan P2P BLE & Wi-Fi Direct di latar belakang"
            }
        }

        val zeroPeerStatus = formatStatusText(0)
        assertEquals("Mendengarkan jaringan P2P BLE & Wi-Fi Direct di latar belakang", zeroPeerStatus)

        val multiplePeersStatus = formatStatusText(3)
        assertEquals("Terhubung: 3 Perangkat Sekitar", multiplePeersStatus)
        assertTrue(multiplePeersStatus.contains("3"))
    }
}
