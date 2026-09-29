package com.voicehabit.tracker.core.analysis

import android.content.SharedPreferences

/**
 * Приватная аналитика использования: только счётчики на устройстве, никакой сети.
 * Отвечает на вопрос «человек вообще пользуется?» без слежки.
 */
class UsageAnalytics(private val prefs: SharedPreferences) {
    fun trackAppOpen() = inc("use_app_open")
    fun trackVoiceRecord() = inc("use_voice")
    fun trackHabitToggle() = inc("use_habit")
    fun trackTaskDone() = inc("use_task")
    fun trackFocusStart() = inc("use_focus")

    fun snapshot(): Map<String, Int> = mapOf(
        "Открытий" to get("use_app_open"),
        "Голосовых записей" to get("use_voice"),
        "Отметок привычек" to get("use_habit"),
        "Закрытых задач" to get("use_task"),
        "Фокус-сессий" to get("use_focus")
    )

    private fun inc(k: String) = prefs.edit().putInt(k, get(k) + 1).apply()
    private fun get(k: String) = prefs.getInt(k, 0)
}
