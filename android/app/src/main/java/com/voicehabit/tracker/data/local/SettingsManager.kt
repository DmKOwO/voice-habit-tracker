package com.voicehabit.tracker.data.local

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("duro_settings", Context.MODE_PRIVATE)

    var groqApiKey: String
        get() = prefs.getString(KEY_GROQ_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GROQ_API_KEY, value.trim()).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var customBackendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "http://10.0.2.2:8000/") ?: "http://10.0.2.2:8000/"
        set(value) = prefs.edit().putString(KEY_BACKEND_URL, value.trim()).apply()

    var useDirectCloud: Boolean
        get() = prefs.getBoolean(KEY_USE_DIRECT_CLOUD, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_DIRECT_CLOUD, value).apply()

    var lastGithubCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_GITHUB_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_GITHUB_CHECK, value).apply()

    /** Тег релиза, уже загруженного автоматически — не качаем одно и то же дважды. */
    var lastGithubAutoTag: String
        get() = prefs.getString(KEY_LAST_GITHUB_AUTO_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_GITHUB_AUTO_TAG, value.trim()).apply()

    /**
     * Эффективные ключи ИИ: введённые вручную перекрывают встроенные в сборку.
     * Встроенные подставляет CI из секретов — приложение работает из коробки,
     * ничего вставлять не нужно.
     */
    val effectiveGroqApiKey: String
        get() = groqApiKey.ifBlank { BundledKeys.groq }

    val effectiveGeminiApiKey: String
        get() = geminiApiKey.ifBlank { BundledKeys.gemini }

    /** В сборке есть встроенные ключи (подставил CI). */
    val hasBundledKeys: Boolean
        get() = BundledKeys.groq.isNotBlank() && BundledKeys.gemini.isNotBlank()

    /** Сейчас используются именно встроенные ключи (свои не введены). */
    val isUsingBundledKeys: Boolean
        get() = groqApiKey.isBlank() && geminiApiKey.isBlank() && hasBundledKeys

    val hasDirectKeys: Boolean
        get() = effectiveGroqApiKey.isNotBlank() && effectiveGeminiApiKey.isNotBlank()

    var themePreset: com.voicehabit.tracker.presentation.theme.AppThemePreset
        get() = com.voicehabit.tracker.presentation.theme.AppThemePreset.fromId(
            prefs.getString(KEY_THEME_PRESET, com.voicehabit.tracker.presentation.theme.AppThemePreset.NOIR_INK.id)
                ?: com.voicehabit.tracker.presentation.theme.AppThemePreset.NOIR_INK.id
        )
        set(value) = prefs.edit().putString(KEY_THEME_PRESET, value.id).apply()

    var amoledTheme: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_THEME, value).apply()

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.85f, 1.3f)).apply()

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "ru") ?: "ru"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS, value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    var offlineSttEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_STT, false)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_STT, value).apply()

    var lastWeeklyReviewEpochDay: Long
        get() = prefs.getLong(KEY_LAST_WEEKLY_REVIEW, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_WEEKLY_REVIEW, value).apply()

    var lastAutoBackupMillis: Long
        get() = prefs.getLong(KEY_LAST_AUTO_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_BACKUP, value).apply()

    var customCategoriesCsv: String
        get() = prefs.getString(KEY_CUSTOM_CATEGORIES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_CATEGORIES, value).apply()

    val customCategories: List<String>
        get() = customCategoriesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    var obsidianVaultUri: String
        get() = prefs.getString(KEY_OBSIDIAN_VAULT_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OBSIDIAN_VAULT_URI, value.trim()).apply()

    var obsidianAutoExport: Boolean
        get() = prefs.getBoolean(KEY_OBSIDIAN_AUTO_EXPORT, false)
        set(value) = prefs.edit().putBoolean(KEY_OBSIDIAN_AUTO_EXPORT, value).apply()

    val hasObsidianVault: Boolean
        get() = obsidianVaultUri.isNotBlank()

    // ---------- Контекст обо мне (User Persona & Memory Engine) ----------

    /** Общее key-value хранилище для UI-состояния и выводов (без новых колонок). */
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String = ""): String = prefs.getString(key, default) ?: default
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)

    /**
     * Автовыводы о пользователе из его задач и конспектов (отдельно от ручных
     * фактов персоны — не конфликтует, ручное всегда главнее).
     */
    var inferredInsights: String
        get() = prefs.getString(KEY_INFERRED_INSIGHTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_INFERRED_INSIGHTS, value).apply()

    var userPersonaHardFacts: String
        get() = prefs.getString(KEY_USER_PERSONA_HARD_FACTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PERSONA_HARD_FACTS, value.trim()).apply()

    var userPersonaActiveFocus: String
        get() = prefs.getString(KEY_USER_PERSONA_ACTIVE_FOCUS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PERSONA_ACTIVE_FOCUS, value.trim()).apply()

    var userPersonaMemoryLog: Set<String>
        get() = prefs.getStringSet(KEY_USER_PERSONA_MEMORY_LOG, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_USER_PERSONA_MEMORY_LOG, value).apply()

    fun addMemoryFact(fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isNotBlank()) {
            val updated = userPersonaMemoryLog.toMutableSet()
            updated.add(trimmed)
            userPersonaMemoryLog = updated
        }
    }

    fun removeMemoryFact(fact: String) {
        val updated = userPersonaMemoryLog.toMutableSet()
        updated.remove(fact)
        userPersonaMemoryLog = updated
    }

    fun getUserPersonaContext(): String {
        val sb = StringBuilder()
        if (userPersonaHardFacts.isNotBlank()) {
            sb.appendLine("Обо мне, интересы и сферы жизни: $userPersonaHardFacts")
        }
        if (userPersonaActiveFocus.isNotBlank()) {
            sb.appendLine("Идеи, замыслы и текущий фокус: $userPersonaActiveFocus")
        }
        if (userPersonaMemoryLog.isNotEmpty()) {
            sb.appendLine("Факты памяти и ориентиры:")
            userPersonaMemoryLog.take(15).forEach { sb.appendLine("- $it") }
        }
        return sb.toString().trim()
    }

    companion object {
        /** Канал OTA по умолчанию: релизы на аккаунте владельца. */
        const val DEFAULT_GITHUB_REPO = "DmKOwO/voice-habit-tracker"
        private const val KEY_OBSIDIAN_VAULT_URI = "key_obsidian_vault_uri"
        private const val KEY_OBSIDIAN_AUTO_EXPORT = "key_obsidian_auto_export"
        private const val KEY_GROQ_API_KEY = "key_groq_api_key"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_BACKEND_URL = "key_backend_url"
        private const val KEY_USE_DIRECT_CLOUD = "key_use_direct_cloud"
        private const val KEY_LAST_GITHUB_CHECK = "key_last_github_check"
        private const val KEY_LAST_GITHUB_AUTO_TAG = "key_last_github_auto_tag"
        private const val KEY_AMOLED_THEME = "key_amoled_theme"
        private const val KEY_THEME_PRESET = "key_theme_preset"
        private const val KEY_FONT_SCALE = "key_font_scale"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_QUIET_HOURS = "key_quiet_hours"
        private const val KEY_ONBOARDING_DONE = "key_onboarding_done"
        private const val KEY_AUTO_UPDATE_CHECK = "key_auto_update_check"
        private const val KEY_TTS_ENABLED = "key_tts_enabled"
        private const val KEY_OFFLINE_STT = "key_offline_stt"
        private const val KEY_LAST_WEEKLY_REVIEW = "key_last_weekly_review"
        private const val KEY_LAST_AUTO_BACKUP = "key_last_auto_backup"
        private const val KEY_CUSTOM_CATEGORIES = "key_custom_categories"
        private const val KEY_USER_PERSONA_HARD_FACTS = "key_user_persona_hard_facts"
        private const val KEY_USER_PERSONA_ACTIVE_FOCUS = "key_user_persona_active_focus"
        private const val KEY_USER_PERSONA_MEMORY_LOG = "key_user_persona_memory_log"
        private const val KEY_INFERRED_INSIGHTS = "key_inferred_insights"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Ключи ИИ, встроенные в сборку через BuildConfig (CI подставляет из секретов).
 * Чтение через рефлексию — как BuildConfigCompat: unit-тесты и старые сборки
 * без этих полей возвращают "" вместо падения.
 */
internal object BundledKeys {
    val groq: String get() = read("BUNDLED_GROQ_API_KEY")
    val gemini: String get() = read("BUNDLED_GEMINI_API_KEY")

    private fun read(field: String): String = runCatching {
        Class.forName("com.voicehabit.tracker.BuildConfig")
            .getField(field).get(null) as? String ?: ""
    }.getOrDefault("")
}
