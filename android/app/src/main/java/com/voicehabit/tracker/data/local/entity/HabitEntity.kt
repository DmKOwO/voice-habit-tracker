package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String = "Routine",
    val displayType: String = "DAILY_CHECK", // STREAKS, GRID, DAILY_CHECK, BAR_GRAPH, MINIMAL
    val colorHex: String = "#FF6B35",
    val quote: String = "",
    val targetValue: Double = 1.0,
    val unit: String? = null,
    val frequency: String = "DAILY",
    val streak: Int = 0,
    /** G18/G9/G19: лучший стрик, закреп, минуты напоминания (null = нет). */
    val bestStreak: Int = 0,
    val pinned: Int = 0,
    val reminderMin: Int? = null,
    /** F7: 1 = в архиве. F4: дни недели "1,2,3,4,5,6,7" (1=пн). Теги через запятую. deletedAt = корзина. */
    val archived: Int = 0,
    val scheduleDays: String = "1,2,3,4,5,6,7",
    val tags: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
