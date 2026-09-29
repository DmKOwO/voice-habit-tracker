package com.voicehabit.tracker.core.analysis

/**
 * Числа прописью в цифры: «два с половиной часа» → 2.5, «полтора литра» → 1.5.
 * Нужно для голосового ввода количественных привычек («выпил два литра воды»).
 * Чистая функция, покрыта тестами.
 */
object RussianNumberParser {
    private val UNITS = mapOf(
        "ноль" to 0.0, "один" to 1.0, "одна" to 1.0, "одно" to 1.0, "одного" to 1.0,
        "два" to 2.0, "две" to 2.0, "двух" to 2.0, "три" to 3.0, "трех" to 3.0, "трёх" to 3.0,
        "четыре" to 4.0, "пять" to 5.0, "пяти" to 5.0, "шесть" to 6.0, "семь" to 7.0,
        "восемь" to 8.0, "девять" to 9.0, "десять" to 10.0, "десяти" to 10.0,
        "одиннадцать" to 11.0, "двенадцать" to 12.0, "пятнадцать" to 15.0,
        "двадцать" to 20.0, "тридцать" to 30.0, "сорок" to 40.0, "пятьдесят" to 50.0,
        "сто" to 100.0, "двести" to 200.0, "тысяча" to 1000.0, "тысячи" to 1000.0
    )

    /**
     * Ищет первое количественное выражение в тексте.
     * Возвращает значение или null. Дробная часть через «с половиной» / «с четвертью».
     */
    fun findFirst(lowerText: String): Double? {
        val words = lowerText.split(' ', ',', '.', '!', '?', ';', ':', '—', '-')
            .map { it.trim() }.filter { it.isNotEmpty() }
        // «полтора/полторы X» и «пол-X»
        for (i in words.indices) {
            val w = words[i]
            if (w == "полтора" || w == "полторы") return 1.5
            if (w.startsWith("пол") && w.length > 3) {
                UNITS[w.removePrefix("пол")]?.let { return 0.5 }
            }
        }
        for (i in words.indices) {
            val w = words[i]
            // Цифрами: «2,5» / «2.5» / «2»
            w.replace(',', '.').toDoubleOrNull()?.let { base ->
                // «2 с половиной» идёт после цифры
                if (words.getOrNull(i + 1) == "с" && words.getOrNull(i + 2) == "половиной") return base + 0.5
                return base
            }
            UNITS[w]?.let { base ->
                val next1 = words.getOrNull(i + 1)
                val next2 = words.getOrNull(i + 2)
                if (next1 == "с" && next2 == "половиной") return base + 0.5
                if (next1 == "с" && next2 == "четвертью") return base + 0.25
                // «два литра», «сто страниц» — просто число
                return base
            }
        }
        return null
    }
}
