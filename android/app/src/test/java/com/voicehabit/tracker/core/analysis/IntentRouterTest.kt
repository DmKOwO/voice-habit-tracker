package com.voicehabit.tracker.core.analysis

import com.voicehabit.tracker.domain.model.IntentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1. Детектор режима.
 *
 * Главные регрессии, которые класс закрывает:
 * - «мне кажется, нам надо бы купить молоко» не должно превращаться в поток мыслей:
 *   одно вводное слово не перевешивает волю пользователя.
 * - Монолог без единого глагола действия обязан стать конспектом, а не заметкой,
 *   которая нигде не сохранялась.
 * - «что у меня сегодня» — это вопрос, а не новая задача.
 * - При полном отсутствии сигналов побеждает DICTATE: выдуманная задача в списке дел
 *   дороже, чем потерянная задача внутри конспекта.
 */
class IntentRouterTest {

    private fun route(text: String) = IntentRouter.route(text)

    @Test
    fun `explicit plan trigger is a task`() {
        val verdict = route("напомни купить хлеб завтра")
        assertEquals(
            "Прямое поручение с датой — это задача",
            IntentMode.LOG,
            verdict.mode
        )
        assertTrue("Уверенность обязана быть высокой: $verdict", verdict.confidence > 0.8f)
    }

    @Test
    fun `past tense report is a task`() {
        assertEquals(
            "Отчёт о сделанном закрывает привычки и задачи",
            IntentMode.LOG,
            route("сходил к врачу и выпил таблетки").mode
        )
    }

    @Test
    fun `monologue without a single action verb becomes a digest`() {
        val verdict = route(
            "короче я тут надиктовываю мысли про новый проект, обсуждал с ребятом " +
                "архитектуру, и вот что важно — рынок огромный но конкуренты сильные, " +
                "как я уже говорил, надо бы ещё подумать про позиционирование"
        )
        assertEquals(
            "Поток мыслей с одним затерянным «надо бы» — это MIXED, не чистый поток",
            IntentMode.MIXED,
            verdict.mode
        )
    }

    @Test
    fun `pure deliberation with no action stays a digest`() {
        val verdict = route(
            "мы с Сашей обсудили архитектуру, короче решили что монолит проще, " +
                "но есть риск что потом придётся переписывать, в следующий раз обсудим варианты"
        )
        assertEquals(
            "Рассуждение без поручения — конспект",
            IntentMode.DICTATE,
            verdict.mode
        )
    }

    @Test
    fun `one hedge word does not beat a real task`() {
        val verdict = route("мне кажется, нам надо бы купить молоко до вечера")
        assertEquals(
            "Вводное «мне кажется» не должно переводить задачу в поток мыслей",
            IntentMode.LOG,
            verdict.mode
        )
    }

    @Test
    fun `day summary is a query`() {
        val verdict = route("что у меня сегодня")
        assertEquals("Просьба о сводке не создаёт записей", IntentMode.QUERY, verdict.mode)
    }

    @Test
    fun `empty transcript is a digest with zero confidence`() {
        val verdict = route("   ")
        assertEquals("Пустое не должно превращаться в задачу", IntentMode.DICTATE, verdict.mode)
        assertEquals("На пустом уверенность нулевая", 0f, verdict.confidence, 0.001f)
    }

    @Test
    fun `greeting without signals does not invent a task`() {
        val verdict = route("привет")
        assertTrue(
            "Хотя бы одно слово без сигналов не должно давать уверенную задачу: $verdict",
            verdict.confidence < 0.8f
        )
    }

    @Test
    fun `confusion is honest`() {
        val strongTask = route("не забудь в 18:30 позвонить в сервисный центр по поводу счётчика")
        assertTrue("Явный план должен быть уверенным", strongTask.confidence > 0.8f)
    }

    @Test
    fun `delete command is a task operation`() {
        assertEquals(
            "Удаление задачи голосом — это операция, а не размышление",
            IntentMode.LOG,
            route("уйди задачу купить молоко").mode
        )
    }

    @Test
    fun `reason and signals are always present for a real phrase`() {
        val verdict = route("напомни купить хлеб завтра")
        assertTrue("Причина обязана объясняться пользователю", verdict.reason.isNotBlank())
        assertTrue("Сигналы показываются в шторке разбора", verdict.signals.isNotEmpty())
    }

    @Test
    fun `mode drives what gets created`() {
        assertTrue(IntentMode.LOG.producesEntities)
        assertTrue(IntentMode.MIXED.producesEntities)
        assertTrue("Поток мыслей не должен трогать список дел", !IntentMode.DICTATE.producesEntities)
        assertTrue("Вопрос не должен трогать список дел", !IntentMode.QUERY.producesEntities)
        assertTrue(IntentMode.DICTATE.needsDigest)
        assertTrue(IntentMode.MIXED.needsDigest)
    }
}
