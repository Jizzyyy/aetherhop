package com.kadhafi.aetherhop.data.dtn

import com.kadhafi.aetherhop.data.local.entity.OutboxBundleEntity
import com.kadhafi.aetherhop.domain.model.PacketType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxBundleLifecycleTest {

    @Test
    fun testBundleEntityDefaultValues() {
        val now = System.currentTimeMillis()
        val entity = OutboxBundleEntity(
            bundleId = "bundle_lifecycle_1",
            targetPeerId = "node_x",
            packetType = PacketType.CHAT.name,
            payload = "Payload data"
        )

        assertEquals("bundle_lifecycle_1", entity.bundleId)
        assertEquals("node_x", entity.targetPeerId)
        assertEquals(PacketType.CHAT.name, entity.packetType)
        assertEquals("Payload data", entity.payload)
        assertEquals(0, entity.retryCount)
        assertTrue(entity.timestamp >= now)
        assertTrue(entity.expiresAt > entity.timestamp)
        // Default TTL is 24 hours
        assertEquals(24 * 60 * 60 * 1000.0, (entity.expiresAt - entity.timestamp).toDouble(), 1000.0)
    }

    @Test
    fun testFifoOrderingAndPeerFiltering() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        // Insert multiple bundles across different peers and timestamps
        buffer.enqueueBundle("b1", "peer_a", PacketType.CHAT, "msg 1", ttlMs = 10000)
        Thread.sleep(10)
        buffer.enqueueBundle("b2", "peer_b", PacketType.CHAT, "msg 2", ttlMs = 10000)
        Thread.sleep(10)
        buffer.enqueueBundle("b3", "peer_a", PacketType.CHAT, "msg 3", ttlMs = 10000)

        val pendingA = buffer.getPendingBundles("peer_a")
        assertEquals(2, pendingA.size)
        assertEquals("b1", pendingA[0].bundleId)
        assertEquals("b3", pendingA[1].bundleId)

        val pendingB = buffer.getPendingBundles("peer_b")
        assertEquals(1, pendingB.size)
        assertEquals("b2", pendingB[0].bundleId)

        val allValid = buffer.getAllPendingBundles()
        assertEquals(3, allValid.size)
    }

    @Test
    fun testGradualTtlEviction() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        val currentTime = 1000000L

        // Bundle 1: expires at 1000500L (in the past relative to check at 1001000L)
        fakeDao.insertBundle(
            OutboxBundleEntity(
                bundleId = "exp_1",
                targetPeerId = "peer_z",
                packetType = "CHAT",
                payload = "Old",
                timestamp = 900000L,
                expiresAt = 1000500L
            )
        )

        // Bundle 2: expires at 1005000L (valid)
        fakeDao.insertBundle(
            OutboxBundleEntity(
                bundleId = "valid_2",
                targetPeerId = "peer_z",
                packetType = "CHAT",
                payload = "Fresh",
                timestamp = 900000L,
                expiresAt = 1005000L
            )
        )

        val validBeforePurge = fakeDao.getBundlesForPeer("peer_z", currentTime = 1001000L)
        assertEquals(1, validBeforePurge.size)
        assertEquals("valid_2", validBeforePurge[0].bundleId)

        // Run purge at 1001000L
        fakeDao.purgeExpiredBundles(currentTime = 1001000L)
        assertEquals(1, fakeDao.storage.size)
        assertFalse(fakeDao.storage.containsKey("exp_1"))
        assertTrue(fakeDao.storage.containsKey("valid_2"))
    }
}
