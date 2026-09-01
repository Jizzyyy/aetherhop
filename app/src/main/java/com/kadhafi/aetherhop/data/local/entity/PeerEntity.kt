package com.kadhafi.aetherhop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val lastSeenTimestamp: Long
)
