package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianNumberParserTest {
    @Test fun `цифры и слова`() {
        assertEquals(2.0, RussianNumberParser.findFirst("выпил два литра воды")!!, 0.0)
        assertEquals(2.5, RussianNumberParser.findFirst("два с половиной часа")!!, 0.0)
        assertEquals(1.5, RussianNumberParser.findFirst("полтора литра")!!, 0.0)
        assertEquals(10.0, RussianNumberParser.findFirst("прочитал десять страниц")!!, 0.0)
        assertEquals(2.0, RussianNumberParser.findFirst("2 литра")!!, 0.0)
    }
    @Test fun `нет числа`() { assertNull(RussianNumberParser.findFirst("просто погулял вечером")) }
}

class TokenOverlapSearchTest {
    @Test fun `релевантность по смыслу`() {
        val docs = listOf("созвон с командой про релиз", "тренировка в зале утром", "купить молоко")
        val ranked = TokenOverlapSearch.rank("релиз команды", docs, { it })
        assertTrue(ranked.isNotEmpty())
        assertEquals("созвон с командой про релиз", ranked.first().first)
    }
}

class UserInsightEngineTest {
    @Test fun `профессия по задачам`() {
        val r = UserInsightEngine.infer(listOf("пофиксить баг на сервере", "залить релиз"), emptyList())
        assertEquals("разработчик", r.profession)
        assertTrue(r.topics.isNotEmpty())
    }
}

class HabitTagCodecTest {
    @Test fun `avoid и perweek`() {
        val t1 = HabitTagCodec.withAvoid("спорт", true)
        assertTrue(HabitTagCodec.isAvoid(com.voicehabit.tracker.data.local.entity.HabitEntity(id = "1", title = "x", tags = t1)))
        val t2 = HabitTagCodec.withWeeklyTarget("", 3)
        assertEquals(3, HabitTagCodec.weeklyTarget(com.voicehabit.tracker.data.local.entity.HabitEntity(id = "1", title = "x", tags = t2)))
    }
}
