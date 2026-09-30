package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1. Голосовая отметка прогресса в тренировке.
 *
 * ## Ключевая проверка
 *
 * «двенадцать отжиманий» должно попасть в **Отжимания на брусьях**, а не в первое
 * упражнение дня. Названия упражнений в программе длинные и с уточнениями в скобках,
 * поэтому подбор идёт по общему слову, а не по всему названию.
 */
class ProgrammeVoiceResolverTest {

    private val monday = Programme.Day(
        id = "d1",
        programmeId = "p1",
        weekday = 1,
        title = "Тяга",
        focusNote = "Спина и бицепс",
        exercises = listOf(
            Programme.Exercise(
                id = "e1", dayId = "d1", position = 0,
                title = "Tuck Front Lever (Передний вис в группировке)",
                sets = 4, repsMin = 8, repsMax = 12, measure = "сек"
            ),
            Programme.Exercise(
                id = "e2", dayId = "d1", position = 1,
                title = "Силовые подтягивания с весом",
                sets = 4, repsMin = 6, repsMax = 8, measure = "повт"
            ),
            Programme.Exercise(
                id = "e3", dayId = "d1", position = 2,
                title = "Отжимания на брусьях",
                sets = 4, repsMin = 8, repsMax = 10, measure = "повт"
            )
        )
    )

    @Test
    fun `повторы попадают в нужное упражнение`() {
        val result = ProgrammeVoiceResolver.resolve("сделал двенадцать отжиманий", monday)
        assertNotNull("Фраза про отжимания должна что-то найти", result)
        assertEquals("Отжимания на брусьях", result!!.exercise.title)
        assertEquals(12.0, result.number!!, 0.001)
        assertEquals("повт", result.unit)
        assertFalse(result.completesWholeDay)
    }

    @Test
    fun `подтягивания не путаются с передним висом`() {
        val result = ProgrammeVoiceResolver.resolve("подтягивания восемь", monday)!!
        assertTrue(
            "Должно быть подтягивание, а не «${result.exercise.title}»",
            result.exercise.title.contains("подтягивания")
        )
        assertEquals(8.0, result.number!!, 0.001)
    }

    @Test
    fun `секунды читаются как удержание`() {
        // Человек называет уточнение из скобок, а не полное название из документа.
        val result = ProgrammeVoiceResolver.resolve("передний вис двадцать секунд", monday)
        assertNotNull("«Передний вис в группировке» должен найтись по уточнению", result)
        assertEquals("сек", result!!.unit)
        assertEquals(20.0, result.number!!, 0.001)
    }

    @Test
    fun `подходы от повторов отличаются`() {
        val result = ProgrammeVoiceResolver.resolve("сделал четыре подхода по восемь отжиманий", monday)!!
        assertEquals("Подходы названы отдельно от повторов", 4, result.sets)
        assertEquals(8.0, result.number!!, 0.001)
    }

    @Test
    fun `подход без числа не выдумывает повторы`() {
        val result = ProgrammeVoiceResolver.resolve("подтягивания сделал четыре подхода", monday)!!
        assertEquals(4, result.sets)
        assertNull("Повторов не названо — выдумывать их нельзя", result.number)
    }

    @Test
    fun `фраза без числа отмечает один подход`() {
        val result = ProgrammeVoiceResolver.resolve("подтягивания сделал", monday)!!
        assertTrue(result.isSetOnly)
        assertNull(result.number)
    }

    @Test
    fun `закрытие дня целиком сильнее отдельного упражнения`() {
        val result = ProgrammeVoiceResolver.resolve("тренировка сделана", monday)!!
        assertTrue(result.completesWholeDay)
        assertNull(result.number)
        assertTrue(result.reason.contains("целиком"))
    }

    @Test
    fun `в день отдыха голос ничего не пишет`() {
        val rest = monday.copy(isRest = true, exercises = emptyList())
        assertNull(ProgrammeVoiceResolver.resolve("сделал двенадцать отжиманий", rest))
        assertNull(ProgrammeVoiceResolver.resolve("тренировка сделана", rest))
    }

    @Test
    fun `без сегодняшнего дня голос молчит`() {
        assertNull(ProgrammeVoiceResolver.resolve("сделал двенадцать отжиманий", null))
    }

    @Test
    fun `пустая фраза не разбирается`() {
        assertNull(ProgrammeVoiceResolver.resolve("   ", monday))
    }

    @Test
    fun `непро тренировка не превращается в отметку`() {
        assertNull(
            "Купил хлеб и сходил в магазин — это не упражнение",
            ProgrammeVoiceResolver.resolve("купил хлеб и сходил в магазин", monday)
        )
    }

    @Test
    fun `причина показывается пользователю`() {
        val result = ProgrammeVoiceResolver.resolve("сделал двенадцать отжиманий", monday)!!
        assertTrue(result.reason.contains("Отжимания"))
        assertTrue(result.reason.contains("12"))
    }
}