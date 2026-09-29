package com.voicehabit.tracker.core.analysis

/**
 * Лёгкий «семантический» поиск без эмбеддингов: ранжирование по пересечению
 * токенов со стеммингом-обрезкой. Работает полностью на CPU, без моделей.
 * Это не замена векторному поиску, а честная первая версия «спроси дневник»:
 * находит релевантные конспекты и задачи по смыслу запроса.
 */
object TokenOverlapSearch {
    private fun tokens(text: String): Set<String> =
        text.lowercase().split(' ', ',', '.', '!', '?', ';', ':', '—', '-', '«', '»', '"', '\n')
            .map { it.trim() }.filter { it.length >= 3 }
            .map { if (it.length > 6) it.substring(0, 6) else it }.toSet()

    fun score(query: String, document: String): Double {
        val q = tokens(query)
        val d = tokens(document)
        if (q.isEmpty() || d.isEmpty()) return 0.0
        val inter = q.intersect(d).size
        return inter.toDouble() / q.size
    }

    /** Возвращает документы с оценкой, отсортированные по убыванию релевантности. */
    fun <T> rank(query: String, docs: List<T>, textOf: (T) -> String, minScore: Double = 0.2): List<Pair<T, Double>> =
        docs.map { it to score(query, textOf(it)) }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
}
