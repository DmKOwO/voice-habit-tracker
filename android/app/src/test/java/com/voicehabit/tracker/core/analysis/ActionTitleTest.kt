package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1. Общий чистильщик названия задачи.
 *
 * Регрессии, которые класс закрывает:
 * - Ручное создание задачи из конспекта давало «Может имеет смысл сначала потестировать…»
 *   там, где автоматическое давало «Потестировать на десяти клиентах». Два названия
 *   одной задачи в одном списке дел.
 * - [ActionTitle.stripMeta] не должен срезать связки: «сначала купить молоко» в задаче
 *   означает «сначала молоко, потом остальное», и потеря этого слова меняет смысл.
 * - Обрезка до 60 символов не должна оставлять висящий пробел.
 */
class ActionTitleTest {

    @Test
    fun `hedge words are removed`() {
        assertEquals("Потестировать на десяти клиентах", ActionTitle.from("может имеет смысл сначала потестировать на десяти клиентах"))
        assertEquals("Предупредить команду", ActionTitle.from("и надо бы предупредить команду"))
    }

    @Test
    fun `dates and times are stripped`() {
        assertEquals("Позвонить врачу", ActionTitle.from("завтра в 18:30 позвонить врачу"))
        assertEquals("Купить подарок", ActionTitle.from("через 3 дня купить подарок"))
    }

    @Test
    fun `urgency words are stripped`() {
        assertEquals("Сдать отчёт", ActionTitle.from("очень срочно сдать отчёт"))
    }

    @Test
    fun `truncation leaves no trailing space`() {
        val long = "и по срокам надо бы уточнить у бухгалтерии когда будет закрыт квартал"
        val title = ActionTitle.from(long)
        assertEquals("Заголовок не должен заканчиваться пробелом", title, title.trim())
        assertTrue("Длина должна быть ограничена: ${title.length}", title.length <= ActionTitle.MAX_LENGTH)
    }

    @Test
    fun `already lowercase input is accepted`() {
        assertEquals(
            "То же самое при входе в нижнем регистре",
            ActionTitle.from("И НАДО БЫ ПРЕДУПРЕДИТЬ КОМАНДУ".lowercase()),
            ActionTitle.from("И НАДО БЫ ПРЕДУПРЕДИТЬ КОМАНДУ")
        )
    }

    @Test
    fun `stripMeta keeps the word first but removes the date`() {
        assertEquals("сначала купить молоко", ActionTitle.stripMeta("завтра сначала купить молоко"))
    }

    @Test
    fun `empty and punctuation only input becomes empty`() {
        assertEquals("", ActionTitle.from("надо"))
        assertEquals("", ActionTitle.from("!!!"))
        assertEquals("", ActionTitle.from("   "))
    }
}
