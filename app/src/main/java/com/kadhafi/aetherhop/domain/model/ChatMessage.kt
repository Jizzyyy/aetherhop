package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    PENDING,
    SENT,
    FAILED
}

@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false,
    val status: MessageStatus = MessageStatus.SENT
)

@Serializable
data class FileChunk(
    val fileId: String,
    val fileName: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val dataBase64: String
)
