package com.voicehabit.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.voicehabit.tracker.data.local.entity.VoiceLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceLogDao {
    @Query("SELECT * FROM voice_logs ORDER BY createdAt DESC")
    fun getAllVoiceLogsFlow(): Flow<List<VoiceLogEntity>>

    /**
     * `@Upsert`, а не `REPLACE`: статус и сводка обновляются по id, а не пересоздаётся строка,
     * иначе повторная запись воркера затирала бы исходный транскрипт и время создания.
     */
    @Upsert
    suspend fun upsertVoiceLog(log: VoiceLogEntity)

    @Query("UPDATE voice_logs SET status = :status, summary = :summary WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, summary: String)

    @Query("SELECT * FROM voice_logs WHERE status = 'PENDING_UPLOAD' ORDER BY createdAt ASC")
    suspend fun getPendingUploads(): List<VoiceLogEntity>

    @Query("SELECT * FROM voice_logs WHERE id = :id")
    suspend fun getVoiceLogById(id: String): VoiceLogEntity?
}
