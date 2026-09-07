package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReactionPayload(
    val messageId: String,
    val emoji: String,
    val senderId: String
)
