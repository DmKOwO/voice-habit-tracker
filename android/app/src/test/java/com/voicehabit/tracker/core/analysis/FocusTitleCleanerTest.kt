package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTitleCleanerTest {

    @Test fun `сырой поток делится на заголовок и детали`() {
        val r = FocusTitleCleaner.split("изучение английского от A2 до B1, примерно на 60% дохожу до B1")
        assertEquals("Изучение английского от A2 до B1", r.title)
        assertTrue(r.details.contains("60%"))
    }

    @Test fun `модальное начало срезается`() {
        val r = FocusTitleCleaner.split("надо разобрать отчёт по практике")
        assertEquals("Разобрать отчёт по практике", r.title)
        assertEquals("", r.details)
    }

    @Test fun `длинный хвост уходит в детали`() {
        val r = FocusTitleCleaner.split("подготовить очень длинное и подробное выступление на конференции по итогам квартала")
        assertTrue(r.title.length <= 48)
        assertTrue(r.details.isNotBlank())
    }

    @Test fun `пусто не ломает`() {
        val r = FocusTitleCleaner.split("   ")
        assertEquals("Фокус-сессия", r.title)
    }
}
