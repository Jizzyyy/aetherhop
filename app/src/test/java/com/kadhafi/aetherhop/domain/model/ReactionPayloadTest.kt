package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReactionPayloadTest {

    @Test
    fun testReactionPayloadSerializationRoundtrip() {
        val payload = ReactionPayload(
            messageId = "msg_001_abc",
            emoji = "🚨",
            senderId = "node_rescue_1"
        )

        val jsonStr = Json.encodeToString(payload)
        val decoded = Json.decodeFromString<ReactionPayload>(jsonStr)

        assertNotNull(decoded)
        assertEquals(payload.messageId, decoded.messageId)
        assertEquals(payload.emoji, decoded.emoji)
        assertEquals(payload.senderId, decoded.senderId)
    }
}
