package com.kadhafi.aetherhop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kadhafi.aetherhop.data.local.entity.OutboxBundleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBundle(bundle: OutboxBundleEntity)

    @Query("SELECT * FROM outbox_bundles WHERE targetPeerId = :targetPeerId AND expiresAt > :currentTime ORDER BY timestamp ASC")
    suspend fun getBundlesForPeer(targetPeerId: String, currentTime: Long = System.currentTimeMillis()): List<OutboxBundleEntity>

    @Query("SELECT * FROM outbox_bundles WHERE expiresAt > :currentTime ORDER BY timestamp ASC")
    suspend fun getAllValidBundles(currentTime: Long = System.currentTimeMillis()): List<OutboxBundleEntity>

    @Query("SELECT * FROM outbox_bundles ORDER BY timestamp ASC")
    fun observeAllBundles(): Flow<List<OutboxBundleEntity>>

    @Query("DELETE FROM outbox_bundles WHERE bundleId = :bundleId")
    suspend fun deleteBundle(bundleId: String)

    @Query("DELETE FROM outbox_bundles WHERE expiresAt <= :currentTime")
    suspend fun purgeExpiredBundles(currentTime: Long = System.currentTimeMillis())

    @Query("UPDATE outbox_bundles SET retryCount = retryCount + 1 WHERE bundleId = :bundleId")
    suspend fun incrementRetryCount(bundleId: String)
}
