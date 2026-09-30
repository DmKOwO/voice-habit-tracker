package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1. Разбор тренировочной программы.
 *
 * ## Регрессии, которые класс закрывает
 *
 * 1. **`\\w` в Java — это `[a-zA-Z_0-9]`.** Из-за этого «4 подхода × 8-12 сек»
 *    не находилось: `подход\\w*` съедал «подход», висела «а», и **все статические
 *    упражнения теряли рецепт**, пока силовые («4 × 6-8 повт») разбирались.
 * 2. **Инлайн-`(?i)` покрывает только ASCII.** «Главные цели» не находилось вообще.
 * 3. **Фильтр по длине первой ячейки** отбрасывал заголовки разделов вида
 *    «Понедельник - День 1: Тяга + Передний вис + Квадрицепсы (Pull & Quads)», и все
 *    упражнения программы сваливались в последний день недели.
 * 4. **Прогрессия искалась по названиям упражнений**, а не по документу, поэтому
 *    «L-sit: 8 сек → 20 сек» ни к чему не привязывалась.
 * 5. **Позиция упражнения не читалась из номера строки** — у всех был `position = 0`.
 */
class ProgrammeParserTest {

    /**
     * Кусок реального документа: текст из `.docx`, где таблица приходит строками
     * с `|`, пункты склеены, а прогрессия вынесена в отдельный раздел.
     */
    private val source = """
        ПРОГРАММА ТРЕНИРОВОК ПО КАЛИСТЕНИКЕ

        Параметры атлета | Рост: 173 см | Вес: 55 кг | ИМТ: 18.4
        Главные цели | 1. Набор массы (+5-7 кг)2. Чистый выход силой3. Контроль тела

        Понедельник | День 1: Тяга + Передний вис | Старт недели на свежие силы
        Вторник | ДЕНЬ ОТДЫХА | Восстановление мышц спины
        Среда | День 2: Жим + Стойка на руках | Работают грудь и плечи
        Четверг | ДЕНЬ ОТДЫХА | Восстановление плечевого пояса
        Пятница | День 3: Выход силой + Фуллбоди | Пиковая мощность
        Суббота | ДЕНЬ ОТДЫХА | Суперкомпенсация и сон
        Воскресенье | ДЕНЬ ОТДЫХА | Полная перезагрузка

        Понедельник - День 1: Тяга, Передний вис и Квадрицепсы
        1 | Tuck Front Lever (Передний вис в группировке) | На высоком турнике | Под столом | 4 подхода × 8-12 сек | СтатикаОтдых 2 мин
        2 | Силовые подтягивания с весом | Турник, рюкзак +5-10 кг | Домашний турник | 4 × 6-8 повт | Темп 2-0-3Отдых 2.5 мин
        3 | Пистолетики | Без опоры | С касанием стены | 4 × 6-8 повт | Темп 3-1-1Отдых 90 сек

        Среда - День 2: Жим, Плечи и Баланс
        1 | Стойка на руках (Handstand) | У стены | Животом к стене | 5 подходов × 20-30 сек | КачествоОтдых 90 сек
        2 | Отжимания на брусьях | На брусьях | Между стульями | 4 × 8-10 повт | Темп 2-1-3Отдых 2 мин

        Пятница - День 3: Взрывная сила и статики
        1 | Чистый выход силой | Без маха ногами | Взрывные подтягивания | 5 подходов × 2-3 повт | Взрыв 100%Отдых 3 мин
        2 | L-sit (Уголок) | На брусьях | На полу | 4 × макс (цель: 15-20с) | СтатикаОтдых 90 сек

        5. Прогрессия статики
        L-sit (Уголок на полу / брусьях) [Текущие 8 сек -> Цель: 20 сек]
        Стойка на руках [Текущие 30 сек -> Цель: 60 сек]
    """.trimIndent()

    private val draft = ProgrammeParser.parse(source)

    @Test
    fun `находит заголовок, атлета и цели`() {
        assertTrue("Заголовок: ${draft.title}", draft.title.contains("КАЛИСТЕНИК"))
        assertTrue("Рост: ${draft.athleteNote}", draft.athleteNote.contains("173"))
        assertTrue("Вес: ${draft.athleteNote}", draft.athleteNote.contains("55"))
        assertTrue("Цели: ${draft.goals}", draft.goals.contains("Набор массы"))
        assertTrue(
            "Номера пунктов целей должны быть сняты: ${draft.goals}",
            !draft.goals.contains("1. ") && !draft.goals.contains("2. ")
        )
    }

    @Test
    fun `расписание недели разбирается полностью`() {
        assertEquals("Все семь дней недели", 7, draft.days.size)
        assertEquals(
            "Тренировочные дни Пн/Ср/Пт",
            setOf(1, 3, 5),
            draft.trainingDays.map { it.weekday }.toSet()
        )
        assertEquals("Дни отдыха — четыре", 4, draft.days.count { it.isRest })
        assertTrue(
            "Дни отдыха должны называться как в источнике",
            draft.days.first { it.weekday == 2 }.title.contains("ОТДЫХА", ignoreCase = true)
        )
    }

    @Test
    fun `заголовок раздела не съедает упражнения соседнего дня`() {
        // Регрессия №3: все 18 упражнений сваливались в «Вс».
        assertEquals("Понедельник", 3, draft.days.first { it.weekday == 1 }.exercises.size)
        assertEquals("Среда", 2, draft.days.first { it.weekday == 3 }.exercises.size)
        assertEquals("Пятница", 2, draft.days.first { it.weekday == 5 }.exercises.size)
        assertEquals("День отдыха без упражнений", 0, draft.days.first { it.weekday == 7 }.exercises.size)
    }

    @Test
    fun `статический рецепт с подходами разбирается`() {
        // Регрессия №1: `подход\w*` обрезался на «подход», и рецепт терялся.
        val frontLever = draft.days.first { it.weekday == 1 }.exercises.first()
        assertEquals("Tuck Front Lever (Передний вис в группировке)", frontLever.title)
        assertEquals(4, frontLever.sets)
        assertEquals(8, frontLever.repsMin)
        assertEquals(12, frontLever.repsMax)
        assertEquals("сек", frontLever.measure)
        assertEquals("4 × 8–12 сек", frontLever.prescription)
    }

    @Test
    fun `темп и отдых разбираются даже склеенными без пробела`() {
        val weighted = draft.days.first { it.weekday == 1 }.exercises[1]
        assertEquals("Темп без слова «Темп»", "2-0-3", weighted.tempo)
        assertEquals("2.5 минуты = 150 секунд", 150, weighted.restSec)
        assertEquals("Отдых 2 мин 30 с", "Отдых 2 мин 30 с", weighted.restLabel)

        val static = draft.days.first { it.weekday == 1 }.exercises.first()
        assertEquals("Темп-слово сохраняется", "Статика", static.tempo)
        assertEquals(120, static.restSec)
    }

    @Test
    fun `варианты выполнения не смешиваются`() {
        val pull = draft.days.first { it.weekday == 1 }.exercises[1]
        assertTrue("Вариант на площадке: ${pull.outdoor}", pull.outdoor.contains("Турник"))
        assertTrue("Домашний вариант: ${pull.home}", pull.home.contains("Домашний"))
    }

    @Test
    fun `позиция упражнения читается из номера строки`() {
        // Регрессия №5: у всех упражнений был position = 0.
        val monday = draft.days.first { it.weekday == 1 }.exercises
        assertEquals(listOf(0, 1, 2), monday.map { it.position })
    }

    @Test
    fun `прогрессия навешивается на упражнение`() {
        // Регрессия №4: прогрессия искалась по названиям упражнений, а не по документу.
        val lSit = draft.days.first { it.weekday == 5 }.exercises.first { it.title.startsWith("L-sit") }
        assertEquals("Текущий уровень", 8.0, lSit.currentValue, 0.001)
        assertEquals("Цель", 20.0, lSit.targetValue, 0.001)
        assertTrue("Подпись прогрессии: ${lSit.progressionLabel}", lSit.progressionLabel.contains("8"))
        assertTrue(lSit.hasProgression)
    }

    @Test
    fun `статика без повторов не показывает пустой рецепт`() {
        val lSit = draft.days.first { it.weekday == 5 }.exercises.first { it.title.startsWith("L-sit") }
        assertEquals(4, lSit.sets)
        assertFalse("«4 × » — это мусор, а не рецепт", lSit.prescription.contains("×"))
        assertEquals("4 подхода", lSit.prescription)
    }

    @Test
    fun `пустой текст не выдумывает программу`() {
        val empty = ProgrammeParser.parse("   ")
        assertTrue("Пустой текст обязан дать пустой результат", empty.isEmpty)
        assertTrue("И объяснение: ${empty.warnings}", empty.warnings.isNotEmpty())
    }

    @Test
    fun `текст без дней недели честно ругается`() {
        val noSchedule = ProgrammeParser.parse("Завтра купить хлеб и сходить в зал")
        assertTrue("Без расписания программы не существует", noSchedule.isEmpty)
        assertTrue(
            "Пользователь должен понять, что не так: ${noSchedule.warnings}",
            noSchedule.warnings.any { it.contains("дня тренировки") }
        )
    }

    @Test
    fun `расписание без упражнений помечается предупреждением, а не молча`() {
        val onlySchedule = ProgrammeParser.parse(
            """
            Понедельник | День 1: Тяга
            Вторник | ДЕНЬ ОТДЫХА
            Среда | День 2: Жим
            """.trimIndent()
        )
        assertEquals(2, onlySchedule.trainingDays.size)
        assertTrue(
            "Программа без упражнений должна об этом сказать: ${onlySchedule.warnings}",
            onlySchedule.warnings.any { it.contains("упражнен") }
        )
    }

    /**
     * Регрессия №6: «Отдых 90 сек» в колонке темпа не должен делать тренировочный
     * день днём отдыха. Отдых смотрится только в ячейках, описывающих день.
     */
    @Test
    fun `отдых в колонке темпа не делает день выходным`() {
        val monday = draft.days.first { it.weekday == 1 }
        assertFalse(
            "Понедельник с «Отдых 90 сек» в темпе — это тренировка, а не отдых",
            monday.isRest
        )
        assertTrue(draft.trainingDays.any { it.weekday == 1 })
    }

    @Test
    fun `короткие названия дней недели распознаются`() {
        val short = ProgrammeParser.parse(
            """
            Пн | Тяга | 1 | Подтягивания | Турник | Дом | 4 × 8-10 повт | Отдых 90 сек
            Ср | Жим | 1 | Отжимания | Брусья | Дом | 4 × 8-10 повт | Отдых 90 сек
            Пт | Силовая | 1 | Выход силой | Турник | Дом | 5 × 2-3 повт | Отдых 3 мин
            """.trimIndent()
        )
        assertEquals(setOf(1, 3, 5), short.trainingDays.map { it.weekday }.toSet())
        assertEquals(1, short.trainingDays.first().exercises.size)
    }
}
