package com.kadhafi.aetherhop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kadhafi.aetherhop.data.local.entity.ChannelMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelMessage(message: ChannelMessageEntity)

    @Query("SELECT * FROM channel_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(channelId: String): Flow<List<ChannelMessageEntity>>

    @Query("SELECT DISTINCT channelId FROM channel_messages ORDER BY channelId ASC")
    fun getAllChannelIds(): Flow<List<String>>

    @Query("DELETE FROM channel_messages WHERE channelId = :channelId")
    suspend fun clearChannel(channelId: String)
}
