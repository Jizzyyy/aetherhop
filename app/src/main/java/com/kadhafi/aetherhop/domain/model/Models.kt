package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PeerNode(
    val id: String,
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val distanceMeters: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class PacketType {
    HANDSHAKE,
    CHAT,
    FILE_CHUNK,
    SOS_ALERT,
    ACK,
    VOICE_NOTE,
    PING,
    PONG
}

@Serializable
data class MeshPacket(
    val id: String,
    val senderId: String,
    val targetId: String,
    val type: PacketType,
    val payload: String,
    val ttl: Int = 5,
    val timestamp: Long = System.currentTimeMillis()
)
