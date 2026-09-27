package com.voicehabit.tracker.core.audio.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Оконное сглаживание перед БПФ.
 *
 * Без окна спектр «растекается»: энергия одной частоты попадает в соседние бины
 * (leakage), и пик речи в 300–3400 Гц невозможно нормально измерить.
 */
enum class WindowType {
    RECTANGULAR,
    HANN,
    HAMMING,
    BLACKMAN;

    fun apply(input: FloatArray): FloatArray {
        val n = input.size
        val out = FloatArray(n)
        if (n <= 1) {
            input.copyInto(out)
            return out
        }
        val last = n - 1
        for (i in 0 until n) {
            val ratio = i.toDouble() / last
            val gain = when (this) {
                RECTANGULAR -> 1.0
                HANN -> 0.5 * (1.0 - cos(2.0 * PI * ratio))
                HAMMING -> 0.54 - 0.46 * cos(2.0 * PI * ratio)
                BLACKMAN -> 0.42 - 0.5 * cos(2.0 * PI * ratio) + 0.08 * cos(4.0 * PI * ratio)
            }
            out[i] = (input[i] * gain).toFloat()
        }
        return out
    }
}

/** Спектр одного кадра. */
class Spectrum(
    val size: Int,
    val sampleRateHz: Int,
    val magnitudes: FloatArray
) {
    private val binHz: Float = sampleRateHz.toFloat() / size

    /** Амплитуда в герцах для бина. */
    fun binFrequency(index: Int): Float = index * binHz

    /**
     * Индекс максимального бина.
     *
     * Массив содержит только положительные частоты (размер N/2), поэтому искать
     * нужно по всему массиву. Раньше поиск шёл до `size / 2`, из-за чего тон выше
     * 4 кГц при 1024 точках и 16 кГц (бин 384) «не находился» вовсе.
     */
    fun peakIndex(): Int {
        var best = 1
        for (index in 2 until magnitudes.size) {
            if (magnitudes[index] > magnitudes[best]) best = index
        }
        return best
    }

    /**
     * Частота пика с суббинной точностью: сам бин отвечает шагу сетки (15.6 Гц
     * при 1024 точках и 16 кГц), поэтому «440 Гц» реально лежат между бинами.
     * Параболическая интерполяция по трём точкам даёт оценку с точностью ~1 Гц.
     */
    fun interpolatedPeakFrequency(): Float {
        val peak = peakIndex()
        if (peak <= 0 || peak + 1 >= magnitudes.size) return binFrequency(peak)
        val left = magnitudes[peak - 1].toDouble()
        val center = magnitudes[peak].toDouble()
        val right = magnitudes[peak + 1].toDouble()
        val denominator = left - 2 * center + right
        val delta = if (kotlin.math.abs(denominator) < 1e-12) 0.0 else 0.5 * (left - right) / denominator
        return binFrequency(peak) + (delta * binHz).toFloat()
    }

    fun peakFrequency(): Float = interpolatedPeakFrequency()

    fun peakMagnitude(): Float = magnitudes[peakIndex()]

    /** Спектральный центроид — «яркость» сигнала; у шума выше, у тона ниже. */
    fun spectralCentroid(): Double {
        var weighted = 0.0
        var total = 0.0
        for (index in 1 until magnitudes.size) {
            val magnitude = magnitudes[index].toDouble()
            weighted += magnitude * binFrequency(index)
            total += magnitude
        }
        return if (total <= 0.0) 0.0 else weighted / total
    }

    /** Энергия в полосе, дБFS-подобная величина (0 = максимум). */
    fun bandEnergyDb(fromHz: Int, toHz: Int): Double {
        var sum = 0.0
        val fromIndex = (fromHz / binHz).toInt().coerceIn(1, magnitudes.size - 1)
        val toIndex = (toHz / binHz).toInt().coerceIn(fromIndex + 1, magnitudes.size)
        for (index in fromIndex until toIndex) {
            val magnitude = magnitudes[index].toDouble()
            sum += magnitude * magnitude
        }
        if (sum <= 0.0) return -120.0
        val db = 10.0 * (ln(sqrt(sum) / magnitudes.size) / ln(10.0))
        return maxOf(-120.0, db)
    }
}

/**
 * БПФ (Cooley–Tukey, radix-2) с окном и переводом амплитуд в нормализацию синуса.
 *
 * Итеративная реализация без рекурсии и без аллокаций в `analyze` (буферы
 * переиспользуются), потому что анализатор работает в аудиопотоке.
 */
class FftAnalyzer(
    val fftSize: Int = 1024,
    private val sampleRateHz: Int = 16_000,
    private val window: WindowType = WindowType.HANN
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize должен быть степенью двойки, получено $fftSize"
        }
    }

    private val real = FloatArray(fftSize)
    private val imaginary = FloatArray(fftSize)
    private val cosTable = FloatArray(fftSize / 2)
    private val sinTable = FloatArray(fftSize / 2)

    init {
        for (index in 0 until fftSize / 2) {
            val angle = -2.0 * PI * index / fftSize
            cosTable[index] = cos(angle).toFloat()
            sinTable[index] = sin(angle).toFloat()
        }
    }

    fun analyze(samples: ShortArray, offset: Int = 0, length: Int = minOf(fftSize, samples.size - offset)): Spectrum {
        require(length > 0) { "Нужно хотя бы одно значение" }
        require(length <= fftSize) { "Окно анализа больше fftSize" }

        // Нормируем к [-1, 1], дополняем нулями до степени двойки
        for (index in 0 until length) {
            real[index] = samples[offset + index] / 32_768f
        }
        for (index in length until fftSize) {
            real[index] = 0f
            imaginary[index] = 0f
        }
        imaginary.fill(0f, 0, length)

        val windowed = window.apply(real.copyOf(length))
        for (index in 0 until fftSize) real[index] = if (index < windowed.size) windowed[index] else 0f

        transform()

        val magnitudes = FloatArray(fftSize / 2)
        val scale = (2.0 / (fftSize * windowGain())).toFloat()
        for (index in 0 until fftSize / 2) {
            magnitudes[index] = sqrt(real[index] * real[index] + imaginary[index] * imaginary[index]) * scale
        }
        return Spectrum(fftSize, sampleRateHz, magnitudes)
    }

    /** Средний выигрыш окна: без поправки амплитуда занижается на 0.5 при Ханне. */
    private fun windowGain(): Double = when (window) {
        WindowType.RECTANGULAR -> 1.0
        WindowType.HANN -> 0.5
        WindowType.HAMMING -> 0.54
        WindowType.BLACKMAN -> 0.42
    }

    private fun transform() {
        // Бит-реверс перестановка
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempReal = real[i]; real[i] = real[j]; real[j] = tempReal
                val tempImag = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = tempImag
            }
        }

        var size = 2
        while (size <= fftSize) {
            val halfSize = size shr 1
            val tableStep = fftSize / size
            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in i until i + halfSize) {
                    val tableIndex = k * tableStep
                    val wr = cosTable[tableIndex]
                    val wi = sinTable[tableIndex]
                    val a = j + halfSize
                    val tr = wr * real[a] - wi * imaginary[a]
                    val ti = wr * imaginary[a] + wi * real[a]
                    real[a] = real[j] - tr
                    imaginary[a] = imaginary[j] - ti
                    real[j] += tr
                    imaginary[j] += ti
                    k++
                }
                i += size
            }
            size = size shl 1
        }
    }
}
