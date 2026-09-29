package com.voicehabit.tracker.core.analysis

/**
 * H88. Определение отрицания.
 *
 * «Я не делал зарядку» и «я делал зарядку» — это противоположные факты о данных
 * пользователя. Раньше обе фразы содержали один и тот же маркер «делал», и
 * привычка отмечалась выполненной в обоих случаях. Это единственный класс ошибок
 * разбора, который портит данные, а не только впечатление: пользователь видит
 * в истории то, чего не было, и перестаёт доверять экрану.
 *
 * Логика намеренно консервативна: сомнительную фразу лучше не закрыть, чем
 * закрыть несуществующую отметку. Поэтому [isNegated] требует явного отрицания
 * рядом с глаголом, а не где-то в предложении.
 */
object NegationDetector {

    /**
     * Частицы и обороты, которые прямо отменяют действие.
     * Отделены от [PASSIVE_SKIP]: «не делал» отменяет, «ничего не делал» —
     * тоже отменяет, но «не мог не делать» отрицать не должен (крайне редко).
     */
    private val NEGATION_MARKERS = listOf(
        "не", "нет", "ни", "без", "не успел", "не успела", "не успели",
        "не смог", "не смогла", "не получилось", "не вышло", "не вышлось",
        "забил", "забила", "забили", "забил на", "забила на",
        "пропустил", "пропустила", "пропустили",
        "забыл", "забыла", "забыли",
        "не ходил", "не ходила", "не был", "не была", "не были",
        "не делал", "не делала", "не делали", "не делал целый",
        "не выпил", "не выпила", "не съел", "не поел", "не поела",
        "не бегал", "не бегала", "не читал", "не читала",
        "не тренировался", "не тренировалась", "не занимался", "не занималась",
        "не закончил", "не закончила", "не отправил", "не отправила",
        "не написал", "не написала", "не позвонил", "не позвонила",
        "не купил", "не купила", "не сходил", "не сходила",
        "не принял", "не приняла", "не выходил", "не выходила",
        "не встал", "не встала", "не ложился", "не ложилась",
        "не доспал", "не доспала", "не отдохнул", "не отдохнула"
    )

    /**
     * Обороты, после которых отрицание относится не к действию, а к ожиданию.
     * «Не доделал, но горжусь собой» всё равно отрицание; здесь — наоборот:
     * слова, которые сами по себе не отменяют действие.
     */
    private val AMBIGUOUS_NEUTRAL = listOf(
        "не считая", "не глядя", "не зная", "не помню", "не уверен", "не уверена",
        "не просто", "не только"
    )

    private const val DEFAULT_WINDOW = 4

    /**
     * Есть ли отрицание перед [target] в пределах окна из [windowWords] слов.
     *
     * Ищет первое вхождение [target] и смотрит только назад: в русском языке
     * отрицание стоит перед глаголом («не делал», а не «делал не»).
     */
    fun isNegatedBefore(
        lowerText: String,
        target: String,
        windowWords: Int = DEFAULT_WINDOW
    ): Boolean {
        val index = lowerText.indexOf(target)
        if (index < 0) return false
        return isNegatedInWindow(lowerText, index, windowWords)
    }

    /**
     * Отрицается ли весь текст. Для фраз вида «ничего не делал»,
     * где маркер и отрицание стоят в разных местах предложения.
     */
    fun isNegated(lowerText: String, windowWords: Int = DEFAULT_WINDOW): Boolean {
        if (containsNeutralContext(lowerText)) return false
        val words = lowerText.split(' ', '\n', '\t', ',', '.', '!', '?', ';', ':', '—', '-')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return false

        for (i in words.indices) {
            val word = words[i]
            // Многословные обороты ищем целиком, начиная с текущего слова.
            if (NEGATION_MARKERS.any { it.length > 1 && it.contains(' ') }) {
                val phrase = words.subList(i, minOf(i + 3, words.size)).joinToString(" ")
                if (NEGATION_MARKERS.any { phrase.startsWith(it) }) return true
            }
            if (word !in NEGATION_MARKERS && !word.startsWith("не")) continue
            // Частица «не» сама по себе отменяет только если рядом есть действие.
            // Иначе «не Москва» не про привычки.
            val next = words.getOrNull(i + 1) ?: continue
            if (next.isActionLike()) return true
        }
        return false
    }

    /** Частица отрицания в [windowWords] слов до позиции [targetIndex]. */
    private fun isNegatedInWindow(text: String, targetIndex: Int, windowWords: Int): Boolean {
        if (containsNeutralContext(text)) return false
        val before = text.substring(0, targetIndex)
        val words = before.split(' ', '\n', '\t', ',', '.', '!', '?', ';', ':', '—', '-')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return false

        val window = words.takeLast(windowWords)
        return window.any { w ->
            w in NEGATION_MARKERS || (w.startsWith("не") && w.length > 2)
        }
    }

    private fun containsNeutralContext(text: String): Boolean =
        AMBIGUOUS_NEUTRAL.any { text.contains(it) }

    private fun String.isActionLike(): Boolean =
        length >= 3 && (endsWith("л") || endsWith("ла") || endsWith("ть") || endsWith("лся") || endsWith("лась"))
}
