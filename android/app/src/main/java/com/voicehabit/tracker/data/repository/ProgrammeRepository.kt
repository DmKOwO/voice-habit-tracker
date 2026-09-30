package com.voicehabit.tracker.data.repository

import androidx.room.withTransaction
import com.voicehabit.tracker.data.local.AppDatabase
import com.voicehabit.tracker.data.local.entity.ProgrammeDayEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeExerciseEntity
import com.voicehabit.tracker.data.local.entity.ProgrammeLogEntity
import com.voicehabit.tracker.domain.model.Programme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * P1. Хранилище тренировочных программ.
 *
 * ## Запись целиком, а не по частям
 *
 * [saveProgramme] пишет программу, дни и упражнения одной транзакцией. Импорт
 * разобранного текста устроен «всё или ничего»: половина программы без упражнений
 * выглядит как пустая и молча теряет то, что человек вставил.
 *
 * ## Прогресс читается на дату
 *
 * Тренировку можно отметить в 23:40 за сегодня или утром за вчера. Поэтому срез
 * всегда parameterized датой, и «сегодня» — значение по умолчанию, а не отдельный путь.
 *
 * ## Прогресс не смешан с самим упражнением
 *
 * Одна строка `programme_exercises` обслуживает все даты, а «сколько отработано
 * сегодня» живёт в [ProgrammeLogEntity] и накладывается при чтении. Поэтому
 * `Programme.Exercise` — единственное место, где эта величина вообще существует.
 */
class ProgrammeRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = { LocalDate.now() }
) {

    private val dao get() = db.programmeDao()

    // ── Чтение ────────────────────────────────────────────────────────────────────

    fun allFlow(): Flow<List<ProgrammeSummary>> = dao.allFlow().map { list -> list.map { it.toSummary() } }

    fun countFlow(): Flow<Int> = dao.countFlow()

    /** Заголовок программы без упражнений — для списка на экране. */
    suspend fun byId(id: String): Programme? = dao.byId(id)?.let { it.toDomain(loadDays(it.id)) }

    /** Активная программа или `null`. Экран «сегодня» читает только её. */
    suspend fun activeProgramme(): Programme? =
        dao.activeOne()?.let { it.toDomain(loadDays(it.id)) }

    /**
     * Программа целиком через Flow — для экрана, который живёт на месте.
     *
     * Дни и упражнения подтягиваются **внутри** `combine`, а не в `map`: `map` не
     * умеет вызывать suspend-функции, и программа приехала бы без упражнений.
     */
    fun byIdFlow(id: String): Flow<Programme?> =
        combine(dao.byIdFlow(id), dao.daysFlowOf(id)) { programme, _ ->
            programme?.let { it.toDomain(loadDays(it.id)) }
        }

    /**
     * Активная программа через Flow.
     *
     * Только заголовок: вызывающая сторона читает упражнения дня отдельным
     * [todayWithProgressFlow]. Смешивать два наблюдения в один Flow значило бы
     * перечитывать все упражнения программы при каждом чекбоксе.
     */
    fun activeProgrammeFlow(): Flow<Programme?> = dao.allFlow().map { list ->
        list.firstOrNull { it.isActive }?.toDomain(emptyList())
    }

    /**
     * Активная программа **с днями, упражнениями и прогрессом на сегодня**.
     *
     * Для экрана программ. [activeProgrammeFlow] отдаёт только заголовок — это
     * экономно для главного экрана, но здесь нужен весь день упражнениями и
     * отметками, иначе карточка показывает «0 тренировки в неделю».
     *
     * Дни и упражнения перечитываются на каждое событие журнала, а не на каждый
     * чекбокс: журнал меняется редко, чекбокс — часто, поэтому прогресс сюда не
     * подмешивается отдельным combine.
     */
    fun activeProgrammeWithDaysFlow(date: LocalDate = today()): Flow<Programme?> =
        dao.activeProgrammeIdFlow().flatMapLatest { id: String? ->
            if (id == null) {
                flowOf<Programme?>(null)
            } else {
                // Два вложенных `combine` на два потока вместо одного на три:
                // трёхаргументная перегрузка `combine` в этой версии
                // kotlinx.coroutines разрешается неоднозначно, и вызов молча
                // уходил в vararg-версию, где типы не выводятся вовсе.
                combine(
                    dao.byIdFlow(id),
                    dao.daysFlowOf(id)
                ) { entity, dayEntities -> entity to dayEntities }
                    .combine(dao.logsForDateFlow(id, date.toString())) { pair, logs ->
                        val (entity, dayEntities) = pair
                        entity?.let { programme ->
                            val byExercise = logs.associateBy { it.exerciseId }
                            val dayIds = dayEntities.map { it.id }
                            val exercises: Map<String, List<ProgrammeExerciseEntity>> =
                                if (dayIds.isEmpty()) {
                                    emptyMap()
                                } else {
                                    dao.exercisesOfDays(dayIds).groupBy { it.dayId }
                                }
                            programme.toDomain(
                                dayEntities.map { day ->
                                    day.toDomain(
                                        exercises[day.id].orEmpty().map { ex ->
                                            val log = byExercise[ex.id]
                                            Progress(ex, log?.setsDone ?: 0, log?.value ?: 0.0, false)
                                        }
                                    )
                                }
                            )
                        }
                    }
            }
        }

    /** Дни программы с упражнениями. */
    suspend fun loadDays(programmeId: String): List<Programme.Day> {
        val days = dao.daysOf(programmeId)
        if (days.isEmpty()) return emptyList()
        val exercises = dao.exercisesOfDays(days.map { it.id }).groupBy { it.dayId }
        return days.map { day -> day.toDomain(exercises[day.id].orEmpty().map { Progress(it, 0, 0.0, false) }) }
    }

    /**
     * Сегодняшний день активной программы с прогрессом — то, что открывает карточка.
     */
    suspend fun todayWithProgress(
        programmeId: String,
        date: LocalDate = today()
    ): Programme.Day? {
        val day = dao.dayByWeekday(programmeId, date.dayOfWeek.value) ?: return null
        val logs = dao.logsForDate(programmeId, date.toString()).associateBy { it.exerciseId }
        val everDone = dao.recentLogs(programmeId, 400).map { it.exerciseId }.toSet()
        return day.toDomain(
            dao.exercisesOfDays(listOf(day.id)).map { entity ->
                val log = logs[entity.id]
                Progress(entity, log?.setsDone ?: 0, log?.value ?: 0.0, entity.id in everDone)
            }
        )
    }

    fun todayWithProgressFlow(
        programmeId: String,
        date: LocalDate = today()
    ): Flow<Programme.Day?> {
        val weekday = date.dayOfWeek.value
        val key = date.toString()
        return combine(
            dao.daysFlowOf(programmeId).map { days -> days.firstOrNull { it.weekday == weekday } },
            dao.logsForDateFlow(programmeId, key)
        ) { day, logs ->
            day?.toDomain(
                dao.exercisesOfDays(listOf(day.id)).map { entity ->
                    val log = logs.firstOrNull { it.exerciseId == entity.id }
                    Progress(entity, log?.setsDone ?: 0, log?.value ?: 0.0, false)
                }
            )
        }
    }

    suspend fun exerciseById(id: String): Programme.Exercise? = dao.exerciseById(id)?.toDomain()

    suspend fun logsOfExercise(exerciseId: String): List<ProgrammeLogEntity> = dao.logsOfExercise(exerciseId)

    suspend fun activeDates(programmeId: String): List<String> = dao.activeDates(programmeId)

    /** День, спроецированный в привычку: нужен, чтобы не плодить дубли при повторном импорте. */
    suspend fun dayForHabit(habitId: String): Programme.Day? {
        val day = dao.daysByHabitIds(listOf(habitId)).firstOrNull() ?: return null
        return day.toDomain(dao.exercisesOfDays(listOf(day.id)).map { Progress(it, 0, 0.0, false) })
    }

    // ── Запись ─────────────────────────────────────────────────────────────────────

    fun newProgrammeId(): String = "prg_" + UUID.randomUUID().toString().take(12)
    fun newDayId(): String = "pday_" + UUID.randomUUID().toString().take(12)
    fun newExerciseId(): String = "pex_" + UUID.randomUUID().toString().take(12)

    /**
     * Сохраняет программу целиком.
     *
     * Идентификаторы дней и упражнений назначаются здесь, а не вызывающим кодом:
     * разбирающий текст не должен знать формат ключей. Ключи детей переиспользуются
     * по позиции, иначе повторный импорт оставлял бы осиротевшие строки прогресса —
     * каскад их бы снёс, но логи по старым id остались бы в `programme_logs` без родителя.
     */
    suspend fun saveProgramme(
        id: String?,
        title: String,
        athleteNote: String = "",
        goals: String = "",
        sourceText: String = "",
        days: List<Programme.Day>,
        isActive: Boolean = true,
        startEpochDay: Long = today().toEpochDay()
    ): String = db.withTransaction {
        val programmeId = id ?: newProgrammeId()
        val previousDays = if (id != null) dao.daysOf(id) else emptyList()
        val previousDayIds = previousDays.map { it.id }
        val previousExerciseIds =
            if (previousDayIds.isEmpty()) emptyList() else dao.exercisesOfDays(previousDayIds).map { it.id }

        if (isActive) dao.setActiveOthersFalse(programmeId)
        dao.upsertProgramme(
            ProgrammeEntity(
                id = programmeId,
                title = title,
                athleteNote = athleteNote,
                goals = goals,
                sourceText = sourceText,
                startEpochDay = startEpochDay,
                isActive = isActive,
                createdAt = clock()
            )
        )

        val dayEntities = days.mapIndexed { index, day ->
            ProgrammeDayEntity(
                id = day.id.takeIf { it.isNotBlank() && it in previousDayIds } ?: newDayId(),
                programmeId = programmeId,
                position = index,
                weekday = day.weekday.coerceIn(1, 7),
                title = day.title,
                focusNote = day.focusNote,
                isRest = day.isRest,
                habitId = day.habitId
            )
        }
        dao.upsertDays(dayEntities)
        dao.deleteDaysOfMissing(programmeId, dayEntities.map { it.id })

        val exerciseEntities = dayEntities.flatMapIndexed { index, dayEntity ->
            days[index].exercises.mapIndexed { exIndex, exercise ->
                ProgrammeExerciseEntity(
                    id = exercise.id.takeIf { it.isNotBlank() && it in previousExerciseIds } ?: newExerciseId(),
                    dayId = dayEntity.id,
                    position = exIndex,
                    title = exercise.title,
                    outdoor = exercise.outdoor,
                    home = exercise.home,
                    sets = exercise.sets.coerceAtLeast(1),
                    repsMin = exercise.repsMin,
                    repsMax = exercise.repsMax,
                    measure = exercise.measure,
                    tempo = exercise.tempo,
                    restSec = exercise.restSec,
                    note = exercise.note,
                    targetValue = exercise.targetValue,
                    currentValue = exercise.currentValue
                )
            }
        }
        dao.upsertExercises(exerciseEntities)

        programmeId
    }

    suspend fun setActive(id: String, active: Boolean) {
        if (active) dao.setActiveOthersFalse(id)
        dao.setActive(id, active)
    }

    /**
     * Удаляет программу. Привычки дней **не трогаются**: у них своя история стриков,
     * и снести её вместе с планом — потерять данные, которых нигде больше нет.
     */
    suspend fun delete(id: String) = dao.deleteProgramme(id)

    suspend fun linkHabit(dayId: String, habitId: String?) = dao.linkHabit(dayId, habitId)

    // ── Прогресс ───────────────────────────────────────────────────────────────────

    /**
     * Отмечает подход. Значение **прибавляется**: три подхода по 10 секунд дают
     * 30 секунд, а не 10, записанные трижды.
     *
     * @return упражнение с новым состоянием за день или `null`, если упражнения нет
     */
    suspend fun logSet(
        exerciseId: String,
        programmeId: String,
        value: Double,
        date: LocalDate = today()
    ): Programme.Exercise? = db.withTransaction {
        val exercise = dao.exerciseById(exerciseId) ?: return@withTransaction null
        val key = date.toString()
        val existing = dao.logsForDate(programmeId, key).firstOrNull { it.exerciseId == exerciseId }
        val setsDone = (existing?.setsDone ?: 0) + 1
        val totalValue = (existing?.value ?: 0.0) + value
        dao.upsertLog(
            ProgrammeLogEntity(
                id = existing?.id ?: "plog_" + UUID.randomUUID().toString().take(12),
                exerciseId = exerciseId,
                programmeId = programmeId,
                localDate = key,
                value = totalValue,
                setsDone = setsDone,
                completedAt = clock()
            )
        )
        exercise.toDomain().copy(setsDoneToday = setsDone, valueToday = totalValue)
    }

    /** Ставит прогресс упражнения за день ровно — для голоса «сделал 4 подхода». */
    suspend fun setProgress(
        exerciseId: String,
        programmeId: String,
        setsDone: Int,
        value: Double,
        date: LocalDate = today()
    ) = db.withTransaction {
        val key = date.toString()
        val existing = dao.logsForDate(programmeId, key).firstOrNull { it.exerciseId == exerciseId }
        if (setsDone <= 0) {
            dao.deleteLogsOfDate(exerciseId, key)
            return@withTransaction
        }
        dao.upsertLog(
            ProgrammeLogEntity(
                id = existing?.id ?: "plog_" + UUID.randomUUID().toString().take(12),
                exerciseId = exerciseId,
                programmeId = programmeId,
                localDate = key,
                value = value,
                setsDone = setsDone,
                completedAt = clock()
            )
        )
    }

    /** Сбросить день: «считал не то». */
    suspend fun resetDay(programmeId: String, date: LocalDate = today()) {
        val key = date.toString()
        dao.logsForDate(programmeId, key).forEach { dao.deleteLogsOfDate(it.exerciseId, key) }
    }

    /**
     * Отметить весь день одним касанием — «сделал всё».
     *
     * @return сколько упражнений закрыто этим действием
     */
    suspend fun completeWholeDay(programmeId: String, date: LocalDate = today()): Int {
        val day = dao.dayByWeekday(programmeId, date.dayOfWeek.value) ?: return 0
        val exercises = dao.exercisesOfDays(listOf(day.id))
        if (exercises.isEmpty()) return 0
        val key = date.toString()
        val existing = dao.logsForDate(programmeId, key).associateBy { it.exerciseId }
        var closed = 0
        exercises.forEach { entity ->
            if ((existing[entity.id]?.setsDone ?: 0) >= entity.sets) return@forEach
            dao.upsertLog(
                ProgrammeLogEntity(
                    id = "plog_" + UUID.randomUUID().toString().take(12),
                    exerciseId = entity.id,
                    programmeId = programmeId,
                    localDate = key,
                    value = entity.repsMin.toDouble(),
                    setsDone = entity.sets,
                    completedAt = clock()
                )
            )
            closed++
        }
        return closed
    }

    // ── Отображение ────────────────────────────────────────────────────────────────

    /** Упражнение + состояние за день. */
    private data class Progress(
        val entity: ProgrammeExerciseEntity,
        val setsDoneToday: Int,
        val valueToday: Double,
        val isEverDone: Boolean
    )

    private fun ProgrammeEntity.toSummary() = ProgrammeSummary(
        id = id,
        title = title,
        goals = goals,
        athleteNote = athleteNote,
        isActive = isActive,
        createdAt = createdAt
    )

    private fun ProgrammeEntity.toDomain(days: List<Programme.Day>) = Programme(
        id = id,
        title = title,
        athleteNote = athleteNote,
        sourceText = sourceText,
        goals = goals,
        startEpochDay = startEpochDay,
        isActive = isActive,
        createdAt = createdAt,
        days = days
    )

    private fun ProgrammeDayEntity.toDomain(exercises: List<Progress>) = Programme.Day(
        id = id,
        programmeId = programmeId,
        position = position,
        weekday = weekday,
        title = title,
        focusNote = focusNote,
        isRest = isRest,
        habitId = habitId,
        exercises = exercises.map { p ->
            Programme.Exercise(
                id = p.entity.id,
                dayId = p.entity.dayId,
                position = p.entity.position,
                title = p.entity.title,
                outdoor = p.entity.outdoor,
                home = p.entity.home,
                sets = p.entity.sets,
                repsMin = p.entity.repsMin,
                repsMax = p.entity.repsMax,
                measure = p.entity.measure,
                tempo = p.entity.tempo,
                restSec = p.entity.restSec,
                note = p.entity.note,
                targetValue = p.entity.targetValue,
                currentValue = p.entity.currentValue,
                setsDoneToday = p.setsDoneToday,
                valueToday = p.valueToday,
                isEverDone = p.isEverDone
            )
        }
    )

    private fun ProgrammeExerciseEntity.toDomain() = Programme.Exercise(
        id = id,
        dayId = dayId,
        position = position,
        title = title,
        outdoor = outdoor,
        home = home,
        sets = sets,
        repsMin = repsMin,
        repsMax = repsMax,
        measure = measure,
        tempo = tempo,
        restSec = restSec,
        note = note,
        targetValue = targetValue,
        currentValue = currentValue
    )
}

/** Строка списка программ: без упражнений, их на этом экране всё равно не видно. */
data class ProgrammeSummary(
    val id: String,
    val title: String,
    val goals: String,
    val athleteNote: String,
    val isActive: Boolean,
    val createdAt: Long
)
