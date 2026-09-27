package com.voicehabit.tracker.core.logging

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кольцевой буфер последних событий в памяти.
 *
 * Однонаправленный: журналы нужны UI (экран операций) и должны быть наблюдаемыми
 * как Flow, но при этом не расти без ограничения — на слабом устройстве
 * 10 000 LogEvent это десятки мегабайт.
 */
class RingBufferLogSink(
    private val capacity: Int = 500,
    private val minLevel: LogLevel = LogLevel.DEBUG
) : LogSink {

    private val buffer = ArrayDeque<LogEvent>(capacity)
    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    private val _activeOperations = MutableStateFlow<List<ActiveOperation>>(emptyList())
    val activeOperations: StateFlow<List<ActiveOperation>> = _activeOperations.asStateFlow()

    @Synchronized
    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return
        buffer.addLast(event)
        while (buffer.size > capacity) buffer.removeFirst()
        _events.value = buffer.toList()
    }

    fun clear() {
        synchronized(this) {
            buffer.clear()
            _events.value = emptyList()
        }
    }

    fun snapshot(): List<LogEvent> = _events.value

    // --- Фоновая работа: счётчик активных операций для UI ---

    fun startOperation(id: String, title: String) {
        _activeOperations.value = _activeOperations.value.filterNot { it.id == id } +
            ActiveOperation(id = id, title = title, startedAtMillis = System.currentTimeMillis())
    }

    fun finishOperation(id: String, result: String? = null) {
        val remaining = _activeOperations.value.filterNot { it.id == id }
        _activeOperations.value = remaining
        if (result != null) {
            write(
                LogEvent(
                    timestampMillis = System.currentTimeMillis(),
                    level = LogLevel.INFO,
                    tag = "operation",
                    message = "Операция завершена: $result",
                    attributes = mapOf("id" to id)
                )
            )
        }
    }

    fun failOperation(id: String, reason: String) {
        _activeOperations.value = _activeOperations.value.filterNot { it.id == id }
        write(
            LogEvent(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.ERROR,
                tag = "operation",
                message = "Операция провалена: $reason",
                attributes = mapOf("id" to id)
            )
        )
    }
}

@Immutable
data class ActiveOperation(
    val id: String,
    val title: String,
    val startedAtMillis: Long
)
