package com.kadhafi.aetherhop.core.util

import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.TransportMedium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PacketSignatureVerificationTest {

    @Test
    fun testAuthenticPacketSignatureVerificationSucceeds() {
        val keyPair = KeyExchangeManager.generateKeyPair()

        val packet = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "operator_bravo",
            targetId = "BROADCAST",
            type = PacketType.SOS_ALERT,
            payload = "{\"emergencyNote\":\"Ambush at grid 48M\"}",
            transport = TransportMedium.WIFI_DIRECT
        )

        val signedPacket = PacketSigner.signMeshPacket(packet, keyPair.private)
        assertNotNull(signedPacket.signatureBase64)
        assertTrue(signedPacket.signatureBase64.isNotBlank())

        val isValid = PacketSigner.verifyMeshPacket(signedPacket, keyPair.public)
        assertTrue("Signature verification must succeed for authentic untampered packet", isValid)
    }

    @Test
    fun testTamperedPayloadFailsVerification() {
        val keyPair = KeyExchangeManager.generateKeyPair()

        val originalPacket = MeshPacket(
            id = "packet_id_42",
            senderId = "legit_node",
            targetId = "#ops",
            type = PacketType.CHAT,
            payload = "Move to Extraction Point A"
        )
        val signedPacket = PacketSigner.signMeshPacket(originalPacket, keyPair.private)

        // Attacker tampers with the message payload
        val tamperedPacket = signedPacket.copy(payload = "Move to Extraction Point B (FORGED)")

        val isValid = PacketSigner.verifyMeshPacket(tamperedPacket, keyPair.public)
        assertFalse("Tampered payload must fail cryptographic signature check", isValid)
    }

    @Test
    fun testImpersonatedSenderPublicKeyFailsVerification() {
        val legitimateKeyPair = KeyExchangeManager.generateKeyPair()
        val attackerKeyPair = KeyExchangeManager.generateKeyPair()

        val packet = MeshPacket(
            id = "pkt_auth_99",
            senderId = "node_commander",
            targetId = "#general",
            type = PacketType.CHAT,
            payload = "Authorize operation alpha"
        )
        // Signed by legitimate commander
        val signedPacket = PacketSigner.signMeshPacket(packet, legitimateKeyPair.private)

        // Verifier checks against attacker's public key -> MUST FAIL
        val verifiedWithAttackerKey = PacketSigner.verifyMeshPacket(signedPacket, attackerKeyPair.public)
        assertFalse("Verification against wrong public key must fail", verifiedWithAttackerKey)
    }

    @Test
    fun testTamperedMetadataFailsVerification() {
        val keyPair = KeyExchangeManager.generateKeyPair()

        val packet = MeshPacket(
            id = "msg_001",
            senderId = "node_1",
            targetId = "node_2",
            type = PacketType.CHAT,
            payload = "Secret mission payload",
            timestamp = 1000L
        )
        val signedPacket = PacketSigner.signMeshPacket(packet, keyPair.private)

        // Tampering with senderId
        assertFalse(PacketSigner.verifyMeshPacket(signedPacket.copy(senderId = "node_impostor"), keyPair.public))

        // Tampering with packetId
        assertFalse(PacketSigner.verifyMeshPacket(signedPacket.copy(id = "msg_modified"), keyPair.public))

        // Tampering with timestamp
        assertFalse(PacketSigner.verifyMeshPacket(signedPacket.copy(timestamp = 2000L), keyPair.public))
    }

    @Test
    fun testBlankOrMalformedSignatureFailsGracefully() {
        val keyPair = KeyExchangeManager.generateKeyPair()

        val unsignedPacket = MeshPacket(
            id = "pkt_unsigned",
            senderId = "node_a",
            targetId = "node_b",
            type = PacketType.CHAT,
            payload = "Unsigned packet"
        )

        assertFalse(PacketSigner.verifyMeshPacket(unsignedPacket, keyPair.public))

        val malformedPacket = unsignedPacket.copy(signatureBase64 = "not_a_valid_base64_sig!@#$")
        assertFalse(PacketSigner.verifyMeshPacket(malformedPacket, keyPair.public))
    }
}
