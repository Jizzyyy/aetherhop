package com.kadhafi.aetherhop.presentation.chat

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kadhafi.aetherhop.domain.model.ChatMessage
import com.kadhafi.aetherhop.domain.model.MessageStatus
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testChatScreenPeerNameAndMessageBubbleRenders() {
        val testMessages = listOf(
            ChatMessage(
                id = "m1",
                senderId = "p1",
                senderName = "Bob",
                text = "Emergency Mesh Test Message",
                isMine = false,
                status = MessageStatus.SENT
            )
        )

        composeTestRule.setContent {
            ChatScreen(
                peerName = "Bob",
                messages = testMessages,
                onSendMessage = {},
                onBackClick = {}
            )
        }

        composeTestRule.onNodeWithText("Bob").assertExists()
        composeTestRule.onNodeWithText("Emergency Mesh Test Message").assertExists()
    }
}
