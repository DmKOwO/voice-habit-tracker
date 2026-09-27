package com.voicehabit.tracker.data.remote

import com.voicehabit.tracker.data.local.entity.HabitEntity
import com.voicehabit.tracker.data.local.entity.TaskEntity
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OfflineVoiceParserTest {

    private val habits = listOf(
        HabitEntity(id = "habit_make_bed", title = "Make Bed", category = "Morning", displayType = "STREAKS"),
        HabitEntity(id = "habit_workout", title = "Workout", category = "Fitness", displayType = "GRID"),
        HabitEntity(id = "habit_posting", title = "Posting", category = "Work", displayType = "BAR_GRAPH"),
        HabitEntity(id = "habit_vitamin", title = "Vitamin", category = "Health", displayType = "DAILY_CHECK")
    )

    private val openTasks = listOf(
        TaskEntity(
            id = "task_1",
            title = "Сдать отчет по проекту",
            dueDateIso = null,
            priority = "HIGH",
            category = "Work",
            isCompleted = false,
            completedAt = null,
            createdAt = 0L
        )
    )

    @Test
    fun `future plan is a quick task, not a completed habit`() {
        val action = OfflineVoiceParser.parse("завтра надо поучить английский", habits, openTasks)

        assertTrue("Планы не должны закрывать привычки", action.habitsCompleted.isEmpty())
        assertEquals(1, action.tasksToAdd.size)

        val task = action.tasksToAdd.first()
        assertEquals("Выучить английский", task.title)
        assertEquals(TaskType.QUICK.name, task.taskType)
        assertNotNull("Дата «завтра» должна распознаваться", task.dueDate)
        assertEquals(LocalDate.now().plusDays(1).toString(), task.dueDate!!.take(10))
    }

    @Test
    fun `need to buy vitamins does not close vitamin habit`() {
        val action = OfflineVoiceParser.parse("надо купить витамины", habits, openTasks)

        assertTrue("План «купить витамины» не должен закрывать привычку Vitamin", action.habitsCompleted.isEmpty())
        assertEquals(1, action.tasksToAdd.size)
        assertEquals("Купить витамины", action.tasksToAdd.first().title)
    }

    @Test
    fun `past tense closes the mentioned habit`() {
        val action = OfflineVoiceParser.parse("выпил витамины утром", habits, openTasks)

        assertEquals(1, action.habitsCompleted.size)
        assertEquals("habit_vitamin", action.habitsCompleted.first().habitId)
        assertTrue("Заметка не нужна, когда привычка распознана", action.quickNotes.isEmpty())
    }

    @Test
    fun `explicit completion marker closes the open task`() {
        val action = OfflineVoiceParser.parse("сдал отчет по проекту", habits, openTasks)

        assertEquals(1, action.tasksToComplete.size)
        assertEquals("task_1", action.tasksToComplete.first().taskId)
    }

    @Test
    fun `recurring wording produces a long goal`() {
        val action = OfflineVoiceParser.parse("надо каждый день пить воду", habits, openTasks)

        assertEquals(1, action.tasksToAdd.size)
        assertEquals(TaskType.LONG.name, action.tasksToAdd.first().taskType)
        assertEquals("Пить воду", action.tasksToAdd.first().title)
    }

    @Test
    fun `explicit time is parsed and stripped from the title`() {
        val action = OfflineVoiceParser.parse("завтра в 18:30 позвонить врачу", habits, openTasks)

        assertEquals(1, action.tasksToAdd.size)
        val task = action.tasksToAdd.first()
        assertEquals("Позвонить врачу", task.title)
        assertTrue(task.dueDate!!.contains("T18:30"))
    }

    @Test
    fun `done everything closes all active habits`() {
        val action = OfflineVoiceParser.parse("сделал всё", habits, openTasks)

        assertEquals(habits.size, action.habitsCompleted.size)
    }

    @Test
    fun `unrelated phrase becomes a digest instead of a lost note`() {
        val action = OfflineVoiceParser.parse("думаю про отпуск летом", habits, openTasks)

        assertTrue("Рассуждение не должно закрывать привычки", action.habitsCompleted.isEmpty())
        assertTrue("Рассуждение не должно закрывать задачи", action.tasksToComplete.isEmpty())
        assertTrue("Рассуждение не должно создавать задачи", action.tasksToAdd.isEmpty())
        assertEquals("Рассуждение — это режим выжимки", IntentMode.DICTATE, action.mode)
        assertNotNull("Рассуждение обязано попасть в конспект", action.digest)
        assertTrue(
            "Раньше фраза попадала в quickNotes, которые никто никогда не сохранял, " +
                "и пользователь терял слова целиком",
            action.quickNotes.isEmpty()
        )
    }

    @Test
    fun `digest carries a title and survives being empty of sections`() {
        val action = OfflineVoiceParser.parse(
            "короче тут просто болтаю ни о чём particular",
            habits,
            openTasks
        )
        assertEquals(IntentMode.DICTATE, action.mode)
        assertNotNull(action.digest)
        assertTrue("Заголовок конспекта обязателен", action.digest!!.title.isNotBlank())
    }

    @Test
    fun `task mode does not build a digest`() {
        val action = OfflineVoiceParser.parse("напомни купить хлеб завтра", habits, openTasks)

        assertEquals(IntentMode.LOG, action.mode)
        assertTrue("У задачи конспект не нужен", action.digest == null)
        assertTrue("Уверенность у явного поручения высокая", action.modeConfidence > 0.8f)
    }

    @Test
    fun `mixed mode keeps both the action and the digest`() {
        val action = OfflineVoiceParser.parse(
            "короче мы тут обсуждали релиз и решили что переносим, короче рынок не подождёт, " +
                "и вот что важно — сроки горят, надо бы предупредить команду",
            habits,
            openTasks
        )

        assertEquals(
            "Поток мыслей с действием внутри обязан дать оба результата",
            IntentMode.MIXED,
            action.mode
        )
        assertTrue("Действие из потока не теряется", action.tasksToAdd.isNotEmpty())
        assertNotNull("Рассуждение не теряется", action.digest)
    }

    @Test
    fun `mixed mode never builds a task title out of the opening of the monologue`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут надиктовываю мысли после разговора с Сашей и Олегом про релиз, " +
                "и вот что важно — сроки горят, рынок не подождёт, мы решили что переносим " +
                "релиз на пятницу, потратили 2 часа 30 минут на обсуждение, а как быть с " +
                "обучением пользователей этот вопрос пока открыт, и надо бы предупредить команду",
            habits,
            openTasks
        )

        assertEquals(IntentMode.MIXED, action.mode)
        action.tasksToAdd.forEach { task ->
            assertTrue(
                "Задача «${task.title}» собрана из начала монолога, а не из действия",
                !task.title.lowercase().contains("надиктовываю") &&
                    !task.title.lowercase().contains("разговора с") &&
                    !task.title.lowercase().startsWith("короче")
            )
        }
        assertTrue(
            "Хотя бы одно дело должно быть найдено: ${action.tasksToAdd.map { it.title }}",
            action.tasksToAdd.isNotEmpty()
        )
    }

    @Test
    fun `digest title is not the description of the act of speaking`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут надиктовываю мысли после разговора с Сашей про релиз, " +
                "и вот что важно — сроки горят, рынок не подождёт, мы решили перенести релиз " +
                "на пятницу, а как быть с обучением пользователей этот вопрос пока открыт",
            habits,
            openTasks
        )

        val title = action.digest!!.title
        assertFalse(
            "Заголовок «$title» описывает разговор, а не его тему",
            title.startsWith("Я тут") || title.contains("Надиктовываю")
        )
    }

    @Test
    fun `summary stays short so the voice log list stays readable`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут надиктовываю мысли после разговора с Сашей и Олегом про релиз, " +
                "и вот что важно — сроки горят, рынок не подождёт, мы решили что переносим " +
                "релиз на пятницу, потратили 2 часа 30 минут на обсуждение, а как быть с " +
                "обучением пользователей этот вопрос пока открыт, и надо бы предупредить команду",
            habits,
            openTasks
        )

        assertTrue("Сводка не должна быть пересказом: ${action.summary}", action.summary.length <= 140)
    }

    @Test
    fun `forced dictation mode creates no entities at all`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут надиктовываю мысли после разговора с Сашей и Олегом про релиз, " +
                "и вот что важно — сроки горят, рынок не подождёт, мы решили что переносим " +
                "релиз на пятницу, потратили 2 часа 30 минут на обсуждение, а как быть с " +
                "обучением пользователей этот вопрос пока открыт, и надо бы предупредить команду",
            habits,
            openTasks,
            forcedMode = IntentMode.DICTATE
        )

        assertEquals(IntentMode.DICTATE, action.mode)
        assertTrue(
            "Пользователь выбрал «выжимку» — ни одной задачи быть не должно",
            action.tasksToAdd.isEmpty()
        )
        assertTrue(action.habitsCompleted.isEmpty())
        assertNotNull("Конспект при этом остаётся", action.digest)
        assertTrue(
            "Сводка не должна обещать задачу: ${action.summary}",
            !action.summary.contains("Задача:")
        )
    }

    @Test
    fun `forced task mode produces no digest`() {
        val action = OfflineVoiceParser.parse(
            "короче тут просто болтаю ни о чём",
            habits,
            openTasks,
            forcedMode = IntentMode.LOG
        )
        assertEquals(IntentMode.LOG, action.mode)
        assertTrue("У режима «задача» конспекта быть не должно", action.digest == null)
    }

    @Test
    fun `forced mode is recorded as an override`() {
        val action = OfflineVoiceParser.parse(
            "что-то непонятное",
            habits,
            openTasks,
            forcedMode = IntentMode.DICTATE
        )
        assertTrue("Правка режима обязана быть видна в действии", action.modeIsOverridden)
        assertEquals(1f, action.modeConfidence, 0.001f)
    }

    @Test
    fun `mixed task titles drop hedges and have no trailing space`() {
        val action = OfflineVoiceParser.parse(
            "мы с Мариной пересматривали бюджет, и главное что мы поняли — подписка не окупается, " +
                "мы решили что уберём месячные тарифы и оставим только разовые, и вот тут я не уверен " +
                "что это правильное решение, может имеет смысл сначала потестировать на десяти клиентах, " +
                "и по срокам надо бы уточнить у бухгалтерии когда будет закрыт квартал",
            habits,
            openTasks
        )

        assertEquals(IntentMode.MIXED, action.mode)
        action.tasksToAdd.forEach { task ->
            assertEquals(
                "Заголовок «${task.title}» заканчивается пробелом после обрезки",
                task.title,
                task.title.trim()
            )
            assertFalse(
                "Заголовок «${task.title}» начинается со слов-мотиваторов",
                task.title.startsWith("Может") || task.title.startsWith("И ")
            )
        }
    }

    @Test
    fun `summary shows the time that will actually be saved`() {
        // 14:00 — время по умолчанию во всём приложении, и именно оно попадёт в задачу.
        // Шторка обязана показывать его ДО подтверждения: скрытое здесь время всплыло бы
        // в списке дел как «14:00 · 28 SEP», и расхождение заметил бы уже пользователь.
        val action = OfflineVoiceParser.parse("напомни купить хлеб завтра", habits, openTasks)

        assertTrue("Время по умолчанию показывается: ${action.summary}", action.summary.contains("14:00"))
        assertTrue("День остаётся на месте: ${action.summary}", action.summary.contains("завтра"))
    }

    @Test
    fun `explicit time is still shown`() {
        val action = OfflineVoiceParser.parse("завтра в 18:30 позвонить врачу", habits, openTasks)
        assertTrue("Явное время показывается: ${action.summary}", action.summary.contains("18:30"))
    }

    @Test
    fun `answerable question leaves no digest behind`() {
        val action = OfflineVoiceParser.parse("что у меня сегодня", habits, openTasks)

        assertEquals(IntentMode.QUERY, action.mode)
        assertTrue("Сводка дня затребована", action.daySummaryRequested)
        assertTrue(
            "Ответ на вопрос — сводка, а не конспект «Что у меня сегодня»",
            action.digest == null
        )
        assertTrue("Задач вопрос не создаёт", action.tasksToAdd.isEmpty())
    }

    @Test
    fun `unanswerable question is kept as a digest`() {
        val action = OfflineVoiceParser.parse(
            "почему тогда сломалось, я так и не понял что произошло в тот раз",
            habits,
            openTasks
        )
        assertNotNull(
            "На вопрос, на который приложение ответить не умеет, слова не должны пропасть",
            action.digest
        )
    }

    @Test
    fun `speech duration reaches the digest`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут размышляю про новый проект и про рынок и про конкурентов и про деньги",
            habits,
            openTasks,
            speechSeconds = 95
        )
        assertNotNull(action.digest)
        assertEquals(95, action.digest!!.speechSeconds)
    }

    @Test
    fun `summary shows the digest title in dictation mode`() {
        val action = OfflineVoiceParser.parse(
            "короче я тут размышляю про новый проект и про рынок и про конкурентов и про деньги",
            habits,
            openTasks
        )
        assertTrue(
            "Пользователь видит в шторке, что получился конспект, а не заметка",
            action.summary.startsWith("Конспект:")
        )
    }

    @Test
    fun `parser explains its decisions`() {
        val action = OfflineVoiceParser.parse("завтра надо поучить английский", habits, openTasks)

        assertTrue("Пользователь должен видеть ход рассуждения", action.insights.isNotEmpty())
        assertTrue(action.insights.any { it.contains("Намерение") })
    }

    @Test
    fun `deadline phrase does not stay in the title`() {
        val action = OfflineVoiceParser.parse("напомни через 3 дня купить подарок", habits, openTasks)

        val task = action.tasksToAdd.first()
        assertEquals("Купить подарок", task.title)
        assertEquals(LocalDate.now().plusDays(3).toString(), task.dueDate!!.take(10))
    }

    @Test
    fun `due date is a valid local date time`() {
        val action = OfflineVoiceParser.parse("послезавтра утром записаться к врачу", habits, openTasks)

        val parsed = LocalDateTime.parse(action.tasksToAdd.first().dueDate!!.take(19))
        assertEquals(LocalDate.now().plusDays(2), parsed.toLocalDate())
        assertEquals(9, parsed.hour)
    }
}

class VoiceCommandIntentsTest {

    private val habits = listOf(
        HabitEntity(id = "habit_workout", title = "Workout", category = "Fitness", displayType = "GRID")
    )

    private val openTasks = listOf(
        TaskEntity(
            id = "task_1",
            title = "Сдать отчет по проекту",
            dueDateIso = null,
            priority = "HIGH",
            category = "Work",
            isCompleted = false,
            completedAt = null,
            createdAt = 0L
        )
    )

    @Test
    fun `delete command matches task`() {
        val action = OfflineVoiceParser.parse("удали задачу сдать отчет", habits, openTasks)
        org.junit.Assert.assertEquals(1, action.tasksToDelete.size)
        org.junit.Assert.assertEquals("task_1", action.tasksToDelete.first().taskId)
        org.junit.Assert.assertTrue(action.tasksToAdd.isEmpty())
    }

    @Test
    fun `reschedule command extracts new date`() {
        val action = OfflineVoiceParser.parse("перенеси отчет на завтра", habits, openTasks)
        org.junit.Assert.assertEquals(1, action.tasksToReschedule.size)
        val rescheduled = action.tasksToReschedule.first()
        org.junit.Assert.assertEquals("task_1", rescheduled.taskId)
        org.junit.Assert.assertNotNull(rescheduled.newDueDate)
    }

    @Test
    fun `focus command parses minutes`() {
        val action = OfflineVoiceParser.parse("начни фокус 50 минут работа", habits, openTasks)
        org.junit.Assert.assertNotNull(action.focusToStart)
        org.junit.Assert.assertEquals(50, action.focusToStart!!.minutes)
    }

    @Test
    fun `focus defaults to 25 minutes`() {
        val action = OfflineVoiceParser.parse("включи фокус", habits, openTasks)
        org.junit.Assert.assertNotNull(action.focusToStart)
        org.junit.Assert.assertEquals(25, action.focusToStart!!.minutes)
    }

    @Test
    fun `day summary question creates no entities`() {
        val action = OfflineVoiceParser.parse("что у меня сегодня", habits, openTasks)
        org.junit.Assert.assertTrue(action.daySummaryRequested)
        org.junit.Assert.assertTrue(action.habitsCompleted.isEmpty())
        org.junit.Assert.assertTrue(action.tasksToAdd.isEmpty())
        org.junit.Assert.assertTrue(action.tasksToComplete.isEmpty())
    }

    @Test
    fun `template creates preset task`() {
        val action = OfflineVoiceParser.parse("шаблон: позвонить маме", habits, openTasks)
        org.junit.Assert.assertEquals(1, action.tasksToAdd.size)
        org.junit.Assert.assertTrue(action.tasksToAdd.first().title.startsWith("Позвонить"))
    }
}
