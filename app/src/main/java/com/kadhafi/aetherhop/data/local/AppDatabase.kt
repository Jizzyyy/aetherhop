package com.kadhafi.aetherhop.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kadhafi.aetherhop.data.local.dao.ConversationDao
import com.kadhafi.aetherhop.data.local.dao.MessageDao
import com.kadhafi.aetherhop.data.local.dao.PeerDao
import com.kadhafi.aetherhop.data.local.dao.TacticalWaypointDao
import com.kadhafi.aetherhop.data.local.entity.ConversationEntity
import com.kadhafi.aetherhop.data.local.entity.MessageEntity
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity

@Database(
    entities = [MessageEntity::class, PeerEntity::class, ConversationEntity::class, TacticalWaypointEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun conversationDao(): ConversationDao
    abstract fun tacticalWaypointDao(): TacticalWaypointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aetherhop_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
