package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceNotePayload(
    val voiceId: String,
    val durationMs: Long,
    val audioBase64: String
)
