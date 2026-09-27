package com.voicehabit.tracker.core.audio.engine

/**
 * Узел обработки аудио.
 *
 * Требования к реализациям:
 * - Не выделять память в [process] (горячий путь).
 * - Работать на месте: данные читаются и пишутся в переданном массиве.
 * - Быть детерминированным: одинаковый вход → одинаковый выход (иначе ломается
 *   audio-diff тест, который сравнивает пайплайн с эталоном).
 */
interface AudioProcessor {
    val name: String

    /** Выключенный узел — прозрачный passthrough, состояние не сбрасывается. */
    var enabled: Boolean

    /** Возвращает количество обработанных сэмплов. */
    fun process(buffer: ShortArray, offset: Int = 0, length: Int = buffer.size - offset): Int

    /** Сброс состояния между записями: иначе фильтр «помнит» предыдущую фразу. */
    fun reset() {}
}

/** Статистика узла: сколько времени съел и сколько сэмплов прошёл. */
data class ProcessorStats(
    val name: String,
    val calls: Long,
    val nanos: Long,
    val samples: Long
) {
    val nanosPerSample: Double get() = if (samples == 0L) 0.0 else nanos.toDouble() / samples
    val realTimeRatio: Double get() = if (samples == 0L) 0.0 else nanos.toDouble() / (samples / 16_000.0 * 1_000_000_000.0)
}

/**
 * Граф обработки: узлы вызываются по порядку, каждый измеряет своё время.
 *
 * Замер внутри цепочки нужен для профилировщика (задача B15): по умолчанию
 * «красивый» интерфейс не даёт понять, какой узел съедает бюджет.
 */
class DspChain(
    private val processors: List<AudioProcessor>,
    override var enabled: Boolean = true
) : AudioProcessor {

    override val name: String = "chain(${processors.joinToString(",") { it.name }})"

    private val calls = LongArray(processors.size)
    private val nanos = LongArray(processors.size)
    private val samples = LongArray(processors.size)
    private val measurementEnabled = true

    override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
        if (!enabled) return length
        var processed = length
        for (index in processors.indices) {
            val processor = processors[index]
            if (!processor.enabled) continue
            val started = if (measurementEnabled) System.nanoTime() else 0L
            val count = processor.process(buffer, offset, processed)
            if (measurementEnabled && count > 0) {
                calls[index]++
                nanos[index] += System.nanoTime() - started
                samples[index] += count
            }
            processed = count
            if (processed <= 0) break
        }
        return processed
    }

    override fun reset() = processors.forEach { it.reset() }

    fun stats(): List<ProcessorStats> = processors.mapIndexed { index, processor ->
        ProcessorStats(processor.name, calls[index], nanos[index], samples[index])
    }

    /** Сброс счётчиков, чтобы измерить только интересующий участок (например, запись). */
    fun resetStats() {
        calls.fill(0)
        nanos.fill(0)
        samples.fill(0)
    }
}
