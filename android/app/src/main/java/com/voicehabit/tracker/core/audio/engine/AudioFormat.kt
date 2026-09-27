package com.voicehabit.tracker.core.audio.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Формат аудио в движке.
 *
 * Ядро работает с PCM16 в одном канале: для распознавания речи это оптимально
 * (Whisper/Groq ждут 16 кГц моно), а стерео-разведение на микрофоне не даёт
 * выигрыша в качестве и вдвое увеличивает объём обработки.
 *
 * Формат — value class, а не перечисление строк: опечатка в строке
 * превращалась бы в «тихую» ошибку формата в середине пайплайна.
 */
data class AudioFormat(
    val sampleRateHz: Int,
    val channels: Int,
    val bitsPerSample: Int = BITS_PCM16
) {
    init {
        require(sampleRateHz in VALID_SAMPLE_RATES) { "Недопустимая частота дискретизации: $sampleRateHz" }
        require(channels in 1..8) { "Недопустимое число каналов: $channels" }
        require(bitsPerSample == BITS_PCM16) { "Поддерживается только PCM16, получено $bitsPerSample" }
    }

    val bytesPerSample: Int = bitsPerSample / 8
    val bytesPerFrame: Int = bytesPerSample * channels
    val isMono: Boolean get() = channels == 1

    fun durationMillis(sampleCount: Int): Long =
        sampleCount.toLong() * 1000L / (sampleRateHz.toLong() * channels)

    fun frameCount(sampleCount: Int): Int = sampleCount / channels

    fun samplesPerMillis(millis: Int): Int = sampleRateHz * channels * millis / 1000

    fun withChannels(newChannels: Int): AudioFormat = copy(channels = newChannels)

    fun withSampleRate(newRate: Int): AudioFormat = copy(sampleRateHz = newRate)

    companion object {
        const val BITS_PCM16 = 16
        val VALID_SAMPLE_RATES = setOf(8_000, 11_025, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000)

        /** Формат, который ожидают облачные STT. */
        val VOICE_16K_MONO = AudioFormat(sampleRateHz = 16_000, channels = 1)

        /** Формат, который чаще всего отдаёт AudioRecord/MediaRecorder по умолчанию. */
        val CD_44K_STEREO = AudioFormat(sampleRateHz = 44_100, channels = 2)
    }
}

/** Кадр аудио: формат + данные. Неизменяемый, чтобы его можно было безопасно передавать между потоками. */
class AudioFrame(
    val format: AudioFormat,
    val samples: ShortArray
) {
    val sampleCount: Int get() = samples.size
    val frameCount: Int get() = format.frameCount(samples.size)
    val durationMillis: Long get() = format.durationMillis(samples.size)

    fun copyInto(target: ShortArray, offset: Int = 0, length: Int = samples.size): Int {
        val count = min(length, samples.size)
        System.arraycopy(samples, 0, target, offset, count)
        return count
    }

    fun mono(): AudioFrame = if (format.isMono) this else AudioFrame(format.withChannels(1), downmixToMono(samples))

    fun resampledTo(targetRateHz: Int): AudioFrame {
        if (format.sampleRateHz == targetRateHz) return this
        return AudioFrame(format.withSampleRate(targetRateHz), resampleLinear(samples, format.sampleRateHz, targetRateHz))
    }

    override fun equals(other: Any?): Boolean =
        other is AudioFrame && other.format == format && other.samples.contentEquals(samples)

    override fun hashCode(): Int = 31 * format.hashCode() + samples.contentHashCode()

    companion object {
        fun silent(format: AudioFormat, millis: Int): AudioFrame =
            AudioFrame(format, ShortArray(format.samplesPerMillis(millis)))
    }
}

/** Стерео → моно: усреднение каналов (безусреднение даёт потерю 3 дБ). */
fun downmixToMono(interleaved: ShortArray): ShortArray {
    if (interleaved.size < 2) return interleaved.copyOf()
    val out = ShortArray(interleaved.size / 2)
    var index = 0
    var read = 0
    while (read + 1 < interleaved.size) {
        out[index++] = (((interleaved[read].toInt() + interleaved[read + 1].toInt()) / 2).toShort())
        read += 2
    }
    return out
}

/** Перемежающийся (interleaved) → последовательный (planar) по каналам. */
fun deinterleave(interleaved: ShortArray, channels: Int): Array<ShortArray> {
    require(channels >= 1) { "Каналов должно быть не меньше одного" }
    val perChannel = interleaved.size / channels
    return Array(channels) { channel ->
        ShortArray(perChannel) { index -> interleaved[index * channels + channel] }
    }
}

/** Последовательный (planar) → перемежающийся. */
fun interleave(planar: Array<ShortArray>): ShortArray {
    require(planar.isNotEmpty()) { "Нужен хотя бы один канал" }
    val channels = planar.size
    val perChannel = planar.minOf { it.size }
    val out = ShortArray(perChannel * channels)
    var write = 0
    for (index in 0 until perChannel) {
        for (channel in 0 until channels) {
            out[write++] = planar[channel][index]
        }
    }
    return out
}

/**
 * Линейная интерполяция для смены частоты дискретизации.
 *
 * Для речи 44.1/48 кГц → 16 кГц этого достаточно: после него всё равно идёт
 * БПФ/шумовой гейт, а полоса выше 8 кГц не несёт речевых признаков.
 * Качество стендается в узле B12 (полифазный ресемплинг).
 */
fun resampleLinear(input: ShortArray, fromRateHz: Int, toRateHz: Int): ShortArray {
    require(fromRateHz > 0 && toRateHz > 0) { "Частоты должны быть положительными" }
    if (fromRateHz == toRateHz || input.isEmpty()) return input.copyOf()

    val ratio = toRateHz.toDouble() / fromRateHz.toDouble()
    val outSize = max(1, (input.size * ratio).toInt())
    val out = ShortArray(outSize)
    val lastIndex = input.size - 1

    for (index in 0 until outSize) {
        val position = index / ratio
        val left = position.toInt().coerceIn(0, lastIndex)
        val right = (left + 1).coerceAtMost(lastIndex)
        val fraction = position - left
        val value = input[left] * (1.0 - fraction) + input[right] * fraction
        out[index] = value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
}

/** Нормализация к пиковому значению с ограничением клиппинга. */
fun ShortArray.normalizedToPeak(targetPeak: Int = Short.MAX_VALUE.toInt()): ShortArray {
    if (isEmpty()) return this
    val peak = maxOf { abs(it.toInt()) }
    if (peak == 0) return copyOf()
    if (peak >= targetPeak) return copyOf()
    val gain = targetPeak.toDouble() / peak
    return ShortArray(size) { index -> (this[index] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
}
