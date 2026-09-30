package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.Programme

/**
 * P1. Голосовая отметка прогресса внутри тренировки.
 *
 * [number] — повторы или секунды удержания, [unit] — «повт»/«сек». Если человек не
 * назвал число, поле пустое: тогда команда означает «отметить подход», и это
 * принципиально другое намерение, чем «записать 12 повторов».
 */
data class ProgrammeVoiceResult(
    val exercise: Programme.Exercise,
    val sets: Int,
    val number: Double?,
    val unit: String?,
    /** День целиком закрыт: «тренировка сделана». */
    val completesWholeDay: Boolean,
    val transcript: String,
    val reason: String
) {
    val isSetOnly: Boolean get() = number == null
}

/**
 * P1. Разбор голоса для программы.
 *
 * ## Что понимает
 *
 * - «подтягивания двенадцать» → повторы у упражнения, подобранного по названию;
 * - «уголок двадцать секунд» → удержание;
 * - «сделал четыре подхода по восемь» → подходы и повторы одной фразой;
 * - «тренировка сделана» → закрыть день целиком.
 *
 * ## Почему это чистая функция
 *
 * Как и [ProgrammeHabitProjector], разбор не ходит в сеть и не знает про базу:
 * он получает день программы и фразу, а отдаёт намерение. Поэтому «пятнадцать
 * отжиманий» можно проверить тестом, не поднимая ни БД, ни микрофон.
 *
 * ## Приоритет: сначала закрытие дня, потом упражнение
 *
 * Фраза «тренировка сделана» не должна превращаться в отметку упражнения, которое
 * случайно похоже по слову. Закрытие дня — более сильное намерение, и оно
 * проверяется раньше.
 */
object ProgrammeVoiceResolver {

    /** Порог подбора упражжения по названию. */
    private const val MATCH_THRESHOLD = 0.3

    private val WHOLE_DAY_MARKERS = listOf(
        "тренировка сделана", "тренировку сделал", "тренировку сделала", "тренировка закончена",
        "тренировка окончена", "всю тренировку", "день тренировки сделан", "тренировка выполнена",
        "закрыл тренировку", "закрыла тренировку", "отработал тренировку", "отработала тренировку"
    )

    private val SET_MARKERS = listOf(
        "подход", "подхода", "подходов", "сет", "сета", "сетов"
    )

    private val SECOND_MARKERS = listOf("секунд", "секунда", "секунды", "сек", "удержание", "удержал", "удержала")
    private val REP_MARKERS = listOf("повтор", "повтора", "повторов", "повт", "раз", "раза", "разов")

    /** Сегодня тренировочного дня нет — прогресс записывать некуда. */
    fun resolve(transcript: String, day: Programme.Day?): ProgrammeVoiceResult? {
        val text = transcript.trim().lowercase()
        if (text.isBlank()) return null
        if (day == null || day.isRest || day.exercises.isEmpty()) return null

        WHOLE_DAY_MARKERS.firstOrNull { text.contains(it) }?.let {
            return ProgrammeVoiceResult(
                exercise = day.exercises.first(),
                sets = day.exercises.size,
                number = null,
                unit = null,
                completesWholeDay = true,
                transcript = transcript,
                reason = "Фраза означает, что день тренировки закрыт целиком"
            )
        }

        // «Сделал четыре подхода по восемь»: первое число — подходы, второе — повторы.
        // Одно число «сделал четыре подхода» — это подходы, и повторы выдумывать нельзя.
        val setMarker = SET_MARKERS.firstOrNull { text.contains(it) }
        val sets = setMarker?.let { marker ->
            RussianNumberParser.findFirst(text.substringBefore(marker))?.toInt()?.takeIf { it in 1..20 }
        }
        val number = if (sets != null) {
            // Повторы ищутся после слова «подход(а/ов)»: «... по восемь».
            RussianNumberParser.findFirst(text.substringAfter(setMarker!!))
        } else {
            RussianNumberParser.findFirst(text)
        }
        val matched = day.exercises
            .map { it to bestVariantScore(text, it.title) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MATCH_THRESHOLD }

        if (matched == null) {
            return null
        }

        val exercise = matched.first
        val unit = unitFor(text, exercise)

        return ProgrammeVoiceResult(
            exercise = exercise,
            sets = sets ?: exercise.sets,
            number = number,
            unit = unit,
            completesWholeDay = false,
            transcript = transcript,
            reason = matchedReason(text, exercise, number, unit, sets)
        )
    }

    private fun matchedReason(
        text: String,
        exercise: Programme.Exercise,
        number: Double?,
        unit: String?,
        sets: Int?
    ): String = buildString {
        append("Упражнение «${exercise.title}»")
        append(" подобрано по словам «${matchedFragment(text, exercise.title)}»")
        if (sets != null && number != null) {
            append(", названо $sets ${Programme.setWord(sets)} по ${trimNumber(number)}${unit?.let { " $it" } ?: ""}")
        } else if (sets != null) {
            append(", названо только подходов: $sets")
        } else if (number != null) {
            append(", число: ${trimNumber(number)}${unit?.let { " $it" } ?: ""}")
        } else {
            append(", число не названо — отмечу подход")
        }
    }

    private fun matchedFragment(text: String, title: String): String {
        val word = title.split(" ", "(", "/")
            .map { it.trim() }
            .filter { it.length >= 3 && text.contains(it.lowercase()) }
            .maxByOrNull { it.length }
        return word ?: "похожее название"
    }

    /**
     * Единица измерения: сказанное слово важнее, иначе — единица самого упражнения.
     *
     * «Двенадцать отжиманий» не содержит слова «повт», но отжимания в программе
     * измеряются в повторах. Требовать от человека произнести единицу было бы
     * требованием произносить то, что уже написано в его же программе.
     */
    private fun unitFor(text: String, exercise: Programme.Exercise): String = when {
        SECOND_MARKERS.any { text.contains(it) } -> "сек"
        REP_MARKERS.any { text.contains(it) } -> "повт"
        else -> exercise.measure
    }

    /** «12.0» → «12»: дробные нули в конспекте мешают. */
    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /**
     * Оценка совпадения фразы с названием упражнения.
     *
     * Считается лучший **вариант имени**, а не всё название целиком. Название из
     * документа длинное и уточняющее: «Tuck Front Lever (Передний вис в группировке)».
     * Человек говорит «передний вис», и делить совпадения на семь токенов полного
     * названия нельзя — совпадение тонет. Вариант «Передний вис в группировке»
     * даёт 2/3, и это уже уверенное попадание.
     *
     * Обрезка токенов до 6 символов — не здесь, а в [TokenOverlapSearch]: без неё
     * «отжимания» из названия не находилось в фразе «двенадцать отжиманий».
     */
    private fun bestVariantScore(text: String, title: String): Double =
        nameVariants(title).maxOfOrNull { TokenOverlapSearch.score(it, text) } ?: 0.0

    /** «Tuck Front Lever (Передний вис в группировке)» → варианты имени. */
    private fun nameVariants(title: String): List<String> =
        title.split("(", ")", "/", "—", "·", "-")
            .map { it.trim() }
            .filter { it.length >= 3 }
            .ifEmpty { listOf(title) }
}