package com.voicehabit.tracker.core.logging

import androidx.compose.runtime.Immutable

/** Уровни журнала приложения. */
enum class LogLevel(val priority: Int, val label: String) {
    DEBUG(0, "DBG"),
    INFO(1, "INF"),
    WARN(2, "WRN"),
    ERROR(3, "ERR");

    companion object {
        fun fromLabel(value: String): LogLevel =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: INFO
    }
}

/**
 * Событие журнала. [attributes] — структурированные пары «ключ-значение»,
 * которые попадают и в файл, и в UI, чтобы по ним можно было фильтровать.
 */
@Immutable
data class LogEvent(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
    val threadName: String = ""
) {
    fun formatLine(): String = buildString {
        append(ts())
        append(" [").append(level.label).append("] ")
        append('[').append(tag).append("] ")
        append(message)
        if (attributes.isNotEmpty()) {
            append(' ').append(attributes.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }
        if (threadName.isNotEmpty()) append(" (").append(threadName).append(')')
    }

    private fun ts(): String {
        val s = java.time.Instant.ofEpochMilli(timestampMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return "%02d:%02d:%02d.%03d".format(s.hour, s.minute, s.second, s.nano / 1_000_000)
    }
}

/** Приёмник журнала. Реализации: stdout, файл, кольцевой буфер в памяти. */
interface LogSink {
    fun write(event: LogEvent)

    fun flush() {}
}
