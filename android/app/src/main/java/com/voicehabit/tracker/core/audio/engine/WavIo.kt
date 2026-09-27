package com.voicehabit.tracker.core.audio.engine

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Чтение и запись PCM16 WAV без зависимостей от Android.
 *
 * Нужны и для тестов (эталонные файлы, audio-diff), и для headless-CLI, который
 * прогоняет пайплайн по файлам. Поддерживается только PCM16: сжатые форматы
 * (AAC в m4a) для офлайн-анализа не годятся.
 */
object WavIo {

    private const val HEADER_BYTES = 44
    private const val PCM_FORMAT = 1

    fun write(
        file: File,
        samples: ShortArray,
        format: AudioFormat = AudioFormat.VOICE_16K_MONO
    ): File {
        val dataSize = samples.size * format.bytesPerFrame
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write("RIFF".toByteArray())
            raf.write(intToLeBytes(36 + dataSize))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(intToLeBytes(16))
            raf.write(shortToLeBytes(PCM_FORMAT))
            raf.write(shortToLeBytes(format.channels))
            raf.write(intToLeBytes(format.sampleRateHz))
            raf.write(intToLeBytes(format.sampleRateHz * format.bytesPerFrame))
            raf.write(shortToLeBytes(format.bytesPerFrame))
            raf.write(shortToLeBytes(format.bitsPerSample))
            raf.write("data".toByteArray())
            raf.write(intToLeBytes(dataSize))

            val buffer = ByteArray(dataSize)
            var offset = 0
            for (sample in samples) {
                buffer[offset++] = (sample.toInt() and 0xFF).toByte()
                buffer[offset++] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            raf.write(buffer)
        }
        return file
    }

    fun read(file: File): WavData {
        val bytes = file.readBytes()
        require(bytes.size >= HEADER_BYTES) { "Файл слишком короткий для WAV: ${file.name}" }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "Не WAV-файл: ${file.name}"
        }
        require(String(bytes, 12, 4) == "fmt ") { "Нет блока fmt" }
        val audioFormat = bytesToLeShort(bytes, 20)
        require(audioFormat == PCM_FORMAT) { "Поддерживается только PCM16, получено $audioFormat" }

        val channels = bytesToLeShort(bytes, 22)
        val sampleRate = bytesToLeInt(bytes, 24)
        val bitsPerSample = bytesToLeShort(bytes, 34)
        val format = AudioFormat(sampleRateHz = sampleRate, channels = channels, bitsPerSample = bitsPerSample)

        val dataOffset = findDataChunk(bytes)
        val dataSize = bytesToLeInt(bytes, dataOffset + 4)
        val sampleCount = min(dataSize, bytes.size - dataOffset - 8) / format.bytesPerFrame
        val samples = ShortArray(sampleCount) { index ->
            val position = dataOffset + 8 + index * format.bytesPerSample
            val low = bytes[position].toInt() and 0xFF
            val high = bytes[position + 1].toInt()
            ((high shl 8) or low).toShort()
        }
        return WavData(file = file, format = format, samples = samples)
    }

    private fun findDataChunk(bytes: ByteArray): Int {
        var position = 12
        while (position + 8 <= bytes.size) {
            val id = String(bytes, position, 4)
            val size = bytesToLeInt(bytes, position + 4)
            if (id == "data") return position
            position += 8 + size + (size and 1)
        }
        throw IllegalArgumentException("В WAV нет блока data")
    }

    private fun intToLeBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToLeBytes(value: Int) = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    private fun bytesToLeInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun bytesToLeShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

data class WavData(
    val file: File,
    val format: AudioFormat,
    val samples: ShortArray
) {
    val durationMillis: Long get() = format.durationMillis(samples.size)
}

/**
 * Сравнение двух сигналов.
 *
 * Побайтовое сравнение неприменимо к фильтрам: любой IIR-сдвигает фазу, и эталона
 * «в байтах» не существует. Поэтому сравниваем по метрикам отклонения, а решение
 * о прохождении принимает вызывающий (в тестах — с допуском).
 */
data class AudioDiffResult(
    val comparedSamples: Int,
    val maxAbsoluteError: Int,
    val rmsError: Double,
    val errorDb: Double,
    val passedSamples: Int,
    val withinToleranceSamples: Int
) {
    val toleranceRatio: Double get() = if (comparedSamples == 0) 1.0 else withinToleranceSamples.toDouble() / comparedSamples
    val isClose: Boolean get() = withinToleranceSamples == comparedSamples
}

object AudioDiff {

    fun compare(
        actual: ShortArray,
        expected: ShortArray,
        tolerance: Int = 512,
        limit: Int = min(actual.size, expected.size)
    ): AudioDiffResult {
        val count = min(limit, min(actual.size, expected.size))
        if (count == 0) {
            return AudioDiffResult(0, 0, 0.0, -120.0, 0, 0)
        }
        var maxError = 0
        var sumSquares = 0.0
        var expectedSquares = 0.0
        var within = 0
        for (index in 0 until count) {
            val error = abs(actual[index].toInt() - expected[index].toInt())
            if (error > maxError) maxError = error
            if (error <= tolerance) within++
            sumSquares += (error * error).toDouble()
            val expectedValue = expected[index].toInt()
            expectedSquares += (expectedValue * expectedValue).toDouble()
        }
        val rms = Math.sqrt(sumSquares / count)
        val expectedRms = Math.sqrt(expectedSquares / count)
        val errorDb = if (expectedRms <= 0.0) {
            if (rms <= 0.0) -120.0 else 0.0
        } else {
            20.0 * (Math.log10(max(rms, 1e-9) / expectedRms))
        }
        return AudioDiffResult(
            comparedSamples = count,
            maxAbsoluteError = maxError,
            rmsError = rms,
            errorDb = errorDb,
            passedSamples = count,
            withinToleranceSamples = within
        )
    }
}

/** Генератор тестовых сигналов: тоны, шум, щелчки, свип, «речь»-подобный сигнал. */
object AudioFixtures {

    fun sine(freqHz: Double, sampleRateHz: Int = 16_000, millis: Int = 100, amplitude: Int = 8_000): ShortArray {
        val count = sampleRateHz * millis / 1000
        return ShortArray(count) { index ->
            (sin(2.0 * PI * freqHz * index / sampleRateHz) * amplitude).toInt().toShort()
        }
    }

    fun silence(sampleRateHz: Int = 16_000, millis: Int = 100): ShortArray =
        ShortArray(sampleRateHz * millis / 1000)

    fun noise(size: Int, amplitude: Int = 2_000, seed: Int = 7): ShortArray {
        val random = Random(seed)
        return ShortArray(size) { random.nextInt(-amplitude, amplitude).toShort() }
    }

    /** Синус со щелчком в середине — проверка de-clicker. */
    fun toneWithClick(freqHz: Double = 440.0, sampleRateHz: Int = 16_000, millis: Int = 200): ShortArray {
        val samples = sine(freqHz, sampleRateHz, millis)
        samples[samples.size / 2] = 30_000
        samples[samples.size / 2 + 1] = (-30_000).toShort()
        return samples
    }

    /** Линейный свип 100 → 4000 Гц: проверка спектра и ресемплинга. */
    fun sweep(sampleRateHz: Int = 16_000, millis: Int = 500, fromHz: Double = 100.0, toHz: Double = 4_000.0): ShortArray {
        val count = sampleRateHz * millis / 1000
        val samples = ShortArray(count)
        var phase = 0.0
        for (index in 0 until count) {
            val progress = index.toDouble() / count
            val freq = fromHz + (toHz - fromHz) * progress
            phase += 2.0 * PI * freq / sampleRateHz
            samples[index] = (sin(phase) * 6_000).toInt().toShort()
        }
        return samples
    }

    /** Шум с низкочастотным гулом: проверка high-pass и DC. */
    fun humPlusNoise(sampleRateHz: Int = 16_000, millis: Int = 300, seed: Int = 3): ShortArray {
        val base = noise(sampleRateHz * millis / 1000, amplitude = 1_200, seed = seed)
        for (index in base.indices) {
            val hum = (sin(2.0 * PI * 30.0 * index / sampleRateHz) * 4_000).toInt()
            base[index] = (base[index] + hum).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return base
    }
}
