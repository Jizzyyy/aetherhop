package com.kadhafi.aetherhop.core.util

import com.kadhafi.aetherhop.domain.model.MeshPacket
import java.security.PrivateKey
import java.security.PublicKey

object PacketSigner {

    fun createSigningPayload(
        packetId: String,
        senderId: String,
        targetId: String,
        typeName: String,
        timestamp: Long,
        payload: String
    ): ByteArray {
        val canonical = "$packetId|$senderId|$targetId|$typeName|$timestamp|$payload"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    fun signMeshPacket(packet: MeshPacket, privateKey: PrivateKey): MeshPacket {
        val data = createSigningPayload(
            packetId = packet.id,
            senderId = packet.senderId,
            targetId = packet.targetId,
            typeName = packet.type.name,
            timestamp = packet.timestamp,
            payload = packet.payload
        )
        val signature = CryptoManager.signData(data, privateKey)
        return packet.copy(signatureBase64 = signature)
    }

    fun verifyMeshPacket(packet: MeshPacket, senderPublicKey: PublicKey): Boolean {
        if (packet.signatureBase64.isBlank()) return false
        val data = createSigningPayload(
            packetId = packet.id,
            senderId = packet.senderId,
            targetId = packet.targetId,
            typeName = packet.type.name,
            timestamp = packet.timestamp,
            payload = packet.payload
        )
        return CryptoManager.verifySignature(data, packet.signatureBase64, senderPublicKey)
    }
}
