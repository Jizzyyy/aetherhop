package com.kadhafi.aetherhop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TacticalWaypointDao {
    @Query("SELECT * FROM waypoints ORDER BY createdTimestamp DESC")
    fun getAllWaypoints(): Flow<List<TacticalWaypointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: TacticalWaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypoint(id: String)
}
