package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.core.util.CryptoManager
import com.kadhafi.aetherhop.core.util.KeyExchangeManager
import com.kadhafi.aetherhop.core.util.PacketSigner
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import com.kadhafi.aetherhop.domain.model.SosPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RogueNodeSpoofingMitigationTest {

    @Test
    fun testRogueNodeSignatureSpoofingMitigation() {
        val commanderKeyPair = KeyExchangeManager.generateKeyPair()
        val attackerKeyPair = KeyExchangeManager.generateKeyPair()
        val commanderId = "node_commander_alpha"
        val attackerId = "rogue_adversary_node"

        // Step 1: Legitimate Commander sends authentic emergency SOS broadcast
        val authenticSos = SosPayload(
            senderId = commanderId,
            senderName = "Commander Alpha",
            emergencyNote = "Authentic SOS: Convoy under fire"
        )
        val authenticPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = commanderId,
            targetId = "BROADCAST",
            type = PacketType.SOS_ALERT,
            payload = Json.encodeToString(authenticSos)
        )
        val signedAuthenticPacket = PacketSigner.signMeshPacket(authenticPacket, commanderKeyPair.private)

        // Verifying authentic packet against commander's registered public key
        val isAuthenticValid = PacketSigner.verifyMeshPacket(signedAuthenticPacket, commanderKeyPair.public)
        assertTrue("Authentic commander packet signature must be valid", isAuthenticValid)

        // Step 2: Adversary intercepts and injects a fake SOS pretending to be Commander Alpha
        val spoofedSos = SosPayload(
            senderId = commanderId, // Spoofing senderId
            senderName = "Commander Alpha",
            emergencyNote = "SPOOFED: Fallback to false location"
        )
        val spoofedPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = commanderId,
            targetId = "BROADCAST",
            type = PacketType.SOS_ALERT,
            payload = Json.encodeToString(spoofedSos)
        )
        // Attacker attempts to sign using attacker's private key
        val signedSpoofedPacket = PacketSigner.signMeshPacket(spoofedPacket, attackerKeyPair.private)

        // Verifying spoofed packet against commander's genuine public key
        val isSpoofedValid = PacketSigner.verifyMeshPacket(signedSpoofedPacket, commanderKeyPair.public)
        assertFalse("Spoofed packet signed by adversary must FAIL verification against commander public key", isSpoofedValid)

        // Step 3: Rogue node sends packet from its own identity; node is on blocklist
        val blocklist = setOf(attackerId)
        val rogueDirectPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = attackerId,
            targetId = "#general",
            type = PacketType.CHAT,
            payload = "Disruptive spam"
        )
        val isBlocked = blocklist.contains(rogueDirectPacket.senderId)
        assertTrue("Rogue packet must be identified as blocked", isBlocked)
    }
}
