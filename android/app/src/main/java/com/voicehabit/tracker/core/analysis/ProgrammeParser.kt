package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.Programme
import java.time.LocalDate
import java.util.Locale

/**
 * P1. Черновик программы — результат разбора текста, ещё без id и без привязки к привычкам.
 *
 * [warnings] показывается пользователю перед сохранением: программа, из которой
 * вытащили четыре тренировочных дня, должна об этом сказать, а не молча выглядеть
 * готовой.
 */
data class ProgrammeDraft(
    val title: String = "",
    val athleteNote: String = "",
    val goals: String = "",
    val days: List<Programme.Day> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val trainingDays: List<Programme.Day> get() = days.filter { !it.isRest }
    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * P1. Разбор тренировочной программы из обычного текста.
 *
 * ## Почему без сети
 *
 * Ровно та же логика, что у [IntentRouter] и [DigestBuilder]: программу вставляют
 * из мессенджера или из документа, и в этот момент сети может не быть. Кроме того,
 * разбор таблиц — задача с ответами, а не с рассуждением: облачная модель тут
 * хуже правил, потому что не может гарантировать, что не потеряет строку.
 *
 * ## Что понимается на входе
 *
 * Текст из `.docx` (таблицы приходят строками с `|`), markdown-таблицы (`| … |`) и
 * простой текст с заголовками. Порядок колонок не фиксируется: сначала ищутся
 * ячейки-признаки (номер упражнения, «N × M», «Отдых …»), и только потом
 * оставшиеся ячейки раскладываются в «упражнение / площадка / дом».
 *
 * ## Чего парсер не делает
 *
 * Не выдумывает упражнений. Если в тексте нет ни одного распознанного упражнения,
 * результат пустой и в [ProgrammeDraft.warnings] объяснение — приложение не должно
 * показывать человеку программу, которой он не занимался.
 */
object ProgrammeParser {

    private val WEEKDAY_ALIASES: Map<String, Int> = mapOf(
        "понедельник" to 1, "пн" to 1, "понедельника" to 1,
        "вторник" to 2, "вт" to 2, "вторника" to 2,
        "среда" to 3, "среду" to 3, "ср" to 3, "среды" to 3,
        "четверг" to 4, "чт" to 4, "четверга" to 4,
        "пятница" to 5, "пятницу" to 5, "пт" to 5, "пятницы" to 5,
        "суббота" to 6, "сб" to 6, "субботу" to 6,
        "воскресенье" to 7, "вс" to 7, "воскресенья" to 7
    )

    private val REST_MARKERS = listOf(
        "день отдыха", "выходной", "отдых", "восстановление", "суперкомпенсация", "перезагрузка"
    )

    /**
     * «4 подхода × 8–12 сек», «4 × 6–8 повт», «5 подходов × 20–30 сек».
     *
     * Окончания пишутся как `[\\p{L}\\d_]*`, а не `\\w*`: в Java `\\w` — это
     * `[a-zA-Z_0-9]` и кириллицу не покрывает. Из-за этого «подхода» обрезалось до
     * «подход», висела «а», и **все статические упражнения** («4 подхода × 8–12 сек»)
     * теряли рецепт, хотя силовые («4 × 6–8 повт») разбирались.
     */
    private val SETS_PATTERN = Regex(
        "(\\d{1,2})\\s*(?:подход[а-я]*|сет[а-я]*|series)?\\s*[×xх✕]\\s*(\\d{1,3})" +
            "(?:\\s*[-–—]\\s*(\\d{1,3}))?\\s*([а-яa-z%]*)"
    )

    /** «4 × макс (цель: 15-20с)» — у статики повторов нет, только время удержания. */
    private val SETS_MAX_PATTERN = Regex(
        "(\\d{1,2})\\s*(?:подход[а-я]*|сет[а-я]*)?\\s*[×xх✕]\\s*макс", RegexOption.IGNORE_CASE
    )

    private val SETS_ONLY_PATTERN = Regex(
        "^(\\d{1,2})\\s*(?:подход[а-я]*|сет[а-я]*)?\\s*(\\d{1,3})\\s*([а-яa-z%]*)"
    )

    private val POSITION_PATTERN = Regex("^\\d{1,2}$")

    /** Номер пункта в начале строки: «2. Чистый выход силой». */
    private val LEADING_ITEM_NUMBER = Regex("^\\d{1,2}\\.\\s*")

    /** Ведущее слово «Темп» в ячейке темпа: инлайн-(?i) тут не работает по кириллице. */
    private val TEMPO_PREFIX = Regex("(?iu)\\s*темп\\s*")

    /** «5 подходов × 20–30 сек | Отдых 2.5 мин» — то же с `\\w`, что и выше. */
    private val MEASURE_AFTER_NUMBER = Regex("(\\d{1,3})\\s*([а-я]{1,6})")

    private val GOAL_KEYS = listOf(
        "рост", "вес", "имт", "уровень элементов", "силовой базис",
        "текущий силовой базис", "график тренировок", "главные цели", "возраст", "опыт"
    )

    private val MEASURE_BY_UNIT = mapOf(
        "сек" to "сек", "с" to "сек", "секунд" to "сек", "секунда" to "сек", "секунды" to "сек",
        "мин" to "мин", "м" to "мин", "минут" to "мин", "минуты" to "мин",
        "повт" to "повт", "повто" to "повт", "повторы" to "повт", "повторений" to "повт",
        "раз" to "повт", "раза" to "повт", "разы" to "повт",
        "км" to "м", "кг" to "кг"
    )

    /** `[Текущие 8 сек -> Цель: 20 сек]` — прогрессия статики из документа. */
    private val PROGRESSION_PATTERN = Regex(
        "(?:текущие|сейчас|исходно|старт)?\\s*(\\d{1,3}(?:[.,]\\d)?)\\s*([а-яa-z]+)?\\s*" +
            "(?:->|→|—>|➔)\\s*(?:цель|целевая|до)?\\s*:?\\s*(\\d{1,3}(?:[.,]\\d)?)\\s*([а-яa-z]+)?",
        RegexOption.IGNORE_CASE
    )

    // ── Точка входа ────────────────────────────────────────────────────────────────

    fun parse(raw: String): ProgrammeDraft {
        val text = normalize(raw)
        if (text.isBlank()) {
            return ProgrammeDraft(warnings = listOf("Текст пустой — вставьте программу целиком."))
        }

        val lines = text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        val warnings = mutableListOf<String>()

        val athleteNote = extractAthleteNote(lines)
        val goals = extractGoals(text)
        val days = extractDays(lines, text)
        val title = extractTitle(lines) ?: "Тренировочная программа"

        if (days.isEmpty()) {
            warnings += "Не нашлось ни одного дня тренировки. Похоже, это не расписание Пн/Ср/Пт."
        }
        val withoutExercises = days.count { !it.isRest && it.exercises.isEmpty() }
        if (withoutExercises > 0) {
            warnings += "У $withoutExercises ${dayWord(withoutExercises)} нет распознанных упражнений — таблица, видимо, без номеров."
        }
        val trainingWeekdays = days.filter { !it.isRest }.map { it.weekday }.toSet()
        if (trainingWeekdays.isEmpty() && days.isNotEmpty()) {
            warnings += "Все дни оказались днями отдыха — проверьте текст программы."
        }

        return ProgrammeDraft(
            title = title,
            athleteNote = athleteNote,
            goals = goals,
            days = days,
            warnings = warnings
        )
    }

    // ── Дни ────────────────────────────────────────────────────────────────────────

    private fun extractDays(lines: List<String>, sourceText: String): List<Programme.Day> {
        val days = mutableListOf<Programme.Day>()
        var current: DraftDay? = null

        for (line in lines) {
            val header = parseDayHeader(line)
            if (header != null) {
                // День с тем же номером недели заменяет предыдущий: в документе
                // один и тот же Пн встречается и в таблице недели, и заголовком раздела.
                val existingIndex = days.indexOfFirst { it.weekday == header.weekday }
                if (existingIndex >= 0) {
                    val old = days[existingIndex]
                    days[existingIndex] = old.copy(
                        title = mergeTitles(old.title, header.title),
                        focusNote = header.focusNote.ifBlank { old.focusNote }
                    )
                    current = days[existingIndex].let { DraftDay(it.weekday, it.title, it.isRest) }
                    // Не continue: строка «Пн | Тяга | 1 | Подтягивания» является
                    // заголовком дня И строкой упражнения одновременно.
                } else {
                    days += Programme.Day(
                        id = "",
                        programmeId = "",
                        weekday = header.weekday,
                        title = header.title,
                        focusNote = header.focusNote,
                        isRest = header.isRest
                    )
                    current = DraftDay(header.weekday, header.title, header.isRest)
                }
                // Не continue: строка «Пн | Тяга | 1 | Подтягивания» является
                // заголовком дня И строкой упражнения одновременно.
            }

            val exercise = parseExerciseRow(line) ?: continue
            if (current == null) {
                // Упражнение до первого заголовка дня: привязываем к первому
                // неотмеченному дню, чтобы не потерять таблицу без заголовка.
                if (days.isEmpty()) return emptyList()
                current = DraftDay(days.first().weekday, days.first().title, days.first().isRest)
            }
            val index = days.indexOfFirst { it.weekday == current!!.weekday }
            if (index < 0) continue
            val day = days[index]
            days[index] = day.copy(exercises = day.exercises + exercise)
        }

        val withProgression = applyProgressions(days, sourceText)
        return withProgression.sortedBy { it.position }
    }

    private data class DraftDay(val weekday: Int, val title: String, val isRest: Boolean)

    private data class DayHeader(val weekday: Int, val title: String, val focusNote: String, val isRest: Boolean)

    /**
     * Распознаёт строку-заголовок дня.
     *
     * Ловит и «Понедельник — День 1: Тяга (Pull & Quads)», и строку таблицы
     * `Понедельник | День 1: … | Старт недели`, и одиночное «Среда».
     */
    private fun parseDayHeader(line: String): DayHeader? {
        val firstCell = line.split('|').firstOrNull()?.trim().orEmpty().trim('#', '*', ' ')
        if (firstCell.isBlank()) return null
        val lower = firstCell.lowercase(Locale.getDefault())

        // Заголовок раздела «Понедельник - День 1: Тяга + Передний вис (Pull & Quads)»
        // длиннее 40 символов, и прежний фильтр по длине его отбрасывал: все 18
        // упражнений падали в последний день недели. Отсекать нужно по признаку
        // «строка начинается с названия дня», а не по длине.
        val weekday = WEEKDAY_ALIASES.entries.firstOrNull { (name, _) ->
            lower == name ||
                lower.startsWith("$name ") ||
                lower.startsWith("$name:") ||
                lower.startsWith("$name-") ||
                lower.startsWith("$name—")
        }?.value ?: return null

        // Отдых определяется только по ячейкам, которые описывают **день**: названию
        // дня и его характеру. Раньше проверялась вся строка, и «Отдых 90 сек» из
        // колонки темпа делал тренировочный Пн днём отдыха — программа целиком
        // превращалась в четыре дня отдыха и ни одной тренировки.
        val headerCells = line.split('|').take(3).joinToString(" ").lowercase(Locale.getDefault())
        val hasTrainingNumber = Regex("(?iu)день\\s*\\d+\\s*[:—-]").containsMatchIn(headerCells)
        val isRest = !hasTrainingNumber && REST_MARKERS.any { headerCells.contains(it) }

        val title = extractDayTitle(line, weekday)
        val focusNote = extractFocusNote(line)
        return DayHeader(weekday, title, focusNote, isRest)
    }

    private fun extractDayTitle(line: String, weekday: Int): String {
        val cells = line.split('|').map { it.trim() }.filter { it.isNotBlank() }
        val first = cells.firstOrNull().orEmpty()
        val withoutWeekday = WEEKDAY_ALIASES.entries
            .firstOrNull { (name, value) -> value == weekday && first.lowercase().startsWith(name) }
            ?.key
            ?.let { name -> first.substring(name.length).trim().trimStart('—', '–', '-', ':', ' ') }
            .orEmpty()

        val source = withoutWeekday.ifBlank { cells.getOrNull(1).orEmpty() }
        // «День 1: Тяга + Передний вис» → «Тяга, Передний вис»: «День N» — это номер
        // в расписании, а не часть названия, и в списке он только шумит.
        return source
            .replace(Regex("^день\\s*\\d+\\s*[:—-]?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[*#]+"), "")
            .trim()
            .ifBlank { first.trim().ifBlank { "День $weekday" } }
            .take(70)
    }

    private fun extractFocusNote(line: String): String {
        val cells = line.split('|').map { it.trim() }.filter { it.isNotBlank() }
        // Последняя ячейка строки таблицы — обычно режим восстановления, а заголовок
        // дня без таблицы несёт пояснение в скобках.
        cells.getOrNull(2)?.takeIf { it.length in 4..90 }
            ?.takeUnless { it.contains("день отдыха", ignoreCase = true) }
            ?.let { return it }
        return cells.getOrNull(1)
            ?.takeIf { it.length in 6..90 && !it.contains("день отдыха", ignoreCase = true) }
            .orEmpty()
    }

    private fun mergeTitles(existing: String, incoming: String): String =
        if (existing.isBlank() || incoming.isBlank()) existing.ifBlank { incoming }
        else if (existing.equals(incoming, ignoreCase = true)) existing
        else existing.take(70)

    // ── Упражнения ─────────────────────────────────────────────────────────────────

    /**
     * Строка упражнения начинается с порядкового номера в своей ячейке: «1 | Подтягивания…».
     * Именно номер, а не «похоже на упражнение» — иначе в таблицу упражнений попадали бы
     * строки расписания недели.
     */
    private fun parseExerciseRow(line: String): Programme.Exercise? {
        val cells = line.split('|').map { it.trim() }.filter { it.isNotBlank() }
        if (cells.size < 2) return null

        // Номер упражнения ищется в любой ячейке, а не строго в первой: в таблице
        // «Пн | Тяга | 1 | Подтягивания» день занимает первую колонку, и строгое
        // требование «номер в первой ячейке» молча теряло весь день.
        val positionIndex = cells.indexOfFirst { cell ->
            POSITION_PATTERN.matches(cell.replace("*", "").replace("#", "").trim())
        }
        if (positionIndex < 0) return null
        val first = cells[positionIndex].replace("*", "").replace("#", "").trim()

        val rest = cells.drop(positionIndex + 1)
        if (rest.isEmpty()) return null

        var title = ""
        var prescriptionCell: String? = null
        var tempoCell: String? = null
        val variants = mutableListOf<String>()

        for (cell in rest) {
            when {
                SETS_PATTERN.containsMatchIn(cell) ||
                    SETS_MAX_PATTERN.containsMatchIn(cell) ||
                    SETS_ONLY_PATTERN.matches(cell) ->
                    if (prescriptionCell == null) prescriptionCell = cell
                looksLikeTempo(cell) -> if (tempoCell == null) tempoCell = cell
                title.isBlank() -> title = cell
                else -> variants += cell
            }
        }
        if (title.isBlank()) return null

        val sets = parseSets(prescriptionCell)
        val tempo = parseTempoRest(tempoCell)

        return Programme.Exercise(
            id = "",
            dayId = "",
            position = first.toIntOrNull()?.minus(1) ?: 0,
            title = title.replace(Regex("[*#]+"), "").trim().take(90),
            outdoor = variants.getOrNull(0)?.takeIf { variants.size >= 2 }.orEmpty(),
            home = variants.getOrNull(1)?.takeIf { variants.size >= 2 }
                ?: variants.getOrNull(0)?.orEmpty().orEmpty(),
            sets = sets.sets,
            repsMin = sets.repsMin,
            repsMax = sets.repsMax,
            measure = sets.measure,
            tempo = tempo.tempo,
            restSec = tempo.restSec,
            note = prescriptionCell.orEmpty().take(120)
        )
    }

    private fun looksLikeTempo(cell: String): Boolean {
        val lower = cell.lowercase(Locale.getDefault())
        return lower.contains("отдых") || lower.contains("темп") ||
            Regex("(?<!\\d)\\d-\\d-\\d(?!\\d)").containsMatchIn(cell)
    }

    private data class Sets(val sets: Int, val repsMin: Int, val repsMax: Int, val measure: String)

    private fun parseSets(cell: String?): Sets {
        if (cell.isNullOrBlank()) return Sets(1, 0, 0, "повт")
        SETS_MAX_PATTERN.find(cell)?.let { m ->
            // Повторов нет, но единица измерения из «(цель: 15-20с)» ещё полезна.
            val measure = MEASURE_AFTER_NUMBER.find(cell)
                ?.groupValues?.get(2)
                ?.let { MEASURE_BY_UNIT[it.lowercase(Locale.getDefault())] }
                ?: "сек"
            return Sets(m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1, 0, 0, measure)
        }
        SETS_PATTERN.find(cell)?.let { m ->
            return Sets(
                sets = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1,
                repsMin = m.groupValues[2].toIntOrNull() ?: 0,
                repsMax = m.groupValues[3].toIntOrNull() ?: m.groupValues[2].toIntOrNull() ?: 0,
                measure = measureOf(m.groupValues[4], m.groupValues[2])
            )
        }
        SETS_ONLY_PATTERN.find(cell)?.let { m ->
            return Sets(
                sets = m.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1,
                repsMin = m.groupValues[2].toIntOrNull() ?: 0,
                repsMax = m.groupValues[2].toIntOrNull() ?: 0,
                measure = measureOf(m.groupValues[3], m.groupValues[2])
            )
        }
        return Sets(1, 0, 0, "повт")
    }

    /**
     * Единица измерения берётся из хвоста ячейки, а не из всего текста.
     *
     * Иначе «5 подходов × 20–30 сек | Отдых 90 сек» дала бы «мин» от соседней
     * колонки, и статика превратилась бы в минуты.
     */
    private fun measureOf(unitHint: String, reps: String): String {
        val hint = unitHint.trim().lowercase(Locale.getDefault()).take(6)
        MEASURE_BY_UNIT[hint]?.let { return it }
        // «15–20с» приклеен к числу: «20с» → «сек».
        MEASURE_AFTER_NUMBER.find(reps)?.let { m ->
            MEASURE_BY_UNIT[m.groupValues[2].lowercase(Locale.getDefault())]?.let { return it }
        }
        return "повт"
    }

    private data class Tempo(val tempo: String, val restSec: Int)

    /**
     * «Темп 2-0-3Отдых 2.5 мин», «СтатикаОтдых 2 мин», «Отдых 90 сек».
     *
     * Темп и отдых часто склеены без пробела — в исходной таблице это одна ячейка,
     * и человек не ставил между ними разделитель.
     */
    private fun parseTempoRest(cell: String?): Tempo {
        if (cell.isNullOrBlank()) return Tempo("", 0)
        val lower = cell.lowercase(Locale.getDefault())
        val splitAt = lower.indexOf("отдых")
        if (splitAt < 0) {
            val tempo = cell.replace(Regex("(?<!\\d)(\\d-\\d-\\d)(?!\\d)"), "$1").trim()
            return Tempo(tempo.take(20), 0)
        }
        val tempoPart = cell.substring(0, splitAt)
            .replace(TEMPO_PREFIX, "")
            .trim()
            .take(20)
        val restPart = cell.substring(splitAt)
        val restSec = parseRestSeconds(restPart)
        return Tempo(tempoPart, restSec)
    }

    private fun parseRestSeconds(text: String): Int {
        val lower = text.lowercase(Locale.getDefault()).replace(',', '.')
        val number = Regex("(\\d+(?:[.,]\\d+)?)").find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: return 0
        val isMinutes = lower.contains("мин") || lower.contains("min")
        return if (isMinutes) (number * 60).toInt().coerceIn(0, 3600) else number.toInt().coerceIn(0, 3600)
    }

    // ── Прогрессия ─────────────────────────────────────────────────────────────────

    /**
     * Навешивает «текущее → цель» на упражнение с тем же названием.
     *
     * В документе прогрессия вынесена в отдельный раздел («L-sit … [Текущие 8 сек ->
     * Цель: 20 сек]»), а упражнение живёт в таблице дня. Связь только по названию,
     * поэтому она по пересечению токенов, а не по порядку.
     */
    private fun applyProgressions(days: List<Programme.Day>, sourceText: String): List<Programme.Day> {
        val pairs: List<Progression> = extractProgressions(sourceText)
        if (pairs.isEmpty()) return days
        return days.map { day ->
            day.copy(
                exercises = day.exercises.map { exercise ->
                    val value = pairs.firstOrNull { it.label.isNotBlank() &&
                            TokenOverlapSearch.score(it.label, exercise.title) >= 0.34
                    } ?: return@map exercise
                    exercise.copy(currentValue = value.current, targetValue = value.target)
                }
            )
        }
    }

    private fun extractProgressions(text: String): List<Progression> {
        val out = mutableListOf<Progression>()
        PROGRESSION_PATTERN.findAll(text).forEach { m ->
            val current = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@forEach
            val target = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: return@forEach
            if (target <= 0 || current <= 0) return@forEach
            // Название стоит перед стрелкой: «L-sit (Уголок на полу) [Текущие 8 сек ->…».
            val label = text.substring(maxOf(0, m.range.first - 90), m.range.first)
                .replace(Regex("[\\[\\(].*$"), "")
                .trim()
                .lines().lastOrNull()
                ?.trimStart('•', '-', ' ', '\u00A0')
                ?.trim()
                .orEmpty()
            if (label.isNotBlank()) out += Progression(label, current, target)
        }
        return out
    }

    /** «L-sit → 8 → 20»: подпись, текущий уровень, цель. */
    private data class Progression(val label: String, val current: Double, val target: Double)

    // ── Метаданные ─────────────────────────────────────────────────────────────────

    private fun extractAthleteNote(lines: List<String>): String {
        val found = mutableListOf<String>()
        for (line in lines) {
            val cells = line.split('|').map { it.trim() }.filter { it.isNotBlank() }
            for (cell in cells) {
                val lower = cell.lowercase(Locale.getDefault())
                val key = GOAL_KEYS.firstOrNull { lower.startsWith(it) } ?: continue
                val value = cell.substring(key.length).trimStart(':', '—', '–', '-', ' ').trim()
                if (value.isBlank() || value.length > 140) continue
                val normalisedKey = key.replaceFirstChar { it.uppercase() }
                    .replace("Имт", "ИМТ")
                found += "$normalisedKey: $value"
            }
        }
        return found.distinct().take(6).joinToString(" · ")
    }

    private fun extractGoals(text: String): String {
        // (?iu), а не (?i): в Java `CASE_INSENSITIVE` без `UNICODE_CASE` покрывает
        // только ASCII, поэтому «(?i)главные» молча не находило «Главные цели».
        // Kotlin-овский RegexOption.IGNORE_CASE, в отличие от инлайн-флага, с
        // кириллицей работает — но полагаться на разницу двух почти одинаковых
        // способов в одном файле нельзя.
        val marker = Regex("(?iu)главные\\s+цели").find(text) ?: return ""
        val tail = text.substring(marker.range.last + 1).take(400)
        // Пункты часто склеены без разделителя («…(+5–7 кг)2. Чистый выход силой…»),
        // поэтому режем текст по цифре с точкой, а не по строкам.
        return tail.split(Regex("(?<=\\D)(?=\\d\\.\\s)"))
            .map { part -> part.trim().removePrefix("|").trim() }
            // Номер снимается у каждого пункта: индекс в списке не совпадает с
            // напечатанным номером, потому что первым идёт разделитель колонки.
            .map { it.replace(LEADING_ITEM_NUMBER, "").take(90) }
            .filter { it.length > 3 }
            // Хвост пункта обрезается по следующей колонке или строке таблицы:
            // иначе в цель попадал заголовок следующего раздела.
            .map { it.substringBefore('\n').substringBefore(" | ").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)
            .joinToString(" · ")
    }

    private fun extractTitle(lines: List<String>): String? {
        // Заголовок документа: первая непустая строка без разделителя, до 60 символов,
        // не начинающаяся с цифры-позиции и не состоящая только из заглавных букв таблицы.
        for (line in lines.take(6)) {
            val clean = line.replace(Regex("[#*]+"), "").trim()
            if (clean.isBlank() || clean.length > 60) continue
            if (clean.contains('|')) continue
            if (POSITION_PATTERN.matches(clean)) continue
            if (WEEKDAY_ALIASES.containsKey(clean.lowercase(Locale.getDefault()))) continue
            if (REST_MARKERS.any { clean.lowercase().contains(it) }) continue
            if (Regex("^[а-яёa-z]+\\s+[а-яёa-z]+\\s*$", RegexOption.IGNORE_CASE).matches(clean)) continue
            return clean
        }
        return null
    }

    // ── Утилиты ────────────────────────────────────────────────────────────────────

    /**
     * Нормализация перед разбором.
     *
     * Тире приводится к дефису, потому что в тексте из `.docx` встречаются все три
     * вида, и шаблоны для них пришлось бы дублировать в каждом. Неразрывный пробел
     * убирается: он невидим, но ломает `trim()` и сравнение ячеек.
     */
    private fun normalize(raw: String): String = raw
        .replace(' ', ' ')
        .replace('—', '-')
        .replace('–', '-')
        .replace('‑', '-')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun dayWord(count: Int): String = when (count % 10) {
        1 -> if (count % 100 == 11) "дней" else "дня"
        2, 3, 4 -> if (count % 100 in 12..14) "дней" else "дня"
        else -> "дней"
    }

    /** Сегодняшний день недели — для подсветки в списке. */
    fun todayWeekday(date: LocalDate = LocalDate.now()): Int = date.dayOfWeek.value
}
