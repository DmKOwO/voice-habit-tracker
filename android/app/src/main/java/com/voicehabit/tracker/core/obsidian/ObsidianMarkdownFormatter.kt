package com.voicehabit.tracker.core.obsidian

import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.Habit
import com.voicehabit.tracker.domain.model.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Формирует файлы Obsidian Markdown с валидным YAML Frontmatter,
 * Obsidian Callouts (> [!quote], > [!tip]) и чекбоксами Dataview (- [ ], - [x]).
 */
object ObsidianMarkdownFormatter {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Преобразует [DigestRecord] в полный Markdown-документ для Obsidian.
     */
    fun formatJournal(
        record: DigestRecord,
        audioRelativePath: String? = null
    ): String = buildString {
        val createdDate = if (record.createdAt > 0) Date(record.createdAt) else Date()
        val formattedDate = dateTimeFormat.format(createdDate)
        val title = record.title.ifBlank { "Запись в дневнике" }

        // YAML Frontmatter
        appendLine("---")
        appendLine("id: \"${record.id}\"")
        appendLine("title: \"${escapeYaml(title)}\"")
        appendLine("date: $formattedDate")
        appendLine("updated: $formattedDate")
        appendLine("type: voice-journal")
        if (record.tone.isNotBlank()) {
            appendLine("mood: \"${escapeYaml(record.tone)}\"")
            appendLine("tone: \"${escapeYaml(record.tone)}\"")
        }
        if (!audioRelativePath.isNullOrBlank()) {
            appendLine("audio: \"${escapeYaml(audioRelativePath)}\"")
        }
        if (record.speechSeconds > 0) {
            appendLine("duration_seconds: ${record.speechSeconds}")
        }
        appendLine("source: \"Voice Habit Tracker (Duro)\"")

        // Frontmatter Tags
        val tags = mutableListOf("voice-journal")
        if (record.pinned) tags.add("pinned")
        if (record.decisions.isNotEmpty() || record.nextSteps.isNotEmpty()) tags.add("actionable")
        if (record.tone.isNotBlank()) {
            val sanitizedTone = sanitizeTag(record.tone)
            if (sanitizedTone.isNotBlank()) tags.add(sanitizedTone)
        }
        appendLine("tags:")
        tags.distinct().forEach { tag ->
            appendLine("  - $tag")
        }
        appendLine("---")
        appendLine()

        // Заголовок H1
        appendLine("# $title")
        appendLine()

        // Ключевой инсайт (Obsidian Callout quote)
        if (record.gist.isNotBlank()) {
            appendLine("> [!quote] Ключевой инсайт")
            record.gist.lineSequence().forEach { line ->
                appendLine("> $line")
            }
            appendLine()
        }

        // Ключевые тезисы
        if (record.keyPoints.isNotEmpty()) {
            appendLine("## Ключевые тезисы")
            record.keyPoints.forEach { point ->
                appendLine("- $point")
            }
            appendLine()
        }

        // Принятые решения (Dataview format: - [x])
        if (record.decisions.isNotEmpty()) {
            appendLine("## Принятые решения")
            record.decisions.forEach { decision ->
                appendLine("- [x] $decision")
            }
            appendLine()
        }

        // Следующие шаги / Action Items (Dataview format: - [ ])
        if (record.nextSteps.isNotEmpty()) {
            appendLine("## Следующие шаги (Action Items)")
            record.nextSteps.forEach { step ->
                appendLine("- [ ] $step")
            }
            appendLine()
        }

        // Открытые вопросы
        if (record.openQuestions.isNotEmpty()) {
            appendLine("## Открытые вопросы")
            record.openQuestions.forEach { question ->
                appendLine("- $question")
            }
            appendLine()
        }

        // Упомянутые люди и цифры (Callout tip)
        if (record.people.isNotEmpty() || record.numbers.isNotEmpty()) {
            appendLine("> [!tip] Упомянутые детали")
            if (record.people.isNotEmpty()) {
                appendLine("> - **Люди:** ${record.people.joinToString(", ")}")
            }
            if (record.numbers.isNotEmpty()) {
                appendLine("> - **Цифры и факты:** ${record.numbers.joinToString(", ")}")
            }
            appendLine()
        }

        // Аудиоплеер Obsidian (wikilink embed)
        if (!audioRelativePath.isNullOrBlank()) {
            appendLine("## Оригинальная запись")
            appendLine("![[$audioRelativePath]]")
            appendLine()
        }

        // Полная стенограмма
        if (record.transcript.isNotBlank()) {
            appendLine("## Стенограмма аудиозаписи")
            appendLine(record.transcript.trim())
            appendLine()
        }
    }

    /**
     * Преобразует список привычек и задач в формат Obsidian Markdown.
     */
    fun formatHabitsAndTasks(
        habits: List<Habit>,
        tasks: List<Task>,
        timestamp: Long = System.currentTimeMillis()
    ): String = buildString {
        val date = Date(timestamp)
        val formattedDate = dateTimeFormat.format(date)

        appendLine("---")
        appendLine("title: \"Привычки и задачи\"")
        appendLine("date: $formattedDate")
        appendLine("updated: $formattedDate")
        appendLine("type: habits-and-tasks")
        appendLine("source: \"Voice Habit Tracker (Duro)\"")
        appendLine("tags:")
        appendLine("  - habits")
        appendLine("  - tasks")
        appendLine("  - productivity")
        appendLine("---")
        appendLine()

        appendLine("# Привычки и задачи")
        appendLine()

        val completedHabitsCount = habits.count { it.isCompletedToday }
        val openTasksCount = tasks.count { !it.isCompleted }

        appendLine("> [!tip] Состояние системы")
        appendLine("> Привычек: ${habits.size} (выполнено сегодня: $completedHabitsCount) | Активных задач: $openTasksCount")
        appendLine()

        // Привычки
        if (habits.isNotEmpty()) {
            appendLine("## Привычки")
            habits.forEach { habit ->
                val checkbox = if (habit.isCompletedToday) "[x]" else "[ ]"
                val streakStr = if (habit.currentStreak > 0) " (${habit.currentStreak} дн.)" else ""
                val categoryStr = if (habit.category.isNotBlank()) " #${sanitizeTag(habit.category)}" else ""
                appendLine("- $checkbox ${habit.title}$streakStr$categoryStr")
            }
            appendLine()
        }

        // Задачи
        if (tasks.isNotEmpty()) {
            appendLine("## Задачи")
            val pending = tasks.filter { !it.isCompleted }
            val completed = tasks.filter { it.isCompleted }

            if (pending.isNotEmpty()) {
                appendLine("### Активные задачи")
                pending.forEach { task ->
                    val priorityTag = when (task.priority.name.uppercase(Locale.ROOT)) {
                        "HIGH" -> " #priority/high"
                        "LOW" -> " #priority/low"
                        else -> ""
                    }
                    val dueStr = task.dueDateIso?.let { " до $it" } ?: ""
                    appendLine("- [ ] ${task.title}$dueStr$priorityTag")
                }
                appendLine()
            }

            if (completed.isNotEmpty()) {
                appendLine("### Завершённые задачи")
                completed.forEach { task ->
                    appendLine("- [x] ${task.title}")
                }
                appendLine()
            }
        }
    }

    /**
     * Форматирует отчёт о завершённом спринте фокуса для сохранения в Obsidian.
     */
    fun formatFocusSprint(
        label: String,
        durationMin: Int,
        completed: Boolean,
        debriefNotes: String? = null,
        steps: List<String> = emptyList()
    ): String = buildString {
        val createdDate = Date()
        val formattedDate = dateTimeFormat.format(createdDate)
        val title = label.ifBlank { "Фокус-спринт" }

        appendLine("---")
        appendLine("title: \"${escapeYaml(title)}\"")
        appendLine("date: $formattedDate")
        appendLine("type: focus-sprint")
        appendLine("duration_minutes: $durationMin")
        appendLine("completed: $completed")
        appendLine("tags:")
        appendLine("  - focus")
        appendLine("  - flow-sprint")
        appendLine("---")
        appendLine()
        appendLine("# $title")
        appendLine()
        appendLine("> [!info] Сессия фокуса")
        appendLine("> Длительность: $durationMin мин | Статус: ${if (completed) "Завершено" else "Прервано"}")
        appendLine()

        if (!debriefNotes.isNullOrBlank()) {
            appendLine("## Дебрифинг и инсайты")
            appendLine(debriefNotes)
            appendLine()
        }

        if (steps.isNotEmpty()) {
            appendLine("## Выполненные шаги")
            steps.forEach { step ->
                appendLine("- [x] $step")
            }
            appendLine()
        }
    }

    /**
     * Генерирует безопасное имя файла заметки: `YYYY-MM-DD [Title].md`
     */
    fun suggestFileName(record: DigestRecord): String {
        val createdDate = if (record.createdAt > 0) Date(record.createdAt) else Date()
        val datePrefix = dateOnlyFormat.format(createdDate)
        val rawTitle = record.title.ifBlank { "Запись ${SimpleDateFormat("HH-mm", Locale.US).format(createdDate)}" }
        val safeTitle = sanitizeFileName(rawTitle)
        return "$datePrefix $safeTitle.md"
    }

    /**
     * Очищает строку от запрещённых в файловой системе символов.
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(64)
            .ifBlank { "Заметка" }
    }

    private fun escapeYaml(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }

    private fun sanitizeTag(tag: String): String {
        return tag
            .replace(Regex("""[^a-zA-Zа-яА-Я0-9_-]"""), "")
            .lowercase(Locale.ROOT)
    }
}
