package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AudioFramePayload(
    val pttSessionId: String,
    val sequenceIndex: Long,
    val frameBase64: String
)
