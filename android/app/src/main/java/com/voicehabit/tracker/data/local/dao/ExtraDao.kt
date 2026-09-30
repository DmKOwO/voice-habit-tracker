package com.voicehabit.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.voicehabit.tracker.data.local.entity.AchievementEntity
import com.voicehabit.tracker.data.local.entity.ChallengeEntity
import com.voicehabit.tracker.data.local.entity.DayMarkEntity
import com.voicehabit.tracker.data.local.entity.DigestEntity
import com.voicehabit.tracker.data.local.entity.FocusSessionEntity
import com.voicehabit.tracker.data.local.entity.MoodEntity
import com.voicehabit.tracker.data.local.entity.ReviewLogEntity
import com.voicehabit.tracker.data.local.entity.RoutineEntity
import com.voicehabit.tracker.data.local.entity.SubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_log WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay ASC")
    suspend fun range(from: Long, to: Long): List<MoodEntity>

    @Query("SELECT * FROM mood_log WHERE dateEpochDay = :day")
    suspend fun get(day: Long): MoodEntity?

    @Upsert
    suspend fun upsert(mood: MoodEntity)
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    fun getForTaskFlow(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getForTask(taskId: String): List<SubtaskEntity>

    @Upsert
    suspend fun upsert(item: SubtaskEntity)

    @Query("UPDATE subtasks SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 50): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    suspend fun since(since: Long): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DayMarkDao {
    @Query("SELECT * FROM day_marks WHERE dateEpochDay BETWEEN :from AND :to")
    suspend fun range(from: Long, to: Long): List<DayMarkEntity>

    @Query("SELECT COUNT(*) FROM day_marks WHERE mark = 'FREEZE' AND dateEpochDay BETWEEN :weekStart AND :weekEnd")
    suspend fun freezeCountInWeek(weekStart: Long, weekEnd: Long): Int

    @Upsert
    suspend fun upsert(mark: DayMarkEntity)

    @Query("DELETE FROM day_marks WHERE dateEpochDay = :day")
    suspend fun delete(day: Long)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun allFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun all(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(item: AchievementEntity): Long
}

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY createdAt DESC")
    fun allFlow(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun byId(id: String): ChallengeEntity?

    @Upsert
    suspend fun upsert(item: ChallengeEntity)

    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt ASC")
    fun allFlow(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY createdAt ASC")
    suspend fun all(): List<RoutineEntity>

    @Upsert
    suspend fun upsert(item: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_log ORDER BY dateEpochDay DESC LIMIT :limit")
    fun recentFlow(limit: Int = 30): Flow<List<ReviewLogEntity>>

    @Upsert
    suspend fun upsert(item: ReviewLogEntity)
}

@Dao
interface DigestDao {
    @Query("SELECT * FROM digests ORDER BY pinned DESC, createdAt DESC")
    fun allFlow(): Flow<List<DigestEntity>>

    @Query("SELECT * FROM digests ORDER BY pinned DESC, createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<DigestEntity>

    @Query("SELECT * FROM digests WHERE id = :id")
    suspend fun byId(id: String): DigestEntity?

    /** Поиск по записи голоса: одна голосовая запись — один конспект, без дублей. */
    @Query("SELECT * FROM digests WHERE voiceLogId = :voiceLogId LIMIT 1")
    suspend fun byIdForVoiceLog(voiceLogId: String): DigestEntity?

    @Query("SELECT COUNT(*) FROM digests")
    fun countFlow(): Flow<Int>

    @Upsert
    suspend fun upsert(item: DigestEntity)

    @Query("UPDATE digests SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM digests WHERE id = :id")
    suspend fun delete(id: String)

    /** Чистка старых записей разговоров: конспект важнее, а транскрипт — нет. */
    @Query("UPDATE digests SET transcript = '' WHERE transcript != '' AND createdAt < :before")
    suspend fun stripOldTranscripts(before: Long)
}
