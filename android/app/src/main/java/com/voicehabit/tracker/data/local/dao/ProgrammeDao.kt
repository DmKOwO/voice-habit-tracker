package com.voicehabit.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.voicehabit.tracker.data.local.entity.ProgrammeDayEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeExerciseEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * P1. Программы тренировок.
 *
 * Дни и упражнения читаются по программе отдельными запросами, а не одним JOIN'ом
 * с вложенными `@Relation`: строк мало (десятки), список нужен дважды — для экрана
 * программы и для карточки сегодняшнего дня, — и лишние таблицы в JOIN'е только
 * усложняют отображение на три уровня.
 */
@Dao
interface ProgrammeDao {

    @Query("SELECT * FROM programmes ORDER BY isActive DESC, createdAt DESC")
    fun allFlow(): Flow<List<ProgrammeEntity>>

    @Query("SELECT * FROM programmes WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeOne(): ProgrammeEntity?

    @Query("SELECT * FROM programmes WHERE id = :id")
    suspend fun byId(id: String): ProgrammeEntity?

    @Query("SELECT * FROM programmes WHERE id = :id")
    fun byIdFlow(id: String): Flow<ProgrammeEntity?>

    /**
     * id активной программы — чтобы подписаться на её дни и журнал.
     *
     * Отдельный метод, а не `allFlow().map { it.first().id }`: журнал и дни
     * подписываются по id, и без него экран не знает, что именно наблюдать.
     */
    @Query("SELECT id FROM programmes WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun activeProgrammeIdFlow(): Flow<String?>

    @Upsert
    suspend fun upsertProgramme(item: ProgrammeEntity)

    @Query("UPDATE programmes SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    /** Активна может быть ровно одна программа: экран «сегодня» показывает одну. */
    @Query("UPDATE programmes SET isActive = 0 WHERE id != :keepId")
    suspend fun setActiveOthersFalse(keepId: String)

    @Query("DELETE FROM programmes WHERE id = :id")
    suspend fun deleteProgramme(id: String)

    // --- Дни ---

    @Query("SELECT * FROM programme_days WHERE programmeId = :programmeId ORDER BY position ASC")
    suspend fun daysOf(programmeId: String): List<ProgrammeDayEntity>

    @Query("SELECT * FROM programme_days WHERE programmeId = :programmeId ORDER BY position ASC")
    fun daysFlowOf(programmeId: String): Flow<List<ProgrammeDayEntity>>

    /** День по дню недели — то, что нужно карточке «сегодня». */
    @Query("SELECT * FROM programme_days WHERE programmeId = :programmeId AND weekday = :weekday LIMIT 1")
    suspend fun dayByWeekday(programmeId: String, weekday: Int): ProgrammeDayEntity?

    @Query("SELECT * FROM programme_days WHERE habitId IN (:habitIds)")
    suspend fun daysByHabitIds(habitIds: List<String>): List<ProgrammeDayEntity>

    @Upsert
    suspend fun upsertDay(item: ProgrammeDayEntity)

    @Upsert
    suspend fun upsertDays(items: List<ProgrammeDayEntity>)

    @Query("UPDATE programme_days SET habitId = :habitId WHERE id = :dayId")
    suspend fun linkHabit(dayId: String, habitId: String?)

    @Query("DELETE FROM programme_days WHERE programmeId = :programmeId")
    suspend fun deleteDaysOf(programmeId: String)

    /**
     * Удаляет дни программы, которых больше нет в новой версии.
     *
     * Каскад сносит их упражнения, а те — логи. Без этого повторный импорт
     * оставлял бы после себя осиротевшие строки прогресса.
     */
    @Query("DELETE FROM programme_days WHERE programmeId = :programmeId AND id NOT IN (:keepIds)")
    suspend fun deleteDaysOfMissing(programmeId: String, keepIds: List<String>)

    // --- Упражнения ---

    @Query("SELECT * FROM programme_exercises WHERE dayId IN (:dayIds) ORDER BY position ASC")
    suspend fun exercisesOfDays(dayIds: List<String>): List<ProgrammeExerciseEntity>

    @Query("SELECT * FROM programme_exercises WHERE dayId IN (:dayIds) ORDER BY position ASC")
    fun exercisesFlowOfDays(dayIds: List<String>): Flow<List<ProgrammeExerciseEntity>>

    @Query("SELECT * FROM programme_exercises WHERE id = :id")
    suspend fun exerciseById(id: String): ProgrammeExerciseEntity?

    @Upsert
    suspend fun upsertExercise(item: ProgrammeExerciseEntity)

    @Upsert
    suspend fun upsertExercises(items: List<ProgrammeExerciseEntity>)

    @Query("DELETE FROM programme_exercises WHERE dayId IN (:dayIds)")
    suspend fun deleteExercisesOfDays(dayIds: List<String>)

    // --- Прогресс ---

    /**
     * Логи за дату по программе.
     *
     * Ограничение по `programmeId`, а не по упражнениям: карточке «сегодня» нужен
     * срез одной программы за одну дату, а упражнения надо подтянуть отдельно.
     */
    @Query("SELECT * FROM programme_logs WHERE programmeId = :programmeId AND localDate = :localDate")
    suspend fun logsForDate(programmeId: String, localDate: String): List<ProgrammeLogEntity>

    @Query("SELECT * FROM programme_logs WHERE programmeId = :programmeId AND localDate = :localDate")
    fun logsForDateFlow(programmeId: String, localDate: String): Flow<List<ProgrammeLogEntity>>

    @Query("SELECT * FROM programme_logs WHERE exerciseId = :exerciseId ORDER BY localDate DESC")
    suspend fun logsOfExercise(exerciseId: String): List<ProgrammeLogEntity>

    @Query("SELECT * FROM programme_logs WHERE programmeId = :programmeId ORDER BY localDate DESC LIMIT :limit")
    suspend fun recentLogs(programmeId: String, limit: Int = 200): List<ProgrammeLogEntity>

    @Upsert
    suspend fun upsertLog(item: ProgrammeLogEntity)

    /** Сбросить прогресс дня: «отжал не то». */
    @Query("DELETE FROM programme_logs WHERE exerciseId = :exerciseId AND localDate = :localDate")
    suspend fun deleteLogsOfDate(exerciseId: String, localDate: String)

    /** Даты, в которые программа была отмечена, — для календаря и статистики. */
    @Query("SELECT DISTINCT localDate FROM programme_logs WHERE programmeId = :programmeId ORDER BY localDate DESC")
    suspend fun activeDates(programmeId: String): List<String>

    @Query("SELECT COUNT(*) FROM programmes")
    fun countFlow(): Flow<Int>

    /** Полная замена содержимого программы при переимпорте: дети чистятся каскадом. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammeRaw(item: ProgrammeEntity)
}
