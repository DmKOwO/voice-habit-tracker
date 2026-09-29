package com.voicehabit.tracker.core.obsidian

import com.voicehabit.tracker.domain.model.DigestRecord
import com.voicehabit.tracker.domain.model.IntentMode
import java.util.UUID

/**
 * Импорт конспектов из Obsidian-хранилища обратно в приложение.
 * Закрывает цикл двусторонней синхронизации: экспорт уже был, теперь и импорт.
 * Парсит файлы, созданные нашим же экспортёром (frontmatter + секции ##).
 */
object ObsidianImporter {
    data class ImportResult(val imported: Int, val skipped: Int)

    fun parseMarkdown(id: String, markdown: String, now: Long = System.currentTimeMillis()): DigestRecord? {
        val lines = markdown.lines()
        fun section(name: String): List<String> {
            val out = mutableListOf<String>()
            var inside = false
            for (line in lines) {
                if (line.startsWith("## ")) {
                    inside = line.removePrefix("## ").trim().startsWith(name)
                    continue
                }
                if (inside) {
                    val t = line.trim().removePrefix("- [ ] ").removePrefix("- [x] ").removePrefix("- ").trim()
                    if (t.isNotEmpty() && !t.startsWith(">") && !t.startsWith("![[")) out.add(t)
                }
            }
            return out
        }
        fun frontmatter(key: String): String =
            lines.firstOrNull { it.trim().startsWith("$key:") }
                ?.substringAfter(":")?.trim()?.trim('"') ?: ""

        val title = frontmatter("title").ifBlank {
            lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: return null
        }
        val gist = section("Ключевые тезисы").firstOrNull() ?: ""
        if (title.isBlank() && gist.isBlank()) return null
        return DigestRecord(
            id = id.ifBlank { "import_" + UUID.randomUUID().toString().take(8) },
            title = title,
            gist = gist,
            keyPoints = section("Ключевые тезисы"),
            decisions = section("Принятые решения"),
            openQuestions = section("Открытые вопросы"),
            nextSteps = section("Следующие шаги"),
            mode = IntentMode.DICTATE,
            modeConfidence = 0.5f,
            createdAt = now
        )
    }
}
