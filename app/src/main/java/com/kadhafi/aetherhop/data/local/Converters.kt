package com.kadhafi.aetherhop.data.local

import androidx.room.TypeConverter
import com.kadhafi.aetherhop.domain.model.MessageStatus
import com.kadhafi.aetherhop.domain.model.PacketType

class Converters {
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toMessageStatus(value: String): MessageStatus = try {
        enumValueOf<MessageStatus>(value)
    } catch (_: Exception) {
        MessageStatus.SENT
    }

    @TypeConverter
    fun fromPacketType(type: PacketType): String = type.name

    @TypeConverter
    fun toPacketType(value: String): PacketType = try {
        enumValueOf<PacketType>(value)
    } catch (_: Exception) {
        PacketType.CHAT
    }
}
