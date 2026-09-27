package com.voicehabit.tracker.core.logging

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Подробный вывод в терминал: время, уровень, тег, атрибуты, поток. */
class StdoutLogSink(
    private val minLevel: LogLevel = LogLevel.DEBUG,
    private val printer: (String) -> Unit = { println(it) }
) : LogSink {

    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return
        printer(event.formatLine())
    }
}

/**
 * Файловый журнал с ротацией по размеру.
 *
 * Хранится в `filesDir/logs`, а не в кэше: систему нельзя заставить удалить
 * его случайно, а по кругу удаления история не нужна. Транскрипты и ключи
 * уже вычищены [Redactor] до записи.
 */
class FileLogSink(
    private val directory: File,
    private val maxFileBytes: Long = 512 * 1024,
    private val maxFiles: Int = 5,
    private val minLevel: LogLevel = LogLevel.DEBUG,
    private val clock: () -> Long = System::currentTimeMillis
) : LogSink {

    private var currentFile: File? = null
    private var writer: PrintWriter? = null
    private var currentSize: Long = 0

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    @Synchronized
    override fun write(event: LogEvent) {
        if (event.level.priority < minLevel.priority) return
        try {
            val line = event.formatLine()
            ensureWriter()
            writer?.apply {
                println(line)
                flush()
            }
            currentSize += line.length + 1
            if (currentSize >= maxFileBytes) rotate()
        } catch (error: Exception) {
            // Журнал не должен никогда ронять приложение
            System.err.println("[logger] не удалось записать событие: ${error.message}")
        }
    }

    private fun ensureWriter() {
        if (writer != null) return
        val file = File(directory, "app-${clock()}.log")
        currentFile = file
        writer = PrintWriter(file.bufferedWriter())
        currentSize = file.length()
    }

    @Synchronized
    fun rotate() {
        writer?.close()
        writer = null
        currentFile = null
        currentSize = 0
        val files = directory.listFiles { file -> file.name.startsWith("app-") && file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(maxFiles - 1).forEach { it.delete() }
    }

    @Synchronized
    override fun flush() {
        writer?.flush()
    }

    @Synchronized
    fun readRecentLines(limit: Int = 200): List<String> {
        flush()
        val files = directory.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()
        return files.flatMap { it.readLines() }.takeLast(limit)
    }

    fun stackTrace(error: Throwable): String = StringWriter().also { sw ->
        error.printStackTrace(PrintWriter(sw))
    }.toString()
}
