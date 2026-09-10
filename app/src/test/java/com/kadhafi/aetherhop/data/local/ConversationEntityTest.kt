package com.kadhafi.aetherhop.data.local

import com.kadhafi.aetherhop.data.local.entity.ConversationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConversationEntityTest {

    @Test
    fun testConversationEntityCreationAndProperties() {
        val conv = ConversationEntity(
            conversationId = "#medis",
            title = "Channel Medis",
            isChannel = true,
            lastMessageText = "Peralatan medis dibutuhkan segera",
            lastMessageTimestamp = 1725800000L,
            unreadCount = 3
        )

        assertNotNull(conv)
        assertEquals("#medis", conv.conversationId)
        assertEquals("Channel Medis", conv.title)
        assertEquals(true, conv.isChannel)
        assertEquals(3, conv.unreadCount)
        assertEquals("Peralatan medis dibutuhkan segera", conv.lastMessageText)
    }
}
