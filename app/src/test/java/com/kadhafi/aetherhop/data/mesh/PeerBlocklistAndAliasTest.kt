package com.kadhafi.aetherhop.data.mesh

import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import com.kadhafi.aetherhop.domain.model.MeshPacket
import com.kadhafi.aetherhop.domain.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PeerBlocklistAndAliasTest {

    @Test
    fun testPeerAliasPriorityOverIdentityAndDefaultName() {
        val peerId = "node_delta_99"
        val rawDefaultName = "Android Device 99"
        val discoveredIdentityName = "Delta Operator"
        val customLocalAlias = "Alpha Scout Lead"

        val peerIdentities = mapOf(peerId to discoveredIdentityName)
        val peerAliases = mapOf(peerId to customLocalAlias)

        // Resolver logic: alias takes highest priority, then identity, then default
        fun resolvePeerName(id: String, defaultName: String): String {
            return peerAliases[id] ?: peerIdentities[id] ?: defaultName
        }

        // Case 1: Custom alias defined -> use alias
        assertEquals(customLocalAlias, resolvePeerName(peerId, rawDefaultName))

        // Case 2: No alias -> fallback to discovered identity
        val noAliasMap = emptyMap<String, String>()
        val nameFallbackIdentity = noAliasMap[peerId] ?: peerIdentities[peerId] ?: rawDefaultName
        assertEquals(discoveredIdentityName, nameFallbackIdentity)

        // Case 3: Neither alias nor identity -> fallback to default
        val emptyIdentities = emptyMap<String, String>()
        val nameFallbackDefault = noAliasMap[peerId] ?: emptyIdentities[peerId] ?: rawDefaultName
        assertEquals(rawDefaultName, nameFallbackDefault)
    }

    @Test
    fun testBlockedNodePacketDropping() {
        val blockedPeers = setOf("rogue_node_1", "bad_actor_2")

        fun shouldProcessPacket(packet: MeshPacket): Boolean {
            if (blockedPeers.contains(packet.senderId) || blockedPeers.contains(packet.targetId)) {
                return false // Drop
            }
            return true
        }

        val legitimatePacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "team_alpha",
            targetId = "team_bravo",
            type = PacketType.CHAT,
            payload = "Clear to proceed"
        )
        assertTrue("Legitimate packet must be processed", shouldProcessPacket(legitimatePacket))

        val blockedSenderPacket = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "rogue_node_1",
            targetId = "team_bravo",
            type = PacketType.CHAT,
            payload = "Malicious injection"
        )
        assertFalse("Packet from blocked sender must be dropped", shouldProcessPacket(blockedSenderPacket))

        val packetToBlockedTarget = MeshPacket(
            id = UUID.randomUUID().toString(),
            senderId = "team_alpha",
            targetId = "bad_actor_2",
            type = PacketType.CHAT,
            payload = "Do not route to blocked"
        )
        assertFalse("Packet to blocked target must be dropped", shouldProcessPacket(packetToBlockedTarget))
    }

    @Test
    fun testPeerEntityBlockedAndAliasDefaults() {
        val peer = PeerEntity(
            id = "node_test_01",
            name = "Test Node",
            address = "192.168.49.2",
            lastSeenTimestamp = 1000L
        )
        assertFalse(peer.isBlocked)
        assertEquals("", peer.customAlias)

        val updated = peer.copy(
            customAlias = "Tactical Echo",
            isBlocked = true
        )
        assertTrue(updated.isBlocked)
        assertEquals("Tactical Echo", updated.customAlias)
    }
}
