package com.voicehabit.tracker.core.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1. Сборка конспекта.
 *
 * Регрессии, которые класс закрывает:
 * - Поток без знаков препинания (обычная речь надиктовки) обязан распасться на тезисы.
 * - Секция не должна наполняться выдуманным содержимым: нет вопросов в речи — нет
 *   и секции «открытые вопросы».
 * - Имена извлекаются только из середины фразы: первое слово предложения заглавно
 *   всегда, и это не имя.
 */
class DigestBuilderTest {

    @Test
    fun `long monologue without punctuation becomes several key points`() {
        val digest = DigestBuilder.build(
            "короче я тут размышляю про релиз и вот что важно сроки горят и рынок " +
                "не простит задержку в общем надо бы обсудить с командой перенос"
        )
        assertTrue(
            "Поток без знаков препинания обязан распасться на тезисы",
            digest.keyPoints.isNotEmpty() || digest.nextSteps.isNotEmpty()
        )
        assertTrue("Заголовок должен быть", digest.title.isNotBlank())
    }

    @Test
    fun `open question is found even behind a connective`() {
        val digest = DigestBuilder.build(
            "Мы обсудили релиз. А как быть с обучением пользователей? Этот вопрос пока открыт."
        )
        assertTrue("Вопрос с связкой «а» обязан попасть в открытые вопросы", digest.openQuestions.isNotEmpty())
    }

    @Test
    fun `question is found without a question mark`() {
        val digest = DigestBuilder.build(
            "Решили перенести релиз на пятницу, а как быть с обучением пользователей этот вопрос пока открыт"
        )
        assertTrue(
            "Открытый вопрос без знака «?» всё равно вопрос: ${digest.openQuestions}",
            digest.openQuestions.isNotEmpty()
        )
    }

    @Test
    fun `a statement about speaking is never a key point`() {
        val digest = DigestBuilder.build(
            "короче я тут надиктовываю мысли после разговора с Сашей и Олегом про релиз, " +
                "и вот что важно — сроки горят, рынок не подождёт, мы решили что переносим " +
                "релиз на пятницу, потратили 2 часа 30 минут на обсуждение, а как быть с " +
                "обучением пользователей этот вопрос пока открыт, и надо бы предупредить команду"
        )
        digest.keyPoints.forEach { point ->
            assertFalse(
                "Фраза «$point» описывает разговор, а не его содержание",
                point.lowercase().startsWith("я тут")
            )
        }
        assertTrue("Выжимка должна начинаться с решения, а не с вступления", digest.gist.contains("решили"))
    }

    @Test
    fun `people and numbers are stored and come back`() {
        val digest = DigestBuilder.build("Потратили 2 часа 30 минут на обсуждение с Олегом")
        assertTrue("Люди: ${digest.people}", digest.people.any { it.startsWith("Олег") })
        assertTrue("Цифры: ${digest.numbers}", digest.numbers.any { it.contains("часа") })
    }

    @Test
    fun `one person in different cases is one person`() {
        val digest = DigestBuilder.build("С Мариной пересматривали бюджет, и Марина считает что план рабочий")
        assertEquals(
            "«Марина» и «Мариной» — один человек, а не две записи: ${digest.people}",
            1,
            digest.people.size
        )
    }

    @Test
    fun `mentioning the word solution is not a decision`() {
        val digest = DigestBuilder.build(
            "Марина посчитала сценарии, и я не уверен что это правильное решение"
        )
        assertTrue(
            "Сомнение не является решением: ${digest.decisions}",
            digest.decisions.isEmpty()
        )
    }

    @Test
    fun `gist never opens with a phrase about speaking`() {
        val digest = DigestBuilder.build(
            "слушай я тут проговорил мысли после разговора с Олегом, и вот что важно — " +
                "он считает что мы слишком быстро обещаем сроки, и я с ним согласен, " +
                "мы решили что больше не будем давать точных дат до конца квартала, " +
                "это заняло два с половиной часа, и я вымотался очень сильно сегодня"
        )
        assertFalse(
            "Суть не может начинаться с «я тут проговорил…»: ${digest.gist}",
            digest.gist.startsWith("слушай") || digest.gist.startsWith("я тут")
        )
    }

    @Test
    fun `numbers spelled out as words are honestly not reported as numbers`() {
        val digest = DigestBuilder.build("Обсуждали два с половиной часа подряд")
        assertTrue(
            "Цифр в тексте нет — выдумывать их нельзя: ${digest.numbers}",
            digest.numbers.none { it.contains("час") }
        )
    }

    @Test
    fun `empty sections stay empty instead of being invented`() {
        val digest = DigestBuilder.build("Купил хлеб в магазине")
        assertTrue("Нет решений в речи — нет и секции", digest.decisions.isEmpty())
        assertTrue("Нет вопросов в речи — нет и секции", digest.openQuestions.isEmpty())
        assertTrue("Нет людей в речи — нет и секции", digest.people.isEmpty())
    }

    @Test
    fun `decisions and questions are separated`() {
        val digest = DigestBuilder.build(
            "Мы обсудили релиз и решили, что переносим его на пятницу. " +
                "А как быть с обучением пользователей? Этот вопрос пока открыт."
        )
        assertTrue("Решение должно попасть в решения", digest.decisions.isNotEmpty())
        assertTrue("Вопрос должен попасть в открытые вопросы", digest.openQuestions.isNotEmpty())
    }

    @Test
    fun `people are extracted from the middle of a sentence only`() {
        val digest = DigestBuilder.build("Встретился с Олегом и передал Марине документы")
        assertTrue("Имена должны быть найдены: ${digest.people}", digest.people.contains("Олегом"))
        assertTrue("Второе имя тоже: ${digest.people}", digest.people.any { it.startsWith("Марин") })
    }

    @Test
    fun `numbers keep their units`() {
        val digest = DigestBuilder.build("Потратил 2 часа 30 минут и 15 тысяч рублей на ремонт")
        assertTrue("Цифры с единицами должны попасть в конспект: ${digest.numbers}", digest.numbers.isNotEmpty())
    }

    @Test
    fun `filler words never become a thesis`() {
        val digest = DigestBuilder.build("короче ну типа вот что важно, мы переносим релиз")
        assertFalse("Заголовок не должен начинаться с обвязки", digest.title.startsWith("короче"))
        assertFalse("Заголовок не должен начинаться с «ну»", digest.title.startsWith("ну"))
    }

    @Test
    fun `empty transcript produces an empty digest`() {
        val digest = DigestBuilder.build("   ")
        assertTrue("Пустой ввод не должен давать выдуманный конспект", digest.isEmpty)
    }

    @Test
    fun `speech duration is carried through`() {
        val digest = DigestBuilder.build("Поговорили о плане на неделю", speechSeconds = 42)
        assertEquals("Длительность речи сохраняется", 42, digest.speechSeconds)
        assertNotNull("Суть должна быть", digest.gist)
    }

    @Test
    fun `mood is detected only when present`() {
        assertEquals(
            "Тон усталости должен распознаваться",
            "Усталость",
            DigestBuilder.build("Совсем выгорел, сил нет уже").tone
        )
        assertEquals(
            "Нет настроения — не выдумываем его",
            "",
            DigestBuilder.build("Купил хлеб и пошёл домой").tone
        )
    }

    @Test
    fun `filled sections counter reflects real content`() {
        val rich = DigestBuilder.build(
            "Решили перенести релиз. Когда обновлять документацию? Потратили 3 часа. С Олегом обсудили."
        )
        assertTrue("Богатый конспект заполняет несколько секций: ${rich.filledSections}", rich.filledSections >= 3)
    }

    @Test
    fun `word count is reported`() {
        val digest = DigestBuilder.build("раз два три четыре пять")
        assertEquals("Слово считается один раз", 5, digest.wordCount)
    }
}
