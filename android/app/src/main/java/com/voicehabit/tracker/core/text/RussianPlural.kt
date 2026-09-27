package com.voicehabit.tracker.core.text

import java.util.Locale

/**
 * Русские числительные: «1 выжимка», «2 выжимки», «5 выжимок».
 *
 * ## Зачем отдельный объект
 *
 * Раньше число подставлялось строкой: «3 выжимок разговоров» — заметная ошибка
 * в заголовке экрана, которую пользователь видит первым. Правило у русских числительных
 * не выводится из самого числа: нужно знать ещё и то, с чем число считается
 * («часа»/«часов» различаются при одинаковом «2»), поэтому [plural] принимает три формы.
 */
object RussianPlural {

    /**
     * @param count число
     * @param one форма для 1 (и 21, 31, …): выжимка
     * @param few  форма для 2–4 (и 22–24, …): выжимки
     * @param many форма для 0, 5–20 (и 25–30, …): выжимок
     */
    fun plural(count: Int, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(count)
        val mod100 = abs % 100
        if (mod100 in 11..14) return many
        return when (abs % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    /** «3 выжимки» / «1 выжимка разговора» / «5 выжимок разговоров». */
    fun count(count: Int, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(count)
        val mod100 = abs % 100
        val withTail = when {
            mod100 in 11..14 -> many
            abs % 10 == 1 -> one
            abs % 10 in 2..4 -> few
            else -> many
        }
        return String.format(Locale.getDefault(), "%d %s", count, withTail)
    }
}
