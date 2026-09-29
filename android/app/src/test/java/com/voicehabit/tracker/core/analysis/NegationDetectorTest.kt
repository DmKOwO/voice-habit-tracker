package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H88. «Я не делал зарядку» и «я делал зарядку» — противоположные факты о данных.
 * Ошибка здесь не в удобстве, а в самой базе: пользователю показывают то,
 * чего он не делал. Поэтому тесты на отрицание обязательны.
 */
class NegationDetectorTest {

    @Test
    fun `отрицание перед маркером прошедшего времени`() {
        assertTrue(NegationDetector.isNegated("сегодня я не делал зарядку"))
        assertTrue(NegationDetector.isNegated("не бегал сегодня"))
        assertTrue(NegationDetector.isNegated("не выпил воду утром"))
        assertTrue(NegationDetector.isNegated("забыл отправить отчёт"))
        assertTrue(NegationDetector.isNegated("забил на тренировку"))
        assertTrue(NegationDetector.isNegated("не успел закончить"))
    }

    @Test
    fun `положительные фразы не считаются отрицанием`() {
        assertFalse(NegationDetector.isNegated("сделал зарядку"))
        assertFalse(NegationDetector.isNegated("выпил два литра воды"))
        assertFalse(NegationDetector.isNegated("сегодня отлично потренировался"))
        assertFalse(NegationDetector.isNegated("прочитал книгу"))
        assertFalse(NegationDetector.isNegated("всё сделал"))
    }

    @Test
    fun `окно отрицания ограничено расстоянием`() {
        // Отрицание далеко от действия не должно отменять это действие.
        assertFalse(
            NegationDetector.isNegatedBefore(
                "сегодня не получилось доделать, зато вчера отправил отчёт",
                "отправил"
            )
        )
    }

    @Test
    fun `отрицание перед конкретным маркером`() {
        assertTrue(NegationDetector.isNegatedBefore("не отправил", "отправил"))
        assertTrue(NegationDetector.isNegatedBefore("не сделал", "сделал"))
        assertFalse(NegationDetector.isNegatedBefore("сделал", "сделал"))
        assertFalse(NegationDetector.isNegatedBefore("отправил", "сделал"))
    }

    @Test
    fun `нейтральный контекст не считается отрицанием`() {
        assertFalse(NegationDetector.isNegated("не помню, но кажется сделал"))
        assertFalse(NegationDetector.isNegated("не уверен, что поел"))
    }

    @Test
    fun `пустая строка безопасна`() {
        assertFalse(NegationDetector.isNegated(""))
        assertFalse(NegationDetector.isNegatedBefore("", "сделал"))
    }

    @Test
    fun `фраза про отрицание в названии задачи не ломает разбор`() {
        // «не» в середине предложения без глагола после — не отрицание.
        assertFalse(NegationDetector.isNegated("пробежал десять километров"))
    }
}
