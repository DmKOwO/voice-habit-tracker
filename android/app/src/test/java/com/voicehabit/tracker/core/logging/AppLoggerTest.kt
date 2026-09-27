package com.voicehabit.tracker.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppLoggerTest {

    private fun logger(ring: RingBufferLogSink) = AppLogger.installForTest(ring) { 1_700_000_000_000 }

    @Test
    fun `api keys never reach the log`() {
        val ring = RingBufferLogSink()
        val log = logger(ring)

        log.i("network", "Groq key=${"gsk_abcdefghijklmnopqrstuvwxyz012345"} used")
        log.i("network", "Gemini ${"AIzaSyA1234567890abcdefghijklmnopqrstuv"}")
        log.i("network", "Authorization: Bearer abcdefghijklmnopqrstuvwxyz.123456")

        val dump = ring.snapshot().joinToString("\n") { it.formatLine() }
        assertFalse("Ключ Groq утек в журнал", dump.contains("gsk_abcdefghijklmnopqrstuvwxyz012345"))
        assertFalse("Ключ Gemini утек в журнал", dump.contains("AIzaSyA1234567890abcdefghijklmnopqrstuv"))
        assertFalse("Bearer-токен утёк", dump.contains("abcdefghijklmnopqrstuvwxyz.123456"))
        assertTrue(dump.contains("***"))
    }

    @Test
    fun `sensitive attributes are masked and transcripts are summarized`() {
        val ring = RingBufferLogSink()
        val log = logger(ring)

        log.i(
            "settings",
            "сохранены настройки",
            mapOf("geminiApiKey" to "AIzaSyA1234567890abcdefghijklmnopqrstuv", "rawTranscript" to "выпил витамины")
        )

        val event = ring.snapshot().last()
        assertEquals("***", event.attributes["geminiApiKey"])
        assertTrue(event.attributes.getValue("rawTranscript").startsWith("<transcript:"))
        assertFalse(event.formatLine().contains("выпил витамины"))
    }

    @Test
    fun `ring buffer keeps only the newest events and preserves order`() {
        val ring = RingBufferLogSink(capacity = 3)
        val log = logger(ring)

        repeat(10) { index -> log.d("bulk", "событие $index") }

        val events = ring.snapshot()
        assertEquals(3, events.size)
        assertEquals("событие 7", events[0].message)
        assertEquals("событие 9", events[2].message)
        assertTrue(events[0].timestampMillis <= events[2].timestampMillis)
    }

    @Test
    fun `level filter drops debug events`() {
        val ring = RingBufferLogSink(minLevel = LogLevel.WARN)
        val log = logger(ring)

        log.d("tag", "отладка")
        log.i("tag", "инфо")
        log.w("tag", "предупреждение")
        log.e("tag", "ошибка")

        assertEquals(listOf("предупреждение", "ошибка"), ring.snapshot().map { it.message })
    }

    @Test
    fun `file sink rotates by size and keeps a bounded number of files`() {
        val dir = Files.createTempDirectory("duro-logs").toFile()
        var stamp = 0L
        val sink = FileLogSink(directory = dir, maxFileBytes = 200, maxFiles = 3) { stamp++ }

        repeat(60) { index -> sink.write(LogEvent(0L, LogLevel.INFO, "bulk", "строка $index ${"x".repeat(20)}")) }
        sink.flush()

        val files = dir.listFiles { f -> f.name.endsWith(".log") }?.toList().orEmpty()
        assertTrue("Файлов должно быть не больше лимита: ${files.size}", files.size <= 3)
        assertTrue("Хотя бы один файл создан", files.isNotEmpty())
        assertTrue(sink.readRecentLines().any { it.contains("строка") })
    }

    @Test
    fun `background operations are tracked and cleared`() {
        val ring = RingBufferLogSink()
        val log = logger(ring)

        log.startOperation("op-1", "Расшифровка записи")
        log.startOperation("op-2", "Обновление виджетов")
        assertEquals(2, ring.activeOperations.value.size)

        log.finishOperation("op-1", "Расшифровка записи")
        assertEquals(listOf("op-2"), ring.activeOperations.value.map { it.id })

        log.failOperation("op-2", "нет сети")
        assertTrue(ring.activeOperations.value.isEmpty())
        assertTrue(ring.snapshot().any { it.level == LogLevel.ERROR && it.message.contains("нет сети") })
    }

    @Test
    fun `failing sink never breaks logging`() {
        val ring = RingBufferLogSink()
        val broken = object : LogSink {
            override fun write(event: LogEvent) = throw IllegalStateException("диск полон")
        }
        val healthy = RingBufferLogSink(capacity = 10)
        val custom = AppLogger(listOf(broken, healthy), healthy, null) { 42L }

        custom.i("tag", "сообщение переживает сбой приёмника")

        assertEquals(1, healthy.snapshot().size)
        assertEquals(42L, healthy.snapshot().first().timestampMillis)
    }

    @Test
    fun `track operation reports success and failure`() {
        val ring = RingBufferLogSink()
        val log = logger(ring)

        val value = log.trackOperation("op-ok", "Расчёт") { 42 }
        assertEquals(42, value)
        assertTrue(ring.snapshot().any { it.message.contains("Расчёт") })

        runCatching { log.trackOperation("op-fail", "Расчёт") { error("boom") } }
        assertTrue(ring.snapshot().any { it.level == LogLevel.ERROR && it.message.contains("boom") })
        assertTrue(ring.activeOperations.value.isEmpty())
    }

    @Test
    fun `event line is readable and includes level tag and thread`() {
        val event = LogEvent(
            timestampMillis = 1_700_000_000_000,
            level = LogLevel.WARN,
            tag = "audio",
            message = "underrun",
            attributes = mapOf("xruns" to "3"),
            threadName = "DefaultDispatcher-worker-1"
        )
        val line = event.formatLine()
        assertTrue(line.contains("[WRN]"))
        assertTrue(line.contains("[audio]"))
        assertTrue(line.contains("xruns=3"))
        assertTrue(line.contains("DefaultDispatcher-worker-1"))
    }
}
