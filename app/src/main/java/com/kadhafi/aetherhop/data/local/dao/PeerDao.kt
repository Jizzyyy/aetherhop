package com.kadhafi.aetherhop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeenTimestamp DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: PeerEntity)

    @Query("SELECT * FROM peers WHERE id = :id LIMIT 1")
    suspend fun getPeerById(id: String): PeerEntity?

    @Query("UPDATE peers SET isTrusted = :trusted, fingerprint = :fingerprint WHERE id = :id")
    suspend fun updateTrustStatus(id: String, trusted: Boolean, fingerprint: String)
}
