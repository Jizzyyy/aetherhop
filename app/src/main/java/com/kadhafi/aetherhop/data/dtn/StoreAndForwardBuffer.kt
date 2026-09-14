package com.kadhafi.aetherhop.data.dtn

import com.kadhafi.aetherhop.data.local.dao.OutboxDao
import com.kadhafi.aetherhop.data.local.entity.OutboxBundleEntity
import com.kadhafi.aetherhop.domain.model.PacketType
import kotlinx.coroutines.flow.Flow

class StoreAndForwardBuffer(private val outboxDao: OutboxDao) {

    suspend fun enqueueBundle(
        bundleId: String,
        targetPeerId: String,
        packetType: PacketType,
        payload: String,
        ttlMs: Long = 24 * 60 * 60 * 1000L
    ) {
        val bundle = OutboxBundleEntity(
            bundleId = bundleId,
            targetPeerId = targetPeerId,
            packetType = packetType.name,
            payload = payload,
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + ttlMs,
            retryCount = 0
        )
        outboxDao.insertBundle(bundle)
    }

    suspend fun getPendingBundles(targetPeerId: String): List<OutboxBundleEntity> {
        return outboxDao.getBundlesForPeer(targetPeerId)
    }

    suspend fun getAllPendingBundles(): List<OutboxBundleEntity> {
        return outboxDao.getAllValidBundles()
    }

    fun observeAllBundles(): Flow<List<OutboxBundleEntity>> {
        return outboxDao.observeAllBundles()
    }

    suspend fun removeBundle(bundleId: String) {
        outboxDao.deleteBundle(bundleId)
    }

    suspend fun recordRetry(bundleId: String) {
        outboxDao.incrementRetryCount(bundleId)
    }

    suspend fun purgeExpired() {
        outboxDao.purgeExpiredBundles()
    }
}
