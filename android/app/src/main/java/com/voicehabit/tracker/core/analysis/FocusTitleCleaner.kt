package com.voicehabit.tracker.core.analysis

/**
 * Анти-«копипаст» для названий фокуса и задач.
 *
 * Проблема: сырой поток мыслей («изучение английского от A2 до B1, примерно
 * на 60% дохожу до B1») целиком становился заголовком. Правило: заголовок —
 * короткий и мотивирующий (до ~48 символов, суть + цель), а срезы, проценты
 * и уровни уходят в детали.
 */
object FocusTitleCleaner {

    data class Split(val title: String, val details: String)

    private val FILLER_PREFIX = listOf(
        "надо", "нужно", "хочу", "давай", "пожалуйста", "мне надо", "мне нужно",
        "я хочу", "начни", "начать", "займёмся", "займись"
    )
    private const val MAX_TITLE = 48

    fun split(raw: String): Split {
        var text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return Split("Фокус-сессия", "")
        // Отрезаем модальное начало.
        for (prefix in FILLER_PREFIX.sortedByDescending { it.length }) {
            if (text.lowercase().startsWith(prefix + " ")) {
                text = text.substring(prefix.length).trim()
                break
            }
        }
        // Делим на «суть» и «хвост с цифрами»: хвост — всё после запятой/«примерно».
        val cut = Regex(",| — | – | примерно|где-то|где то").find(text.lowercase())
        val head: String
        val tail: String
        if (cut != null && cut.range.first > 8) {
            head = text.substring(0, cut.range.first).trim().trimEnd(',', '.', '!', ':')
            tail = text.substring(cut.range.first).trimStart(',', ' ', '—', '–').trim()
        } else {
            head = text
            tail = ""
        }
        var title = head.replaceFirstChar { it.uppercase() }
        var details = tail.replaceFirstChar { it.uppercase() }
        if (title.length > MAX_TITLE) {
            // Режем по последнему пробелу, остаток — в детали.
            val lastSpace = title.substring(0, MAX_TITLE).lastIndexOf(' ')
            val cutAt = if (lastSpace > 20) lastSpace else MAX_TITLE
            val rest = title.substring(cutAt).trim()
            title = title.substring(0, cutAt).trimEnd(',', '.', '!', ':')
            details = ((rest + " " + details).trim()).replaceFirstChar { it.uppercase() }
        }
        if (title.isBlank()) title = "Фокус-сессия"
        return Split(title, details)
    }
}
