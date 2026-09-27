package com.voicehabit.tracker.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Русские числительные в заголовках экранов.
 *
 * Регрессия: «3 выжимок разговоров» в шапке экрана конспектов.
 */
class RussianPluralTest {

    @Test
    fun `one is used for 1 and 21`() {
        assertEquals("1 выжимка", RussianPlural.count(1, "выжимка", "выжимки", "выжимок"))
        assertEquals("21 выжимка", RussianPlural.count(21, "выжимка", "выжимки", "выжимок"))
    }

    @Test
    fun `few is used for 2 to 4 and 22 to 24`() {
        assertEquals("2 выжимки", RussianPlural.count(2, "выжимка", "выжимки", "выжимок"))
        assertEquals("3 выжимки", RussianPlural.count(3, "выжимка", "выжимки", "выжимок"))
        assertEquals("4 выжимки", RussianPlural.count(4, "выжимка", "выжимки", "выжимок"))
        assertEquals("23 выжимки", RussianPlural.count(23, "выжимка", "выжимки", "выжимок"))
    }

    @Test
    fun `many is used for zero, 5 to 20 and teens`() {
        assertEquals("0 выжимок", RussianPlural.count(0, "выжимка", "выжимки", "выжимок"))
        assertEquals("5 выжимок", RussianPlural.count(5, "выжимка", "выжимки", "выжимок"))
        assertEquals("11 выжимок", RussianPlural.count(11, "выжимка", "выжимки", "выжимок"))
        assertEquals("14 выжимок", RussianPlural.count(14, "выжимка", "выжимки", "выжимок"))
        assertEquals("19 выжимок", RussianPlural.count(19, "выжимка", "выжимки", "выжимок"))
        assertEquals("111 выжимок", RussianPlural.count(111, "выжимка", "выжимки", "выжимок"))
    }

    @Test
    fun `negative counts use the same rules`() {
        assertEquals("-1 выжимка", RussianPlural.count(-1, "выжимка", "выжимки", "выжимок"))
        assertEquals("-3 выжимки", RussianPlural.count(-3, "выжимка", "выжимки", "выжимок"))
    }

    @Test
    fun `hour forms distinguish 2 and 5`() {
        assertEquals("2 часа", RussianPlural.count(2, "час", "часа", "часов"))
        assertEquals("5 часов", RussianPlural.count(5, "час", "часа", "часов"))
        assertEquals("21 час", RussianPlural.count(21, "час", "часа", "часов"))
    }
}
