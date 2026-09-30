package com.voicehabit.tracker.domain.model

import androidx.compose.runtime.Immutable

/**
 * P1. Программа тренировок.
 *
 * ## Зачем она нужна рядом с привычками
 *
 * Привычка отвечает на вопрос «был ли я тут сегодня». Программа отвечает на вопрос
 * «что именно сегодня делать и куда я иду». Обе вещи нужны одновременно, но ведут
 * себя по-разному: привычка даёт стрик, сетку и виджет, программа — список
 * упражнений с подходами и прогрессом по каждому.
 *
 * ## Проекция, а не замена
 *
 * Каждый тренировочный день получает привычку с **ровно теми** днями недели, что
 * стоят в программе. Программа «Пн/Ср/Пт» даёт три привычки с `scheduleDays =
 * {1, 3, 5}`; дни отдыха в привычки не попадают и потому не обрывают стрик.
 * Привычка — это вход, программа — то, что открывается за этим входом.
 */
@Immutable
data class Programme(
    val id: String,
    val title: String = "",
    val athleteNote: String = "",
    val sourceText: String = "",
    val goals: String = "",
    val startEpochDay: Long = 0L,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val days: List<Day> = emptyList()
) {

    /** Тренировочные дни — те, для которых создаётся привычка. */
    val trainingDays: List<Day> get() = days.filter { !it.isRest }

    /** Дни отдыха из исходного расписания. */
    val restDays: List<Day> get() = days.filter { it.isRest }

    /**
     * Дни недели 1..7, в которые нужно тренироваться.
     *
     * Пусто у программы без тренировочных дней: пустой `scheduleDays` в привычке
     * означает «каждый день», и такая программа тихо превратила бы привычку в
     * ежедневную — то есть в то, чего человек не просил.
     */
    val trainingWeekdays: Set<Int>
        get() = trainingDays.map { it.weekday }.filter { it in 1..7 }.toSet()

    /** Все дни недели, упомянутые программой, включая отдых. */
    val mentionedWeekdays: Set<Int>
        get() = days.map { it.weekday }.filter { it in 1..7 }.toSet()

    /**
     * Сколько раз в неделю нужно приходить: 3 для Пн/Ср/Пт.
     * `Habit.frequency` выводится из этого, а не задаётся руками.
     */
    val sessionsPerWeek: Int get() = trainingWeekdays.size

    /** День по дню недели или `null`, если в этот день отдых. */
    fun dayFor(weekday: Int): Day? = days.firstOrNull { it.weekday == weekday }

    /** День недели по ISO: понедельник = 1. */
    val todayWeekday: Int get() = java.time.LocalDate.now().dayOfWeek.value

    /** Сегодняшний день программы: тренировка, отдых или ничего. */
    fun todayDay(): Day? = dayFor(todayWeekday)

    /** Тренируется ли сегодня. */
    val trainsToday: Boolean get() = todayWeekday in trainingWeekdays

    /**
     * `DAILY` только когда тренировка действительно каждый день.
     *
     * Трёхдневный сплит не должен показываться как ежедневная привычка: иначе в
     * карточке «Каждый день» с расписанием Пн/Ср/Пт, и стрик считает дни отдыха
     * пропусками.
     */
    val frequency: String get() = if (trainingWeekdays.size >= 7) "DAILY" else "WEEKLY"

    @Immutable
    data class Day(
        val id: String,
        val programmeId: String,
        val position: Int = 0,
        val weekday: Int = 1,
        val title: String = "",
        val focusNote: String = "",
        val isRest: Boolean = false,
        val habitId: String? = null,
        val exercises: List<Exercise> = emptyList()
    ) {
        val weekdayLabel: String
            get() = when (weekday) {
                1 -> "Пн"; 2 -> "Вт"; 3 -> "Ср"; 4 -> "Чт"
                5 -> "Пт"; 6 -> "Сб"; 7 -> "Вс"; else -> "—"
            }

        /**
         * Полное название дня по-русски.
         *
         * Раньше здесь был `DayOfWeek.getDisplayName(FULL, Locale.getDefault())`, и на
         * английской системе карточка тренировки подписывалась «Пн monday». Приложение
         * русскоязычное, а локаль телефона о названии дня не решает.
         */
        val weekdayFull: String
            get() = when (weekday) {
                1 -> "понедельник"; 2 -> "вторник"; 3 -> "среда"; 4 -> "четверг"
                5 -> "пятница"; 6 -> "суббота"; 7 -> "воскресенье"; else -> "—"
            }
    }

    @Immutable
    data class Exercise(
        val id: String,
        val dayId: String,
        val position: Int = 0,
        val title: String = "",
        val outdoor: String = "",
        val home: String = "",
        val sets: Int = 1,
        val repsMin: Int = 0,
        val repsMax: Int = 0,
        val measure: String = "повт",
        val tempo: String = "",
        val restSec: Int = 0,
        val note: String = "",
        val targetValue: Double = 0.0,
        val currentValue: Double = 0.0,
        /** Прогресс за сегодня: отработано подходов. */
        val setsDoneToday: Int = 0,
        /** Прогресс за сегодня: накопительное значение. */
        val valueToday: Double = 0.0,
        /** Есть ли записи хотя бы за один день — для истории упражнения. */
        val isEverDone: Boolean = false
    ) {
        /**
         * Рецепт одной строкой: «4 × 8–12 сек».
         *
         * Диапазон повторов показывается только когда он реально задан: «4 × 10 повт»
         * читается лучше, чем «4 × 10–10 повт».
         */
        val prescription: String
            get() {
                val reps = when {
                    repsMax > repsMin && repsMin > 0 -> "$repsMin–$repsMax"
                    repsMin > 0 -> "$repsMin"
                    else -> ""
                }
                // У статики повторов нет («4 × макс»), и рецепт «4 × » с пустым
                // хвостом читается как ошибка разбора, а не как программа.
                return when {
                    reps.isNotBlank() && measure.isNotBlank() -> "${if (sets > 1) "$sets × " else ""}$reps $measure"
                    reps.isNotBlank() -> "${if (sets > 1) "$sets × " else ""}$reps"
                    sets > 1 -> "$sets ${setWord(sets)}"
                    else -> "по рецепту"
                }
            }

        /** Есть ли смысл показывать прогресс: цель задана и текущий уровень известен. */
        val hasProgression: Boolean get() = targetValue > 0.0 && currentValue > 0.0

        val progressionLabel: String
            get() = if (hasProgression) {
                "${trimNumber(currentValue)} → ${trimNumber(targetValue)} $measure"
            } else {
                ""
            }

        /** Выполнен ли подход за сегодня. */
        val isDoneToday: Boolean get() = setsDoneToday >= sets && sets > 0

        /** Отдых в читаемом виде: «Отдых 2 мин 30 с». */
        val restLabel: String
            get() = when {
                restSec <= 0 -> ""
                restSec % 60 == 0 -> "Отдых ${restSec / 60} мин"
                else -> "Отдых ${restSec / 60} мин ${restSec % 60} с"
            }

        val tempoLabel: String get() = if (tempo.isBlank()) "" else "Темп $tempo"
    }

    companion object {
        /** «2 подхода», «4 подхода», «5 подходов» — статика обычно без повторов. */
        fun setWord(sets: Int): String = when {
            sets % 100 in 11..14 -> "подходов"
            sets % 10 == 1 -> "подход"
            sets % 10 in 2..4 -> "подхода"
            else -> "подходов"
        }

        /** «8.0» → «8»: дробные нули в конспекте программы только мешают. */
        fun trimNumber(value: Double): String =
            if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)
    }
}
