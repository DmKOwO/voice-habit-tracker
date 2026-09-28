package com.voicehabit.tracker.core.obsidian

import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.IntentMode
import com.voicehabit.tracker.domain.model.Priority
import com.voicehabit.tracker.domain.model.Task
import com.voicehabit.tracker.domain.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianMarkdownFormatterTest {

    @Test
    fun `formatJournal generates yaml frontmatter, callouts, and dataview tasks`() {
        val record = DigestRecord(
            id = "test_rec_123",
            voiceLogId = "vlog_456",
            mode = IntentMode.JOURNAL,
            modeConfidence = 0.95f,
            title = "Фокус недели",
            gist = "Необходимо сконцентрироваться на ключевой цели.",
            keyPoints = listOf("Утро для архитектуры", "Вечер для рефлексии"),
            decisions = listOf("Перенести созвоны на 15:00"),
            openQuestions = listOf("Как автоматизировать релизы?"),
            nextSteps = listOf("Настроить скрипт сборки"),
            people = listOf("Алексей", "Мария"),
            numbers = listOf("90 минут", "2 задачи"),
            tone = "Фокус",
            transcript = "Сегодня я осознал, что утреннее время наиболее продуктивно.",
            wordCount = 9,
            speechSeconds = 45,
            pinned = true,
            createdAt = 1790600000000L
        )

        val md = ObsidianMarkdownFormatter.formatJournal(record, audioRelativePath = "attachments/voice_123.m4a")

        // Frontmatter
        assertTrue(md.startsWith("---"))
        assertTrue(md.contains("id: \"test_rec_123\""))
        assertTrue(md.contains("title: \"Фокус недели\""))
        assertTrue(md.contains("type: voice-journal"))
        assertTrue(md.contains("audio: \"attachments/voice_123.m4a\""))
        assertTrue(md.contains("duration_seconds: 45"))
        assertTrue(md.contains("source: \"Voice Habit Tracker (Duro)\""))
        assertTrue(md.contains("- voice-journal"))
        assertTrue(md.contains("- pinned"))
        assertTrue(md.contains("- actionable"))
        assertTrue(md.contains("- фокус"))

        // Callout quote
        assertTrue(md.contains("> [!quote] Ключевой инсайт"))
        assertTrue(md.contains("> Необходимо сконцентрироваться на ключевой цели."))

        // Key points
        assertTrue(md.contains("## 📌 Ключевые тезисы"))
        assertTrue(md.contains("- Утро для архитектуры"))
        assertTrue(md.contains("- Вечер для рефлексии"))

        // Decisions (Dataview format - [x])
        assertTrue(md.contains("## 🎯 Принятые решения"))
        assertTrue(md.contains("- [x] Перенести созвоны на 15:00"))

        // Next steps (Dataview format - [ ])
        assertTrue(md.contains("## 🚀 Следующие шаги (Action Items)"))
        assertTrue(md.contains("- [ ] Настроить скрипт сборки"))

        // Questions
        assertTrue(md.contains("## ❓ Открытые вопросы"))
        assertTrue(md.contains("- Как автоматизировать релизы?"))

        // Tip callout
        assertTrue(md.contains("> [!tip] Упомянутые детали"))
        assertTrue(md.contains("**Люди:** Алексей, Мария"))
        assertTrue(md.contains("**Цифры и факты:** 90 минут, 2 задачи"))

        // Audio embed
        assertTrue(md.contains("![[attachments/voice_123.m4a]]"))

        // Transcript
        assertTrue(md.contains("## 📝 Стенограмма аудиозаписи"))
        assertTrue(md.contains("Сегодня я осознал, что утреннее время наиболее продуктивно."))
    }

    @Test
    fun `formatHabitsAndTasks generates clean checklist`() {
        val habits = listOf(
            Habit(id = "h1", title = "Утренняя зарядка", currentStreak = 7, isCompletedToday = true, category = "Здоровье"),
            Habit(id = "h2", title = "Чтение книги", currentStreak = 0, isCompletedToday = false, category = "Саморазвитие")
        )
        val tasks = listOf(
            Task(id = "t1", title = "Релиз v2.1", isCompleted = false, priority = Priority.HIGH, category = "Работа", type = TaskType.QUICK, dueDateIso = "2026-09-30"),
            Task(id = "t2", title = "Купить блокнот", isCompleted = true, priority = Priority.LOW, category = "Покупки", type = TaskType.QUICK)
        )

        val md = ObsidianMarkdownFormatter.formatHabitsAndTasks(habits, tasks, timestamp = 1790600000000L)

        assertTrue(md.contains("type: habits-and-tasks"))
        assertTrue(md.contains("Привычек: 2 (выполнено сегодня: 1) | Активных задач: 1"))
        assertTrue(md.contains("- [x] Утренняя зарядка (🔥 7 дн.) #здоровье"))
        assertTrue(md.contains("- [ ] Чтение книги #саморазвитие"))
        assertTrue(md.contains("- [ ] Релиз v2.1 📅 2026-09-30 #priority/high"))
        assertTrue(md.contains("- [x] Купить блокнот"))
    }

    @Test
    fun `suggestFileName cleans invalid chars and formats date`() {
        val record = DigestRecord(
            id = "test_1",
            title = "План: как сделать / супер-фичу * ? <> |",
            createdAt = 1790600000000L
        )
        val name = ObsidianMarkdownFormatter.suggestFileName(record)
        assertFalse(name.contains(":"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("*"))
        assertFalse(name.contains("?"))
        assertFalse(name.contains("<"))
        assertFalse(name.contains(">"))
        assertFalse(name.contains("|"))
        assertTrue(name.endsWith(".md"))
        assertTrue(name.startsWith("2026-"))
    }

    @Test
    fun `formatJournal with empty record does not emit empty sections`() {
        val emptyRecord = DigestRecord(
            id = "empty_1",
            createdAt = 1790600000000L
        )
        val md = ObsidianMarkdownFormatter.formatJournal(emptyRecord)

        assertTrue(md.startsWith("---"))
        assertTrue(md.contains("id: \"empty_1\""))
        assertTrue(md.contains("title: \"Запись в дневнике\""))
        assertFalse(md.contains("> [!quote]"))
        assertFalse(md.contains("## 📌 Ключевые тезисы"))
        assertFalse(md.contains("## 🎯 Принятые решения"))
        assertFalse(md.contains("## 🚀 Следующие шаги"))
        assertFalse(md.contains("## ❓ Открытые вопросы"))
        assertFalse(md.contains("> [!tip]"))
        assertFalse(md.contains("![["))
        assertFalse(md.contains("## 📝 Стенограмма аудиозаписи"))
    }

    @Test
    fun `formatJournal escapes yaml quotes and handles multiline gist`() {
        val record = DigestRecord(
            id = "quotes_1",
            title = "Мысль о \"продуктивности\" и \\ архитектуре",
            gist = "Первая строка инсайта.\nВторая строка инсайта.",
            createdAt = 1790600000000L
        )
        val md = ObsidianMarkdownFormatter.formatJournal(record)

        assertTrue(md.contains("title: \"Мысль о \\\"продуктивности\\\" и \\\\ архитектуре\""))
        assertTrue(md.contains("> [!quote] Ключевой инсайт\n> Первая строка инсайта.\n> Вторая строка инсайта."))
    }

    @Test
    fun `formatHabitsAndTasks with empty lists produces clean note without crash`() {
        val md = ObsidianMarkdownFormatter.formatHabitsAndTasks(emptyList(), emptyList(), timestamp = 1790600000000L)
        assertTrue(md.contains("Привычек: 0 (выполнено сегодня: 0) | Активных задач: 0"))
        assertFalse(md.contains("## 🌿 Привычки"))
        assertFalse(md.contains("## 🎯 Задачи"))
    }

    @Test
    fun `suggestFileName truncates very long titles and trims properly`() {
        val longTitle = "A".repeat(200)
        val record = DigestRecord(
            id = "long_1",
            title = longTitle,
            createdAt = 1790600000000L
        )
        val fileName = ObsidianMarkdownFormatter.suggestFileName(record)
        // Date (10) + space (1) + title (<=64) + .md (3)
        assertTrue(fileName.length <= 80)
        assertTrue(fileName.endsWith(".md"))
    }
}
