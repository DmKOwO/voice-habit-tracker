package com.voicehabit.tracker.core.logging

/**
 * Убирает секреты и содержимое аудио из журнала.
 *
 * Транскрипты и API-ключи не должны попадать ни в файл, ни в stdout:
 * логи уходят в сторонние системы при сборе крашей, а ключ Groq/Gemini
 * из журнала — это утечка доступа к аккаунту.
 */
object Redactor {

    private val apiKeyPatterns = listOf(
        Regex("gsk_[A-Za-z0-9]{16,}") to "gsk_***",
        Regex("AIza[0-9A-Za-z\\-_]{20,}") to "AIza***",
        Regex("(Bearer\\s+)[A-Za-z0-9\\-._~+/]{12,}") to "Bearer ***"
    )

    private val sensitiveKeys = setOf(
        "apikey", "api_key", "grokkey", "groqapikey", "geminikey", "geminiapikey",
        "token", "authorization", "password", "secret", "authorization"
    )

    private val transcriptKeys = setOf("transcript", "rawtranscript", "text", "summaryline")

    fun redact(value: String): String {
        var result = value
        for ((pattern, replacement) in apiKeyPatterns) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    fun redactAttributes(attributes: Map<String, String>): Map<String, String> =
        attributes.mapValues { (key, value) ->
            when {
                key.lowercase() in sensitiveKeys -> "***"
                key.lowercase() in transcriptKeys -> "<transcript:${value.length} симв.>"
                else -> redact(value)
            }
        }
}
