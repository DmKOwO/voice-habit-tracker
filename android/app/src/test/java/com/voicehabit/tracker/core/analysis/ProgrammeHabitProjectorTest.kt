package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1. Проекция программы на привычки.
 *
 * ## Что здесь защищается
 *
 * Требование пользователя: «если задачи в Пн/Ср/Пт — обязательно надо, чтобы
 * именно в эти дни оно работало». Это единственное место в проекте, где решается
 * `scheduleDays` программы, поэтому проверяется максимально придирчиво.
 */
class ProgrammeHabitProjectorTest {

    private fun day(
        id: String,
        weekday: Int,
        title: String,
        note: String = "",
        rest: Boolean = false
    ) = Programme.Day(
        id = id,
        programmeId = "p1",
        weekday = weekday,
        title = title,
        focusNote = note,
        isRest = rest
    )

    /** Сплит 3/4/7 — тот самый документ. */
    private val split = Programme(
        id = "p1",
        title = "Программа калистеники",
        athleteNote = "173 см, 55 кг",
        goals = "Набор массы",
        sourceText = "текст программы",
        days = listOf(
            day("d1", 1, "Тяга и передний вис", "Спина и бицепс"),
            day("d2", 2, "Отдых", rest = true),
            day("d3", 3, "Жим и стойка на руках", "Грудь и плечи"),
            day("d4", 4, "Отдых", rest = true),
            day("d5", 5, "Выход силой и статика", "Ноги и корпус"),
            day("d6", 6, "Отдых", rest = true),
            day("d7", 7, "Отдых", rest = true)
        )
    )

    @Test
    fun `у каждого дня своё расписание`() {
        val plan = ProgrammeHabitProjector.plan(split)
        assertEquals("Три тренировочных дня", 3, plan.size)
        assertEquals(listOf(1, 3, 5), plan.map { it.weekday })
        plan.forEach { projection ->
            assertEquals(
                "День ${projection.weekday} активен только в свой день, а не во все три",
                setOf(projection.weekday),
                projection.scheduleDays
            )
            assertEquals("Частота не может быть ежедневной", "WEEKLY", projection.frequency)
        }
        // Объединение расписаний всех карточек — ровно Пн/Ср/Пт из документа.
        assertEquals(setOf(1, 3, 5), plan.flatMap { it.scheduleDays }.toSet())
    }

    /**
     * Регрессия: при общем расписании в среду пульс показывал 0/3 — требовалось
     * отметить тягу, жим и выход силой за один день.
     */
    @Test
    fun `в среду активна только среда`() {
        val wednesday = ProgrammeHabitProjector.plan(split).first { it.weekday == 3 }
        assertEquals(setOf(3), wednesday.scheduleDays)
        assertFalse("В среду не должно быть активной понедельничной сессии", 1 in wednesday.scheduleDays)
        assertFalse(5 in wednesday.scheduleDays)
    }

    @Test
    fun `дни отдыха не становятся привычками`() {
        val plan = ProgrammeHabitProjector.plan(split)
        assertFalse("Вторник — отдых, он не тренируется", plan.any { it.weekday == 2 })
        assertTrue(plan.none { it.weekday in setOf(2, 4, 6, 7) })
    }

    /**
     * Регрессия: пустой `scheduleDays` в привычке означает «каждый день».
     * Программа без распознанных дней не должна превращаться в ежедневную привычку.
     */
    @Test
    fun `программа без тренировочных дней не проецируется вовсе`() {
        val empty = split.copy(
            days = listOf(day("d1", 1, "Тренировка", rest = true), day("d2", 2, "Отдых", rest = true))
        )
        assertTrue(
            "Без дней тренировки привычка была бы ежедневной",
            ProgrammeHabitProjector.plan(empty).isEmpty()
        )
    }

    @Test
    fun `редкий сплит показывается серией`() {
        val plan = ProgrammeHabitProjector.plan(split)
        plan.forEach { assertEquals("3 тренировки в неделю — это серия", "STREAKS", it.displayType) }
        assertTrue(plan.first().reason.contains("непрерывность"))
    }

    @Test
    fun `частый график показывается сеткой`() {
        val often = split.copy(
            days = (1..7).map { day("d$it", it, "Тренировка $it") }
        )
        val plan = ProgrammeHabitProjector.plan(often)
        assertEquals("У семи дней расписание по одному дню", setOf(1), plan.first().scheduleDays)
        assertEquals(setOf(7), plan.last().scheduleDays)
        assertEquals("7 дней — ежедневная частота", "DAILY", plan.first().frequency)
        assertEquals("DAILY_CHECK", plan.first().displayType)
    }

    @Test
    fun `шестидневный сплит идёт по сетке`() {
        val six = split.copy(days = (1..6).map { day("d$it", it, "Тренировка $it") })
        assertEquals("GRID", ProgrammeHabitProjector.plan(six).first().displayType)
    }

    /** Требование «возможность внедрить в существующую привычку». */
    @Test
    fun `похожая существующая привычка переиспользуется`() {
        val mine = Habit(id = "h1", title = "Тяга и передний вис", tags = listOf("зал"))
        val plan = ProgrammeHabitProjector.plan(split, existingHabits = listOf(mine))
        val monday = plan.first { it.weekday == 1 }
        assertEquals("h1", monday.existingHabitId)
        assertTrue(monday.isReused)
        assertEquals("встроим в существующую", monday.action)
        // Чужой день не должен цепляться к чужой привычке.
        assertNull(plan.first { it.weekday == 3 }.existingHabitId)
    }

    @Test
    fun `привычка этой же программы переиспользуется даже при другом названии`() {
        val mine = Habit(
            id = "h9",
            title = "Моя тренировка",
            tags = listOf("зал", ProgrammeHabitProjector.tagFor("p1"))
        )
        val plan = ProgrammeHabitProjector.plan(split, existingHabits = listOf(mine))
        plan.forEach {
            assertEquals("Все дни одной программы идут в одну карточку пользователя", "h9", it.existingHabitId)
        }
    }

    @Test
    fun `явная связь дня с привычкой сильнее любого совпадения`() {
        val other = Habit(id = "h1", title = "Тяга и передний вис")
        val plan = ProgrammeHabitProjector.plan(
            split,
            existingHabits = listOf(other),
            linkedHabitIds = mapOf("d1" to "h1")
        )
        assertEquals("h1", plan.first { it.weekday == 1 }.existingHabitId)
    }

    @Test
    fun `чужая привычка не подхватывается`() {
        val mine = Habit(id = "h1", title = "Читать книги")
        val plan = ProgrammeHabitProjector.plan(split, existingHabits = listOf(mine))
        assertTrue(plan.none { it.existingHabitId == "h1" })
    }

    @Test
    fun `у новой привычки теги связывают её с программой`() {
        val tags = ProgrammeHabitProjector.tagsFor(split, split.days[0])
        assertTrue(tags.contains(ProgrammeHabitProjector.tagFor("p1")))
        assertEquals("p1", ProgrammeHabitProjector.programmeIdOf(tags))
        assertTrue("В тегах должно быть имя программы", tags.contains("Программа калистеники"))
        assertTrue(ProgrammeHabitProjector.isFromProgramme(tags))
    }

    @Test
    fun `теги обычной привычки не считаются программными`() {
        assertFalse(ProgrammeHabitProjector.isFromProgramme("зал, дом"))
        assertNull(ProgrammeHabitProjector.programmeIdOf("зал, prog-1"))
    }

    @Test
    fun `название привычки берётся из дня, а не из номера`() {
        val plan = ProgrammeHabitProjector.plan(split)
        assertEquals("Тяга и передний вис", plan.first().habitTitle)
        plan.forEach { assertNotNull(it.habitTitle) }
    }

    @Test
    fun `у каждого дня свой цвет из палитры приложения`() {
        val colors = ProgrammeHabitProjector.plan(split).map { it.colorHex }
        assertEquals("Три тренировки — три разных цвета", 3, colors.distinct().size)
    }

    @Test
    fun `существующую привычку не перекрашиваем и не переименовываем`() {
        val mine = Habit(id = "h1", title = "Моя тяга", colorHex = "#123456", category = "Work")
        val monday = ProgrammeHabitProjector.plan(split, listOf(mine)).first { it.weekday == 1 }
        assertEquals("#123456", monday.colorHex)
        assertEquals("Моя тяга", monday.habitTitle)
        assertEquals("Work", monday.category)
    }
}