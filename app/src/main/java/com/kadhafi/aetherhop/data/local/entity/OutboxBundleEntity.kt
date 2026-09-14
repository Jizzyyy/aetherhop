package com.kadhafi.aetherhop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_bundles")
data class OutboxBundleEntity(
    @PrimaryKey
    val bundleId: String,
    val targetPeerId: String,
    val packetType: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L), // 24 hours TTL
    val retryCount: Int = 0
)
