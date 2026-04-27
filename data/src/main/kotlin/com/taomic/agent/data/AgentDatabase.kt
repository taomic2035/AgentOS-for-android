package com.taomic.agent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HabitEvent::class, Preference::class],
    version = 1,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var instance: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "agent_db",
                ).build().also { instance = it }
            }
    }
}
