package com.taomic.agent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HabitDao {

    @Insert
    suspend fun insertEvent(event: HabitEvent): Long

    @Query("SELECT * FROM habit_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<HabitEvent>

    @Query("SELECT * FROM habit_events WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getEventsSince(sinceTimestamp: Long): List<HabitEvent>

    @Query("SELECT skill_id, COUNT(*) as cnt FROM habit_events WHERE skill_id IS NOT NULL AND result = 'success' GROUP BY skill_id ORDER BY cnt DESC")
    suspend fun getSkillUsageStats(): List<SkillUsageCount>

    @Query("DELETE FROM habit_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteEventsBefore(beforeTimestamp: Long): Int

    @Query("DELETE FROM habit_events")
    suspend fun deleteAllEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(pref: Preference)

    @Query("SELECT * FROM preference ORDER BY confidence DESC")
    suspend fun getAllPreferences(): List<Preference>

    @Query("SELECT * FROM preference WHERE `key` = :key")
    suspend fun getPreference(key: String): Preference?

    @Query("DELETE FROM preference")
    suspend fun deleteAllPreferences()
}

data class SkillUsageCount(
    val skill_id: String,
    val cnt: Int,
)
