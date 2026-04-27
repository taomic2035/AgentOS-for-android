package com.taomic.agent.data

import android.util.Log

/**
 * 习惯/偏好数据仓库。封装 Room DAO 操作，供上层模块调用。
 */
class HabitRepository(private val dao: HabitDao) {

    suspend fun recordEvent(event: HabitEvent): Long {
        val id = dao.insertEvent(event)
        Log.d(TAG, "recorded habit event: id=$id skill=${event.skillId} result=${event.result}")
        return id
    }

    suspend fun getRecentEvents(limit: Int = 100): List<HabitEvent> =
        dao.getRecentEvents(limit)

    suspend fun getEventsSince(sinceTimestamp: Long): List<HabitEvent> =
        dao.getEventsSince(sinceTimestamp)

    suspend fun getSkillUsageStats(): List<SkillUsageCount> =
        dao.getSkillUsageStats()

    suspend fun upsertPreference(key: String, value: String, confidence: Float) {
        dao.upsertPreference(Preference(key, value, confidence, System.currentTimeMillis()))
        Log.d(TAG, "upserted preference: $key=$value conf=$confidence")
    }

    suspend fun getAllPreferences(): List<Preference> =
        dao.getAllPreferences()

    suspend fun getPreference(key: String): Preference? =
        dao.getPreference(key)

    suspend fun clearAll() {
        dao.deleteAllEvents()
        dao.deleteAllPreferences()
        Log.i(TAG, "cleared all habit events and preferences")
    }

    companion object {
        const val TAG: String = "HabitRepository"
    }
}
