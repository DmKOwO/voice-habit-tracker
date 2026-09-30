package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Programme
import java.util.Locale

/**
 * P1. Решение о привычке, в которую проецируется тренировочный день.
 *
 * [reason] показывается пользователю: «почему это серия, а не сетка» — вопрос,
 * который человек задаёт себе каждый раз, глядя на карточку.
 */
data class HabitProjection(
    val dayId: String,
    val weekday: Int,
    /** Готовая привычка, в которую встроились. `null` — нужно создать новую. */
    val existingHabitId: String?,
    val habitTitle: String,
    val scheduleDays: Set<Int>,
    val frequency: String,
    val displayType: String,
    val category: String,
    val colorHex: String,
    val reason: String,
    val isReused: Boolean
) {
    val action: String get() = if (isReused) "встроим в существующую" else "создадим новую"
}

/**
 * P1. Проекция программы на привычки.
 *
 * ## Главное правило: у каждого дня своё расписание
 *
 * Программа «Пн/Ср/Пт» обязана дать три привычки, каждая активная **ровно в
 * свой день**: понедельник — `{1}`, среда — `{3}`, пятница — `{5}`. Общее
 * расписание на все три (`{1,3,5}` у каждой) было бы формально верным, но
 * ломало бы главное: в среду человек увидел бы сразу три невыполненных карточки
 * — тягу, жим и выход силой — и должен был бы отметить все три за один день.
 * Пульс продуктивности при этом показывал бы 0/3 вместо 0/1.
 *
 * Частота при этом остаётся `WEEKLY`: не `DAILY`, потому что тренировка не каждый
 * день. Пустое расписание в привычке означает «каждый день», поэтому программа без
 * дней тренировки **не проецируется вовсе**.
 *
 * ## Почему это чистая функция
 *
 * Решение принимается без БД и без сети, а применяется отдельно. Так его можно
 * показать пользователю **до** записи и проверить тестом: «сплит 3/4/7 → серия,
 * а не сетка» — это утверждение о логике, а не о состоянии базы.
 *
 * ## Как выбирается вид карточки
 *
 * Вид — это ответ на вопрос «что человек хочет видеть на этой карточке».
 * Для редкого графика (2–3 раза в неделю) главное — непрерывность, поэтому это
 * **серия**. Для частого (4–7) история длиннее стрика, и честнее **сетка**:
 * недельная полоса из семи точек.
 */
object ProgrammeHabitProjector {

    /** Тег, которым помечается привычка, спроецированная из программы. */
    const val TAG_PREFIX = "prog:"

    fun tagFor(programmeId: String): String = "$TAG_PREFIX$programmeId"

    fun isFromProgramme(tagsCsv: String): Boolean =
        tagsCsv.split(",").any { it.trim().startsWith(TAG_PREFIX) }

    fun programmeIdOf(tagsCsv: String): String? =
        tagsCsv.split(",")
            .firstOrNull { it.trim().startsWith(TAG_PREFIX) }
            ?.trim()
            ?.removePrefix(TAG_PREFIX)
            ?.takeIf { it.isNotBlank() }

    /**
     * Порог совпадения названий.
     *
     * Оценка [TokenOverlapSearch.score] делит на число токенов **запроса**, поэтому
     * длинное «Тяга и передний вис» против короткой привычки «Моя тяга» даёт 1/3 = 0.33.
     * Порог 0.34 отбрасывал правильное совпадение, поэтому он на один пункт ниже.
     * Чужая привычка («Читать книги») при этом даёт 0 и не подхватывается.
     */
    private const val MATCH_THRESHOLD = 0.3

    /** Палитра привычек приложения — новые тренировки не должны выбиваться из вида. */
    private val TRAINING_COLORS = listOf("#DE6B48", "#5BA872", "#C7A774", "#B8A5E3", "#F4F1EA")

    /**
     * План по всем тренировочным дням программы.
     *
     * @param existingHabits привычки пользователя — из них ищется та, в которую
     *   программа встраивается вместо создания дубликата.
     */
    fun plan(
        programme: Programme,
        existingHabits: List<Habit> = emptyList(),
        linkedHabitIds: Map<String, String> = emptyMap()
    ): List<HabitProjection> = plan(programme.days, programme.id, programme.frequency, existingHabits, linkedHabitIds)

    /**
     * План по черновику разбора — до записи в базу.
     *
     * [ProgrammeDraft] ещё не [Programme]: у него нет `id` и `frequency`, но дни и
     * название уже разобраны. Планировать нужно именно здесь, иначе человек увидит
     * последствия импорта только после того, как они произошли.
     */
    fun plan(
        draft: ProgrammeDraft,
        existingHabits: List<Habit> = emptyList()
    ): List<HabitProjection> {
        val frequency = if (draft.trainingDays.size >= 7) "DAILY" else "WEEKLY"
        return plan(draft.days, "", frequency, existingHabits, emptyMap())
    }

    private fun plan(
        days: List<Programme.Day>,
        programmeId: String,
        frequency: String,
        existingHabits: List<Habit>,
        linkedHabitIds: Map<String, String>
    ): List<HabitProjection> {
        val trainingDays = days.filter { !it.isRest && it.weekday in 1..7 }
        val weekdays = trainingDays.map { it.weekday }.toSet()
        // Нет тренировочных дней — проецировать нечего. Пустой scheduleDays сделал бы
        // привычку ежедневной, то есть ровно тем, чего человек не просил.
        if (weekdays.isEmpty()) return emptyList()

        val (plannedType, displayReason) = displayTypeReason(weekdays.size)
        val displayType = plannedType

        return trainingDays.sortedBy { it.weekday }.mapIndexed { index, day ->
            val existing = findExistingHabit(day, programmeId, existingHabits, linkedHabitIds[day.id])
            HabitProjection(
                dayId = day.id,
                weekday = day.weekday,
                existingHabitId = existing?.id,
                habitTitle = existing?.title ?: habitTitleFor(day),
                scheduleDays = setOf(day.weekday),
                frequency = frequency,
                displayType = existing?.displayType ?: displayType,
                category = existing?.category ?: "Fitness",
                colorHex = existing?.colorHex ?: TRAINING_COLORS[index % TRAINING_COLORS.size],
                reason = displayReason,
                isReused = existing != null
            )
        }
    }

    /**
     * Ищет привычку, в которую стоит встроить день.
     *
     * Порядок: уже связанная → помеченная этой программой → похожая по названию.
     * Третий шаг отвечает на «у меня уже есть Тренировка»: программа не должна
     * плодить вторую карточку с тем же названием и двумя стриками рядом.
     */
    fun findExistingHabit(
        day: Programme.Day,
        programmeId: String,
        habits: List<Habit>,
        linkedId: String? = null
    ): Habit? {
        if (linkedId != null) habits.firstOrNull { it.id == linkedId }?.let { return it }
        if (programmeId.isNotBlank()) {
            habits.firstOrNull { programmeIdOf(it.tags.joinToString(",")) == programmeId }?.let { return it }
        }

        val queries = buildList {
            if (day.title.isNotBlank()) add(day.title)
            if (day.focusNote.isNotBlank()) add(day.focusNote)
        }
        if (queries.isEmpty()) return null
        val best = habits
            .map { habit -> queries.maxOf { TokenOverlapSearch.score(it, habit.title) } to habit }
            .maxByOrNull { it.first }
        return best?.takeIf { it.first >= MATCH_THRESHOLD }?.second
    }

    /** Название привычки по дню программы. */
    fun habitTitleFor(day: Programme.Day): String = when {
        day.title.isNotBlank() -> day.title.take(48)
        day.focusNote.isNotBlank() -> day.focusNote.take(48)
        else -> "Тренировка · ${day.weekdayLabel}"
    }

    /** Вид карточки и объяснение — одним местом, чтобы UI и тест не разошлись. */
    fun displayTypeReason(sessionsPerWeek: Int): Pair<String, String> = when {
        sessionsPerWeek <= 0 -> "DAILY_CHECK" to "Нет запланированных дней — показываю как обычную отметку"
        sessionsPerWeek <= 3 -> "STREAKS" to
            "Редкий график ($sessionsPerWeek дн. в нед.): главное — непрерывность, поэтому серия"
        sessionsPerWeek <= 6 -> "GRID" to
            "Частый график ($sessionsPerWeek дн. в нед.): история длиннее стрика, поэтому сетка"
        else -> "DAILY_CHECK" to
            "Тренировка каждый день — достаточно простой отметки, без графика"
    }

    /**
     * Теги новой привычки: метка программы плюс её название.
     *
     * Имя программы в тегах нужно, чтобы человек мог найти все её дни: без него три
     * карточки «Тяга», «Жим», «Силовая» выглядят как три ничем не связанные привычки.
     */
    fun tagsFor(programme: Programme, day: Programme.Day): String {
        val parts = mutableListOf(tagFor(programme.id))
        if (programme.title.isNotBlank()) parts += programme.title.take(40)
        day.focusNote.takeIf { it.isNotBlank() && it.length <= 40 }?.let { parts += it }
        return parts.distinct().joinToString(",")
    }

    /** «Тренировка · Пн» → понятная подпись для карточки без отдельного заголовка. */
    fun fallbackTitle(weekdayLabel: String): String = "Тренировка · ${weekdayLabel.uppercase(Locale.getDefault())}"
}
