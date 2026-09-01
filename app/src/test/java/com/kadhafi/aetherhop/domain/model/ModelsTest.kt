package com.kadhafi.aetherhop.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelsTest {

    @Test
    fun testPeerNodeModel() {
        val node = PeerNode(
            id = "node_123",
            name = "Test Node",
            address = "192.168.49.1",
            rssi = -65,
            distanceMeters = 3.5
        )

        assertNotNull(node)
        assertEquals("node_123", node.id)
        assertEquals("Test Node", node.name)
        assertEquals("192.168.49.1", node.address)
        assertEquals(-65, node.rssi)
    }

    @Test
    fun testChatMessageModel() {
        val msg = ChatMessage(
            id = "msg_123",
            senderId = "user_a",
            senderName = "Alice",
            text = "Hello!",
            isMine = true,
            status = MessageStatus.SENT
        )

        assertEquals("msg_123", msg.id)
        assertEquals("Alice", msg.senderName)
        assertEquals(MessageStatus.SENT, msg.status)
    }
}
