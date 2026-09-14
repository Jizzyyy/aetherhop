package com.kadhafi.aetherhop.data.dtn

import com.kadhafi.aetherhop.data.local.dao.OutboxDao
import com.kadhafi.aetherhop.data.local.entity.OutboxBundleEntity
import com.kadhafi.aetherhop.domain.model.DeliveryReceiptPayload
import com.kadhafi.aetherhop.domain.model.PacketType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class FakeOutboxDao : OutboxDao {
    val storage = ConcurrentHashMap<String, OutboxBundleEntity>()

    override suspend fun insertBundle(bundle: OutboxBundleEntity) {
        storage[bundle.bundleId] = bundle
    }

    override suspend fun getBundlesForPeer(targetPeerId: String, currentTime: Long): List<OutboxBundleEntity> {
        return storage.values
            .filter { it.targetPeerId == targetPeerId && it.expiresAt > currentTime }
            .sortedBy { it.timestamp }
    }

    override suspend fun getAllValidBundles(currentTime: Long): List<OutboxBundleEntity> {
        return storage.values
            .filter { it.expiresAt > currentTime }
            .sortedBy { it.timestamp }
    }

    override fun observeAllBundles(): Flow<List<OutboxBundleEntity>> {
        return flowOf(storage.values.toList())
    }

    override suspend fun deleteBundle(bundleId: String) {
        storage.remove(bundleId)
    }

    override suspend fun purgeExpiredBundles(currentTime: Long) {
        storage.entries.removeIf { it.value.expiresAt <= currentTime }
    }

    override suspend fun incrementRetryCount(bundleId: String) {
        storage[bundleId]?.let {
            storage[bundleId] = it.copy(retryCount = it.retryCount + 1)
        }
    }
}

class StoreAndForwardTest {

    @Test
    fun testEnqueueAndRetrievePendingBundles() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        buffer.enqueueBundle(
            bundleId = "bundle_001",
            targetPeerId = "peer_alpha",
            packetType = PacketType.CHAT,
            payload = "Halo Alpha dari Outbox",
            ttlMs = 3600000L
        )

        val pendingAlpha = buffer.getPendingBundles("peer_alpha")
        assertEquals(1, pendingAlpha.size)
        assertEquals("bundle_001", pendingAlpha[0].bundleId)
        assertEquals("peer_alpha", pendingAlpha[0].targetPeerId)
        assertEquals(PacketType.CHAT.name, pendingAlpha[0].packetType)
        assertEquals("Halo Alpha dari Outbox", pendingAlpha[0].payload)
        assertEquals(0, pendingAlpha[0].retryCount)

        // Peer Beta should have no pending bundles
        val pendingBeta = buffer.getPendingBundles("peer_beta")
        assertEquals(0, pendingBeta.size)
    }

    @Test
    fun testRetryCountIncrement() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        buffer.enqueueBundle(
            bundleId = "bundle_retry",
            targetPeerId = "peer_gamma",
            packetType = PacketType.CHAT,
            payload = "Test retry"
        )

        buffer.recordRetry("bundle_retry")
        buffer.recordRetry("bundle_retry")

        val bundles = buffer.getPendingBundles("peer_gamma")
        assertEquals(1, bundles.size)
        assertEquals(2, bundles[0].retryCount)
    }

    @Test
    fun testBundleRemovalOnDelivery() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        buffer.enqueueBundle(
            bundleId = "bundle_delivered",
            targetPeerId = "peer_delta",
            packetType = PacketType.CHAT,
            payload = "Pesan berhasil terkirim"
        )
        assertEquals(1, buffer.getPendingBundles("peer_delta").size)

        buffer.removeBundle("bundle_delivered")
        assertEquals(0, buffer.getPendingBundles("peer_delta").size)
    }

    @Test
    fun testTtlExpirationAndPurge() = runBlocking {
        val fakeDao = FakeOutboxDao()
        val buffer = StoreAndForwardBuffer(fakeDao)

        // Negative TTL means it is already expired
        buffer.enqueueBundle(
            bundleId = "bundle_expired",
            targetPeerId = "peer_echo",
            packetType = PacketType.CHAT,
            payload = "Pesan kadaluarsa",
            ttlMs = -1000L
        )

        // Bundle is already expired so getPendingBundles returns empty
        val pending = buffer.getPendingBundles("peer_echo")
        assertEquals(0, pending.size)

        // Purge removes it completely from database
        buffer.purgeExpired()
        assertEquals(0, fakeDao.storage.size)
    }

    @Test
    fun testDeliveryReceiptSerializationAndDeserialization() {
        val receipt = DeliveryReceiptPayload(
            messageId = "msg_uuid_99",
            senderId = "node_sender",
            receiverId = "node_receiver",
            deliveredTimestamp = 1788500000000L
        )

        val json = Json.encodeToString(receipt)
        val decoded = Json.decodeFromString<DeliveryReceiptPayload>(json)

        assertEquals("msg_uuid_99", decoded.messageId)
        assertEquals("node_sender", decoded.senderId)
        assertEquals("node_receiver", decoded.receiverId)
        assertEquals(1788500000000L, decoded.deliveredTimestamp)
    }
}
