package com.voicehabit.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.voicehabit.tracker.domain.model.Programme

/**
 * P1. Тренировочная программа — расписание и структура, а не задача.
 *
 * ## Почему отдельная сущность, а не ещё одна привычка
 *
 * У привычки есть `scheduleDays`, стрик и сетка — ровно то, что нужно тренировочному
 * дню. Но у неё нет списка упражнений с подходами, повторами, темпом и отдыхом, и
 * добавление их в `habits` означало бы три новых колонки, которые читает только
 * тренировка. Поэтому программа живёт отдельно, а на привычки **проецируется**:
 * см. [ProgrammeDayEntity.habitId].
 *
 * ## Что здесь хранится, а что выводится
 *
 * Хранится то, что пришло из источника и не должно меняться от правок пользователя:
 * дни недели, упражнения, варианты выполнения, темп. Счётчики прогресса — в
 * [ProgrammeLogEntity], истина о выполнении — там же.
 */
@Entity(tableName = "programmes")
data class ProgrammeEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    /** Заметка об атлете: рост, вес, база. Попадает в «Контекст обо мне». */
    val athleteNote: String = "",
    /** Текст, из которого программа получена — для переразбора и правок. */
    val sourceText: String = "",
    /** Основные цели: «+5–7 кг», «чистый выход силой». */
    val goals: String = "",
    val startEpochDay: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * P1. День программы. Один день недели — одна строка, включая дни отдыха.
 *
 * Дни отдыха хранятся, а не вычисляются: в исходном документе есть полная таблица
 * недели, и потерять её значило бы потерять «Суперкомпенсация и сон» как часть
 * плана. Привычка при этом создаётся **только** для тренировочных дней, поэтому
 * дни отдыха не ломают стрик — [com.voicehabit.tracker.domain.usecase.CalculateStreakUseCase]
 * и так пропускает дни, не входящие в `scheduleDays`.
 */
@Entity(
    tableName = "programme_days",
    foreignKeys = [ForeignKey(
        entity = ProgrammeEntity::class,
        parentColumns = ["id"],
        childColumns = ["programmeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("programmeId"), Index("habitId")]
)
data class ProgrammeDayEntity(
    @PrimaryKey val id: String,
    val programmeId: String,
    /** Порядок внутри программы: 0,1,2… Соответствует типовой записи «День 1/2/3». */
    val position: Int = 0,
    /** День недели 1..7, где 1 = понедельник. Как в `Habit.scheduleDays`. */
    val weekday: Int = 1,
    val title: String = "",
    /** «Тяга, передний вис и квадрицепсы» — характер дня одной строкой. */
    val focusNote: String = "",
    val isRest: Boolean = false,
    /**
     * P1. Привычка, в которую этот день спроецирован.
     *
     * `null` — день отдыха или проекция ещё не создана. Здесь может лежать id
     * **существующей** привычки пользователя, если программу встроили в неё, а не
     * создали отдельную. Удаление программы не удаляет привычку: обратной связи нет
     * и у историей стриков она не должна делиться с планом.
     */
    val habitId: String? = null
)

/**
 * P1. Упражнение внутри дня.
 *
 * Рецепт хранится структурно, а не строкой: подходы, диапазон повторов и единица
 * измерения нужны прогрессу («2 из 4 подходов»), а темп и отдых — только для
 * показа. Исходная формулировка сохраняется в [note], поэтому ничего не теряется
 * при разборе.
 */
@Entity(
    tableName = "programme_exercises",
    foreignKeys = [ForeignKey(
        entity = ProgrammeDayEntity::class,
        parentColumns = ["id"],
        childColumns = ["dayId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("dayId")]
)
data class ProgrammeExerciseEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val position: Int = 0,
    val title: String = "",
    /** Вариант на воркаут-площадке. */
    val outdoor: String = "",
    /** Домашний вариант. */
    val home: String = "",
    val sets: Int = 1,
    val repsMin: Int = 0,
    val repsMax: Int = 0,
    /** Единица измерения: «сек», «повт», «мин», «м». */
    val measure: String = "повт",
    /** «2-0-3», «Статика», «Изоляция». */
    val tempo: String = "",
    val restSec: Int = 0,
    /** Исходная строка рецепта — для переразбора и показа «как в источнике». */
    val note: String = "",
    /** Цель прогрессии: «20 сек». 0 = цель не задана. */
    val targetValue: Double = 0.0,
    /** Текущий уровень: «8 сек». */
    val currentValue: Double = 0.0
)

/**
 * P1. Выполнение упражнения за конкретный день.
 *
 * ## Почему не `habit_logs`
 *
 * `HabitRepositoryImpl.toggleHabitCompletion` при снятии отметки вызывает
 * `deleteTodayLogsForHabit`, который удаляет **все** записи за день. Для привычки
 * «сегодня или нет» это правильно, для тренировки — нет: отработал 2 подхода из 4,
 * тапнул чекбокс, и два подхода исчезли. Поэтому прогресс упражнений живёт здесь и
 * никогда не делит таблицу с чекбоксом привычки.
 *
 * Значения **накопительные**: один день тренировки может дать несколько строк
 * (подход за подходом), как в `habit_logs`.
 */
@Entity(
    tableName = "programme_logs",
    foreignKeys = [ForeignKey(
        entity = ProgrammeExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exerciseId"), Index("localDate"), Index("programmeId")]
)
data class ProgrammeLogEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    /** Денормализовано для выборки «вся программа за дату» без JOIN. */
    val programmeId: String,
    /** Дата в локальном формате `yyyy-MM-dd`, как в `task_logs`. */
    val localDate: String,
    /** Накопительно за день: секунды для статики, повторы для силовых. */
    val value: Double = 0.0,
    /** Сколько подходов отработано. */
    val setsDone: Int = 0,
    val completedAt: Long = System.currentTimeMillis()
)

/** Доменная проекция программы целиком: дни со своими упражнениями. */
data class ProgrammeWithDays(
    val programme: Programme,
    val days: List<Programme.Day>
)
