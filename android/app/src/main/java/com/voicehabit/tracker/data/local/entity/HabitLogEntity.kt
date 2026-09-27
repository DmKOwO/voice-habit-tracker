package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index("completedAt")]
)
data class HabitLogEntity(
    @PrimaryKey
    val id: String,
    val habitId: String,
    val valueLogged: Double,
    val comment: String?,
    val completedAt: Long
)
