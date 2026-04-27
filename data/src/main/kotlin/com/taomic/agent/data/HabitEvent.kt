package com.taomic.agent.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户交互习惯事件。每次 Skill 执行结束后写入一条。
 *
 * V0.5 用于偏好抽取和 Skill 使用统计。
 */
@Entity(tableName = "habit_events")
data class HabitEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "intent_text") val intentText: String,
    @ColumnInfo(name = "skill_id") val skillId: String?,
    val result: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "token_count") val tokenCount: Int = 0,
    @ColumnInfo(name = "screen_summary") val screenSummary: String? = null,
)

/**
 * 用户偏好键值对。由偏好抽取定时任务写入。
 */
@Entity(tableName = "preference")
data class Preference(
    @PrimaryKey val key: String,
    val value: String,
    val confidence: Float,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
