package com.voicehabit.tracker.core.audio.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-free кольцевой буфер для одного писателя и одного читателя (SPSC).
 *
 * Зачем без блокировок: буфер стоит в горячем пути записи звука. Любая блокировка
 * или системный вызов в этом пути — это потенциальный xrunder, который слышно
 * как щелчок. Классическая схема — два монотонных индекса и volatile-публикация:
 * писатель публикует свой индекс через `lazySet` (release), читатель читает его
 * через обычное чтение (acquire) — гонок на уровне памяти нет, локов нет.
 *
 * Ёмкость должна быть степенью двойки: тогда индекс берётся битовой маской
 * вместо деления по модулю.
 */
class SpscAudioBuffer(
    requestedCapacitySamples: Int = DEFAULT_CAPACITY_SAMPLES,
    private val overrunPolicy: OverrunPolicy = OverrunPolicy.DROP_OLDEST
) {
    enum class OverrunPolicy {
        /** Перезаписывать самые старые сэмплы. Режим реального времени: лучше потерять начало, чем встать. */
        DROP_OLDEST,

        /** Отбрасывать новые сэмплы. Режим заготовки: нельзя терять уже записанное. */
        REJECT_NEW
    }

    val capacity: Int = nextPowerOfTwo(requestedCapacitySamples)
    private val mask: Int = capacity - 1
    private val buffer = ShortArray(capacity)

    private val writeIndex = AtomicLong(0)
    private val readIndex = AtomicLong(0)

    // Метрики различают потерю и back-pressure: это принципиально разные вещи.
    // droppedSamples — данные потеряны (DROP_OLDEST), rejectedSamples — писатель
    // не смог записать сейчас (REJECT_NEW, данные целы, писатель ждёт).
    // События и сэмплы тоже считаются отдельно: 5 событий по 1000 сэмплов и
    // 1 событие по 5000 сэмплов одинаковы по частоте, но различаются по объёму.
    private val droppedSamples = AtomicLong(0)
    private val rejectedSamples = AtomicLong(0)
    private val overrunEvents = AtomicLong(0)
    private val underrunCount = AtomicLong(0)
    private val peakOccupancy = AtomicLong(0)

    /** Сколько сэмплов может писать писатель прямо сейчас. */
    fun availableToWrite(): Int {
        val used = (writeIndex.get() - readIndex.get()).toInt().coerceIn(0, capacity)
        return capacity - used
    }

    /** Сколько сэмплов доступно читателю. */
    fun availableToRead(): Int =
        (writeIndex.get() - readIndex.get()).toInt().coerceIn(0, capacity)

    fun isEmpty(): Boolean = writeIndex.get() == readIndex.get()

    fun isFull(): Boolean = availableToWrite() == 0

    /** Сколько сэмплов реально потеряно из-за переполнения. */
    fun droppedSamples(): Long = droppedSamples.get()

    /** Сколько сэмплов отклонено по back-pressure (данные целы, писатель ждёт). */
    fun rejectedSamples(): Long = rejectedSamples.get()

    /** Сколько раз буфер переполнился. */
    fun overrunEvents(): Long = overrunEvents.get()

    fun underrunCount(): Long = underrunCount.get()

    fun peakOccupancy(): Int = peakOccupancy.get().toInt()

    fun clear() {
        readIndex.set(writeIndex.get())
    }

    /**
     * Запись из внешнего буфера. Возвращает, сколько сэмплов реально записано.
     * Выделений в этом пути нет.
     */
    fun write(source: ShortArray, offset: Int = 0, length: Int = source.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= source.size) { "Некорректный диапазон записи" }
        if (length == 0) return 0

        val write = writeIndex.get()
        val read = readIndex.get()
        val used = (write - read).toInt()
        val free = capacity - used

        if (length > free) {
            overrunEvents.addAndGet(1)
            when (overrunPolicy) {
                OverrunPolicy.REJECT_NEW -> {
                    rejectedSamples.addAndGet(length.toLong())
                    return 0
                }
                OverrunPolicy.DROP_OLDEST -> {
                    // Читатель сдвигается вперёд, «съедая» самые старые сэмплы.
                    val toDrop = length - free
                    droppedSamples.addAndGet(toDrop.toLong())
                    readIndex.set(read + toDrop)
                    return write(source, offset, free)
                }
            }
        }

        val index = (write.toInt() and mask)
        val firstChunk = minOf(length, capacity - index)
        System.arraycopy(source, offset, buffer, index, firstChunk)
        if (firstChunk < length) {
            System.arraycopy(source, offset + firstChunk, buffer, 0, length - firstChunk)
        }
        // Публикация данных до индекса: читатель увидит готовые сэмплы.
        writeIndex.lazySet(write + length)

        // Пиковая заполненность считается после записи, иначе «полный буфер»
        // никогда не фиксировался бы.
        peakOccupancy.accumulateAndGet((used + length).toLong()) { previous, current -> maxOf(previous, current) }
        return length
    }

    /**
     * Чтение во внешний буфер. Возвращает, сколько сэмплов реально прочитано;
     * нехватка данных считается xrunder'ом.
     */
    fun read(target: ShortArray, offset: Int = 0, length: Int = target.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= target.size) { "Некорректный диапазон чтения" }
        if (length == 0) return 0

        val read = readIndex.get()
        val write = writeIndex.get()
        val available = (write - read).toInt().coerceIn(0, capacity)

        if (available == 0) {
            underrunCount.addAndGet(1)
            return 0
        }

        val count = minOf(length, available)
        if (length > available) underrunCount.addAndGet(1)

        val index = (read.toInt() and mask)
        val firstChunk = minOf(count, capacity - index)
        System.arraycopy(buffer, index, target, offset, firstChunk)
        if (firstChunk < count) {
            System.arraycopy(buffer, 0, target, offset + firstChunk, count - firstChunk)
        }
        readIndex.lazySet(read + count)
        return count
    }

    /** Снимок содержимого для офлайн-анализа и тестов (теряет «хвост» сверх ёмкости). */
    fun snapshot(): ShortArray {
        val out = ShortArray(availableToRead())
        read(out, 0, out.size)
        return out
    }

    fun stats(): BufferStats = BufferStats(
        capacity = capacity,
        available = availableToRead(),
        droppedSamples = droppedSamples(),
        rejectedSamples = rejectedSamples(),
        overrunEvents = overrunEvents(),
        underruns = underrunCount(),
        peakOccupancy = peakOccupancy()
    )

    fun resetCounters() {
        droppedSamples.set(0)
        rejectedSamples.set(0)
        overrunEvents.set(0)
        underrunCount.set(0)
        peakOccupancy.set(writeIndex.get() - readIndex.get())
    }

    companion object {
        /** ~3 секунды при 16 кГц — достаточно для фразы и достаточно мало для памяти. */
        const val DEFAULT_CAPACITY_SAMPLES = 48_000

        fun nextPowerOfTwo(value: Int): Int {
            require(value > 0) { "Ёмкость должна быть положительной" }
            var capacity = 1
            while (capacity < value) capacity = capacity shl 1
            return capacity
        }
    }
}

data class BufferStats(
    val capacity: Int,
    val available: Int,
    val droppedSamples: Long,
    val rejectedSamples: Long,
    val overrunEvents: Long,
    val underruns: Long,
    val peakOccupancy: Int
) {
    /** Реальная потеря данных (для отчёта профилировщика). */
    val hasLoss: Boolean get() = droppedSamples > 0 || underruns > 0

    /** Была ли нагрузка на писателя (back-pressure). */
    val underPressure: Boolean get() = rejectedSamples > 0 || overrunEvents > 0
    val fillRatio: Double get() = if (capacity == 0) 0.0 else available.toDouble() / capacity
}
