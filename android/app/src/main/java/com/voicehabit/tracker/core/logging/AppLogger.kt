package com.voicehabit.tracker.core.logging

import android.content.Context
import java.io.File

/**
 * Точка входа для журналирования.
 *
 * Требования, из-за которых он существует:
 * 1. Фоновые операции (миграция БД, сид, запись, очередь, OTA) должны быть видны
 *    в интерфейсе и в терминале — иначе «ничего не происходит» невозможно диагностировать.
 * 2. Секреты и транскрипты не должны попадать в лог.
 * 3. Журналирование не имеет права бросать исключения и тормозить путь записи.
 */
class AppLogger constructor(
    private val sinks: List<LogSink>,
    val ring: RingBufferLogSink,
    private val file: FileLogSink?,
    private val clock: () -> Long
) {

    fun d(tag: String, message: String, attributes: Map<String, String> = emptyMap()) =
        log(LogLevel.DEBUG, tag, message, attributes)

    fun i(tag: String, message: String, attributes: Map<String, String> = emptyMap()) =
        log(LogLevel.INFO, tag, message, attributes)

    fun w(tag: String, message: String, attributes: Map<String, String> = emptyMap(), error: Throwable? = null) =
        log(
            LogLevel.WARN, tag,
            if (error == null) message else "$message: ${error.message}",
            attributes
        )

    fun e(tag: String, message: String, attributes: Map<String, String> = emptyMap(), error: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, attributes)
        if (error != null && file != null) {
            file.write(
                LogEvent(clock(), LogLevel.ERROR, tag, file.stackTrace(error), emptyMap())
            )
        }
    }

    private fun log(level: LogLevel, tag: String, message: String, attributes: Map<String, String>) {
        val event = LogEvent(
            timestampMillis = clock(),
            level = level,
            tag = tag,
            message = Redactor.redact(message),
            attributes = Redactor.redactAttributes(attributes),
            threadName = Thread.currentThread().name
        )
        for (sink in sinks) {
            try {
                sink.write(event)
            } catch (error: Throwable) {
                System.err.println("[logger] sink=${sink.javaClass.simpleName} failed: ${error.message}")
            }
        }
    }

    // --- Фоновые операции для UI ---

    fun startOperation(id: String, title: String) = ring.startOperation(id, title)

    fun finishOperation(id: String, result: String? = null) = ring.finishOperation(id, result)

    fun failOperation(id: String, reason: String) = ring.failOperation(id, reason)

    fun <T> trackOperation(id: String, title: String, block: () -> T): T {
        startOperation(id, title)
        return try {
            val result = block()
            finishOperation(id, title)
            result
        } catch (error: Throwable) {
            failOperation(id, error.message ?: error.javaClass.simpleName)
            throw error
        }
    }

    fun flush() = sinks.forEach { runCatching { it.flush() } }

    companion object {
        @Volatile
        private var INSTANCE: AppLogger? = null

        fun instance(): AppLogger = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val ring = RingBufferLogSink()
                AppLogger(
                    sinks = listOf(ring),
                    ring = ring,
                    file = null,
                    clock = System::currentTimeMillis
                ).also { INSTANCE = it }
            }
        }

        /** Подключает stdout и файловый журнал. Вызывается один раз из Application/Activity. */
        fun install(context: Context, ringCapacity: Int = 500): AppLogger =
            synchronized(this) {
                INSTANCE?.let { return it }
                val ring = RingBufferLogSink(capacity = ringCapacity)
                val file = FileLogSink(File(context.filesDir, "logs"))
                val logger = AppLogger(
                    sinks = listOf(ring, StdoutLogSink(), file),
                    ring = ring,
                    file = file,
                    clock = System::currentTimeMillis
                )
                INSTANCE = logger
                logger
            }

        /** Подмена для тестов: без Android-контекста. */
        fun installForTest(
            ring: RingBufferLogSink,
            stdoutPrinter: (String) -> Unit = {},
            clock: () -> Long = System::currentTimeMillis
        ): AppLogger {
            val logger = AppLogger(
                sinks = listOf(ring, StdoutLogSink(minLevel = LogLevel.WARN, printer = stdoutPrinter)),
                ring = ring,
                file = null,
                clock = clock
            )
            INSTANCE = logger
            return logger
        }

        fun reset() {
            synchronized(this) { INSTANCE = null }
        }
    }
}
