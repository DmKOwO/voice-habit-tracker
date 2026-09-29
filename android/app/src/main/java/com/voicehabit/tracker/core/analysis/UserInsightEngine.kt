package com.voicehabit.tracker.core.analysis

/**
 * Вывод фактов о пользователе из его же данных — локально, без сети.
 * Работает на уже записанных задачах и конспектах, ничего не меняет в них:
 * результат кладётся отдельным списком «выводов», ручные факты персоны не трогает.
 */
object UserInsightEngine {
    private val PROFESSION_HINTS: List<Pair<String, List<String>>> = listOf(
        "разработчик" to listOf("код", "релиз", "сервер", "баг", "коммит", " pull", "деплой", "фич"),
        "студент" to listOf("лекци", "экзамен", "курсов", "практик", "научник", "универ"),
        "менеджер" to listOf("созвон", "встреч", "команд", "отчет", "отчёт", "дедлайн", "клиент"),
        "спортсмен/ЗОЖ" to listOf("трениров", "зал", "бег", "протеин", "зарядк"),
        "родитель" to listOf("ребен", "детск", "школ", "садик")
    )
    private val STOP = setOf("купить", "сделать", "надо", "нужно", "сегодня", "завтра", "дело", "задача", "просто")

    data class Insights(val profession: String?, val topics: List<String>, val summary: String)

    fun infer(taskTitles: List<String>, digestTexts: List<String>): Insights {
        val all = (taskTitles + digestTexts).joinToString(" ").lowercase()
        val profession = PROFESSION_HINTS.firstOrNull { (_, hints) -> hints.any { all.contains(it) } }?.first
        val freq = mutableMapOf<String, Int>()
        (taskTitles + digestTexts).flatMap { it.lowercase().split(' ', ',', '.', '!', '?', ';', ':') }
            .map { it.trim() }.filter { it.length >= 5 && it !in STOP }
            .forEach { freq[it] = (freq[it] ?: 0) + 1 }
        val topics = freq.entries.sortedByDescending { it.value }.take(5).map { it.key }
        val summary = buildString {
            if (profession != null) append("Похоже, вы: $profession. ")
            if (topics.isNotEmpty()) append("Частые темы: ${topics.joinToString(", ")}.")
            if (isBlank()) append("Пока мало данных для выводов — продолжайте записывать.")
        }
        return Insights(profession, topics, summary)
    }
}
