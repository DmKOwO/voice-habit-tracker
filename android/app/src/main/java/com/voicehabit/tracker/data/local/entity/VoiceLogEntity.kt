package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_logs",
    indices = [Index(value = ["status", "createdAt"])]
)
data class VoiceLogEntity(
    @PrimaryKey
    val id: String,
    val audioPath: String,
    val rawTranscript: String,
    val summary: String,
    val status: String, // 'PENDING_UPLOAD', 'PROCESSED', 'APPLIED', 'FAILED'
    val createdAt: Long
)
