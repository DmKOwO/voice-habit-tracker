package com.voicehabit.tracker.core.audio.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Убирает постоянную составляющую (DC offset).
 *
 * Микрофоны и дешёвые гарнитуры часто дают ненулевое среднее; из-за него
 * HighPass работает хуже, а спектр забит ненулевым бинном постоянной.
 */
class DcOffsetRemover(
    override var enabled: Boolean = true,
    private val leakage: Double = 0.995
) : AudioProcessor {
    override val name = "dc-offset"
    private var runningMean = 0.0

    override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        for (index in offset until offset + length) {
            val value = buffer[index].toDouble()
            runningMean = runningMean * leakage + value * (1.0 - leakage)
            buffer[index] = (value - runningMean)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return length
    }

    override fun reset() {
        runningMean = 0.0
    }
}

/**
 * High-pass первого порядка: убирает гул сети и низкочастотный дребезг корпуса.
 *
 * Реализован через `y[n] = alpha * (y[n-1] + x[n] - x[n-1])` — устойчив при любой
 * частоте среза, в отличие от «наивного» RC-фильтра, который при низкой частоте
 * теряет точность на 16-битных отсчётах.
 */
class HighPassFilter(
    private val cutoffHz: Double = 80.0,
    private val sampleRateHz: Int = 16_000,
    override var enabled: Boolean = true
) : AudioProcessor {
    override val name = "highpass-${cutoffHz.toInt()}hz"

    private val alpha: Double = run {
        val rc = 1.0 / (2 * Math.PI * cutoffHz)
        val dt = 1.0 / sampleRateHz
        rc / (rc + dt)
    }
    private var previousInput = 0.0
    private var previousOutput = 0.0

    override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        for (index in offset until offset + length) {
            val input = buffer[index].toDouble()
            val output = alpha * (previousOutput + input - previousInput)
            previousInput = input
            previousOutput = output
            buffer[index] = output
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return length
    }

    override fun reset() {
        previousInput = 0.0
        previousOutput = 0.0
    }
}

/**
 * Подавление щелчков (de-clicker).
 *
 * Щелчок — это резкий скачок между соседними отсчётами (обычно от переключения
 * gain, размыкания наушников, удара по корпусу). Лечится ограничением скорости
 * изменения сигнала: вместо скачка — линейная рампа, звук «размазывается» и
 * перестаёт быть щелчком.
 */
class DeClicker(
    private val maxDelta: Int = 1_500,
    private val rampLength: Int = 16,
    override var enabled: Boolean = true
) : AudioProcessor {
    override val name = "de-clicker"

    private var previous = 0
    private var rampRemaining = 0
    private var rampStart = 0
    private var rampTarget = 0
    private var rampStep = 0

    /** Сколько щелчков подавлено — попадает в профиль пайплайна. */
    var clicksSuppressed: Long = 0
        private set

    override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        for (index in offset until offset + length) {
            val current = buffer[index].toInt()
            var output = current

            if (rampRemaining > 0) {
                // Плавно доводим сигнал до целевого значения
                output = rampStart
                rampStart += rampStep
                rampRemaining--
                if (rampRemaining == 0) {
                    previous = output
                }
            } else {
                val delta = current - previous
                if (abs(delta) > maxDelta) {
                    clicksSuppressed++
                    val distance = abs(delta)
                    val steps = min(rampLength, max(1, distance / 2))
                    rampStart = previous + if (delta > 0) (distance / steps) else -(distance / steps)
                    rampStep = if (delta > 0) (distance / steps) else -(distance / steps)
                    rampTarget = current
                    rampRemaining = steps
                    output = previous
                }
            }
            buffer[index] = output.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            if (rampRemaining == 0) previous = output
        }
        return length
    }

    override fun reset() {
        previous = 0
        rampRemaining = 0
        rampStart = 0
        rampTarget = 0
        rampStep = 0
    }
}

/**
 * Адаптивный шумовой гейт.
 *
 * Порог не задаётся константой: уровень шума считается по «тихим» кадрам,
 * гейт открывается с задержкой (attack) и закрывается плавно (release), иначе
 * на словах появляется характерное «абракадабра» от частых срабатываний.
 */
class NoiseGate(
    private val sampleRateHz: Int = 16_000,
    private val frameSize: Int = 256,
    private val thresholdAboveNoiseDb: Double = 9.0,
    private val attackFrames: Int = 2,
    private val releaseFrames: Int = 12,
    override var enabled: Boolean = true
) : AudioProcessor {
    override val name = "noise-gate"

    private val noiseFloorDb = -60.0
    private var accumulatorSquares = 0.0
    private var samplesInFrame = 0
    private var openFrames = 0
    private var gateGain = 0.0

    /** Обновлённая оценка уровня шума, дБFS. */
    var estimatedNoiseFloorDb: Double = -60.0
        private set

    override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        for (index in offset until offset + length) {
            val value = buffer[index].toInt().toDouble()
            accumulatorSquares += value * value
            samplesInFrame++

            if (samplesInFrame >= frameSize) {
                val rms = sqrt(accumulatorSquares / samplesInFrame)
                val db = toDb(rms)
                val threshold = estimatedNoiseFloorDb + thresholdAboveNoiseDb

                val shouldOpen = db > threshold
                openFrames = when {
                    shouldOpen -> minOf(openFrames + 1, attackFrames)
                    else -> maxOf(0, openFrames - 1)
                }
                gateGain = openFrames.coerceIn(0, releaseFrames) /
                    max(1, if (shouldOpen) attackFrames else releaseFrames).toDouble()

                // Шум оцениваем только по закрытым кадрам — иначе гейт «выучит» речь
                if (!shouldOpen) {
                    estimatedNoiseFloorDb = if (estimatedNoiseFloorDb < -60.0) {
                        db
                    } else {
                        estimatedNoiseFloorDb * 0.95 + db * 0.05
                    }
                }
                accumulatorSquares = 0.0
                samplesInFrame = 0
            }

            buffer[index] = (value * gateGain)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return length
    }

    override fun reset() {
        accumulatorSquares = 0.0
        samplesInFrame = 0
        openFrames = 0
        gateGain = 0.0
        estimatedNoiseFloorDb = -60.0
    }
}

/** Результат измерения одного кадра. */
data class AudioLevel(
    val rms: Double,
    val rmsDb: Double,
    val peak: Int,
    val peakDb: Double,
    val clippedSamples: Int
) {
    val isSilence: Boolean get() = rmsDb < -55.0
    val isClipped: Boolean get() = clippedSamples > 0
}

/**
 * Измеритель уровня: RMS, пик, клиппинг.
 *
 * Заменяет декоративную «волну» в интерфейсе: приложение показывает реальные
 * децибелы, и пользователь видит, что микрофон действительно слышит.
 */
class AudioLevelMeter(
    private val clipThreshold: Int = 32_000
) {
    private var sumSquares = 0.0
    private var count = 0
    private var peak = 0
    private var clipped = 0

    fun process(buffer: ShortArray, offset: Int = 0, length: Int = buffer.size - offset): AudioLevel {
        for (index in offset until offset + length) {
            val value = buffer[index].toInt()
            sumSquares += value.toDouble() * value.toDouble()
            count++
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
            if (magnitude >= clipThreshold) clipped++
        }
        return snapshotAndReset()
    }

    fun snapshotAndReset(): AudioLevel {
        val rms = if (count == 0) 0.0 else sqrt(sumSquares / count)
        val level = AudioLevel(
            rms = rms,
            rmsDb = toDb(rms),
            peak = peak,
            peakDb = toDb(peak.toDouble()),
            clippedSamples = clipped
        )
        sumSquares = 0.0
        count = 0
        peak = 0
        clipped = 0
        return level
    }
}

/** Амплитуда → децибелы. Ноль и тишина дают -120 дБ, а не -Inf, чтобы UI не ловил NaN. */
fun toDb(amplitude: Double): Double =
    if (amplitude <= 0.0) -120.0 else max(-120.0, 20.0 * (ln(amplitude / 32_767.0) / ln(10.0)))

/** Децибелы → амплитуда с ограничением диапазона PCM16. */
fun fromDb(db: Double): Double =
    (32_767.0 * Math.pow(10.0, db / 20.0)).coerceIn(0.0, 32_767.0)

/** Сглаживание для визуализации: экспоненциальное скользящее среднее. */
class LevelSmoother(private val coefficient: Double = 0.2) {
    var smoothedDb: Double = -120.0
        private set

    fun update(db: Double): Double {
        smoothedDb = if (smoothedDb <= -119.0) db else smoothedDb * (1 - coefficient) + db * coefficient
        return smoothedDb
    }

    fun reset() {
        smoothedDb = -120.0
    }
}
