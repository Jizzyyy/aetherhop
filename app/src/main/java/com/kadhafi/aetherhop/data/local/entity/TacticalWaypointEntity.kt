package com.kadhafi.aetherhop.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class TacticalWaypointEntity(
    @PrimaryKey val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "CAMP", // CAMP, HAZARD, MEDICAL, RENDEZVOUS
    val createdTimestamp: Long = System.currentTimeMillis()
)
