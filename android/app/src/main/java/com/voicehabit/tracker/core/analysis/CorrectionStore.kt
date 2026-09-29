package com.voicehabit.tracker.core.analysis

import android.content.SharedPreferences
import com.voicehabit.tracker.domain.model.IntentMode

/**
 * Обучение на правках (#6). Каждая ручная смена режима сохраняется локально:
 * нормализованная фраза → выбранный режим. При следующем похожем разборе
 * сохранённый выбор имеет приоритет над правилами.
 * Данные живут только на устройстве — скопировать это конкуренту невозможно.
 */
class CorrectionStore(private val prefs: SharedPreferences) {

    fun record(transcript: String, chosen: IntentMode) {
        val key = normalize(transcript)
        if (key.length < 4) return
        prefs.edit().putString(KEY_PREFIX + key, chosen.name).apply()
        // Ограничиваем рост: не больше 500 правок.
        val count = prefs.getInt(COUNT_KEY, 0) + 1
        prefs.edit().putInt(COUNT_KEY, count).apply()
    }

    fun lookup(transcript: String): IntentMode? {
        val key = normalize(transcript)
        if (key.length < 4) return null
        // Точное совпадение + совпадение по первым словам (похожая фраза).
        prefs.getString(KEY_PREFIX + key, null)?.let { return runCatching { IntentMode.valueOf(it) }.getOrNull() }
        val head = key.split(' ').take(4).joinToString(" ")
        if (head.length >= 8) {
            prefs.getString(KEY_PREFIX + head, null)?.let { return runCatching { IntentMode.valueOf(it) }.getOrNull() }
        }
        return null
    }

    fun correctionsCount(): Int = prefs.getInt(COUNT_KEY, 0)

    private fun normalize(t: String): String =
        t.lowercase().replace(Regex("[^а-яa-zё ]"), " ").split(' ')
            .filter { it.isNotBlank() }.joinToString(" ").trim()

    companion object {
        private const val KEY_PREFIX = "correction_mode_"
        private const val COUNT_KEY = "correction_count"
    }
}
