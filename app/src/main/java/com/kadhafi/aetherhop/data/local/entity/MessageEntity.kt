package com.kadhafi.aetherhop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kadhafi.aetherhop.domain.model.MessageStatus

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val status: MessageStatus
)
