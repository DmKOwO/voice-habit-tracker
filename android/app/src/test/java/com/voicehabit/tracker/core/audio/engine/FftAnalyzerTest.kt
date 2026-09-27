package com.voicehabit.tracker.core.audio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * D1: FFT-тесты. Опорный сигнал — синусоида 440 Гц.
 *
 * Требование: доминирующий бин должен указывать на 440 Гц (±2 Гц), а энергия
 * должна утекать в соседние бины минимально.
 */
class FftAnalyzerTest {

    @Test
    fun `sine 440 hz produces peak exactly at 440 hz`() {
        val sampleRate = 16_000
        val fftSize = 1024
        val samples = AudioFixtures.sine(440.0, sampleRate, fftSize * 1000 / sampleRate, amplitude = 12_000)

        val analyzer = FftAnalyzer(fftSize = fftSize, sampleRateHz = sampleRate, window = WindowType.HANN)
        val spectrum = analyzer.analyze(samples, 0, fftSize)

        val interpolated = spectrum.peakFrequency()
        assertTrue(
            "Пик должен быть на 440 Гц, получено $interpolated",
            abs(interpolated - 440f) <= 2f
        )
        // Бин находится рядом: шаг сетки 15.6 Гц, 440 Гц между бинами 28 и 29
        assertTrue(
            "Бин должен быть в соседнем сетчатом шаге: ${spectrum.binFrequency(spectrum.peakIndex())}",
            abs(spectrum.binFrequency(spectrum.peakIndex()) - 440f) <= 16f
        )
        assertEquals(fftSize / 2, spectrum.magnitudes.size)
    }

    @Test
    fun `peak amplitude matches input amplitude`() {
        val sampleRate = 16_000
        val fftSize = 1024
        val amplitude = 10_000
        val samples = AudioFixtures.sine(440.0, sampleRate, fftSize * 1000 / sampleRate, amplitude = amplitude)

        val spectrum = FftAnalyzer(fftSize, sampleRate, WindowType.HANN).analyze(samples, 0, fftSize)
        // Спектр нормирован к диапазону [-1, 1], поэтому сравниваем с амплитудой
        // в нормированных единицах: 32768 = полная шкала PCM16.
        val normalized = spectrum.peakMagnitude() * 32_768f / amplitude

        // Окно Ханна и нормализация 2/N дают погрешность единиц процентов
        assertTrue("Амплитуда $normalized должна быть близка к 1", abs(normalized - 1f) < 0.08f)
    }

    @Test
    fun `leakage to neighbouring bins stays low for bin centered tone`() {
        val sampleRate = 16_000
        val fftSize = 1024
        // Частота, точно попадающая в бин 28 (28 * 15625 Гц = 437.5 Гц)
        val samples = AudioFixtures.sine(437.5, sampleRate, fftSize * 1000 / sampleRate, amplitude = 12_000)
        val spectrum = FftAnalyzer(fftSize, sampleRate, WindowType.HANN).analyze(samples, 0, fftSize)

        val peak = spectrum.peakIndex()
        // У окна Ханна главный лепесток шириной в три бина, поэтому близкие
        // соседи всегда около -6 дБ. Проверяем утечку за пределами главного лепестка.
        val farLeak = (3..8).map { spectrum.magnitudes[peak + it] }.maxOrNull() ?: 0f
        val ratioDb = 20.0 * log10(((farLeak / spectrum.peakMagnitude()).coerceAtLeast(1e-9f)).toDouble())

        assertTrue("Утечка всего $ratioDb дБ — многовато для окна Ханна", ratioDb < -60.0)
    }

    @Test
    fun `hann window suppresses sidelobes better than rectangular`() {
        val sampleRate = 16_000
        val fftSize = 1024
        // Тон между бинами: именно там и видно разницу боковых лепестков
        val samples = AudioFixtures.sine(443.0, sampleRate, fftSize * 1000 / sampleRate, amplitude = 12_000)

        // Энергия вне главного лепестка (|bin - peak| > 6) относительно пика
        fun sidelobeDb(windowType: WindowType): Double {
            val spectrum = FftAnalyzer(fftSize, sampleRate, windowType).analyze(samples, 0, fftSize)
            val peak = spectrum.peakIndex()
            var sum = 0.0
            for (index in 1 until spectrum.magnitudes.size) {
                if (kotlin.math.abs(index - peak) <= 6) continue
                val magnitude = spectrum.magnitudes[index].toDouble()
                sum += magnitude * magnitude
            }
            return 10.0 * log10((Math.sqrt(sum) / spectrum.peakMagnitude()).coerceAtLeast(1e-12))
        }

        val hann = sidelobeDb(WindowType.HANN)
        val rectangular = sidelobeDb(WindowType.RECTANGULAR)
        println("[fft] боковые лепестки: HANN ${"%.1f".format(hann)} дБ, RECT ${"%.1f".format(rectangular)} дБ")
        assertTrue(
            "Ханн должен давить боковые лепестки сильнее: HANN=$hann, RECT=$rectangular",
            hann < rectangular - 10.0
        )
    }

    @Test
    fun `bin width matches sample rate and fft size`() {
        val spectrum = FftAnalyzer(1024, 16_000).analyze(AudioFixtures.sine(1_000.0, 16_000, 64), 0, 64)
        assertEquals(16_000f / 1024f, spectrum.binFrequency(1), 0.001f)
        assertTrue(abs(spectrum.binFrequency(27) - 421.875f) < 0.01f)
    }

    @Test
    fun `spectral centroid of a tone is close to the tone frequency`() {
        val sampleRate = 16_000
        val samples = AudioFixtures.sine(1_000.0, sampleRate, 64, amplitude = 12_000)
        val spectrum = FftAnalyzer(1024, sampleRate, WindowType.HANN).analyze(samples, 0, 1024)

        val centroid = spectrum.spectralCentroid()
        assertTrue("Центроид $centroid должен быть около 1000 Гц", abs(centroid - 1_000.0) < 120.0)
    }

    @Test
    fun `noise centroid is higher than tone centroid`() {
        val sampleRate = 16_000
        val tone = FftAnalyzer(1024, sampleRate).analyze(AudioFixtures.sine(300.0, sampleRate, 64), 0, 1024)
        val noise = FftAnalyzer(1024, sampleRate).analyze(AudioFixtures.noise(1_024, amplitude = 8_000), 0, 1_024)

        assertTrue(
            "Центроид шума (${noise.spectralCentroid()}) должен быть выше тона (${tone.spectralCentroid()})",
            noise.spectralCentroid() > tone.spectralCentroid()
        )
    }

    @Test
    fun `band energy separates voice band from ultrasonic`() {
        val sampleRate = 16_000
        // Окно заполняется целиком: усечённый сигнал даёт широкую утечку
        val speechLike = FftAnalyzer(1024, sampleRate).analyze(AudioFixtures.sine(1_000.0, sampleRate, 64), 0, 1024)
        val highTone = FftAnalyzer(1024, sampleRate).analyze(AudioFixtures.sine(6_000.0, sampleRate, 64), 0, 1024)

        val speechBand = speechLike.bandEnergyDb(300, 3_400)
        val highBand = speechLike.bandEnergyDb(5_000, 7_000)
        assertTrue("Полоса речи должна быть громче: $speechBand против $highBand", speechBand > highBand + 20)

        val highInHighBand = highTone.bandEnergyDb(5_000, 7_000)
        assertTrue("Тон 6 кГц должен быть в своей полосе", highInHighBand > highBand + 20)
    }

    @Test
    fun `silence gives flat near zero spectrum`() {
        val spectrum = FftAnalyzer(1024, 16_000).analyze(AudioFixtures.silence(16_000, 64), 0, 1024)
        assertTrue("Пик тишины должен быть нулевым", spectrum.peakMagnitude() < 1e-3f)
        assertTrue(spectrum.bandEnergyDb(100, 7_000) < -100.0)
    }

    @Test
    fun `sweep puts energy in the expected range over time`() {
        val sampleRate = 16_000
        val analyzer = FftAnalyzer(1024, sampleRate, WindowType.HANN)
        val sweep = AudioFixtures.sweep(sampleRate, 500)

        val early = analyzer.analyze(sweep, 512, 1_024)
        val late = analyzer.analyze(sweep, sweep.size - 1_024, 1_024)

        // Окно 64 мс само по себе «размазывает» частоту: берём широкие границы,
        // но проверяем именно порядок — низкая в начале, высокая в конце.
        assertTrue("В начале свипа частота низкая: ${early.peakFrequency()}", early.peakFrequency() < 1_200f)
        assertTrue("В конце свипа частота высокая: ${late.peakFrequency()}", late.peakFrequency() > 2_500f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non power of two size is rejected`() {
        FftAnalyzer(fftSize = 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `window longer than fft size is rejected`() {
        FftAnalyzer(fftSize = 256).analyze(AudioFixtures.sine(440.0, 16_000, millis = 100), 0, 1000)
    }
}

/** B17: WAV I/O и генератор фикстур. */
class WavIoTest {

    @Test
    fun `write and read round trip preserves samples and format`() {
        val dir = Files.createTempDirectory("duro-wav").toFile()
        val file = dir.resolve("voice.wav")
        val samples = AudioFixtures.sine(440.0, 16_000, 100, amplitude = 9_000)

        WavIo.write(file, samples)
        val read = WavIo.read(file)

        assertEquals(AudioFormat.VOICE_16K_MONO, read.format)
        assertEquals(samples.size, read.samples.size)
        assertTrue("Байты должны совпасть точно", samples.contentEquals(read.samples))
        assertEquals(100L, read.durationMillis)
    }

    @Test
    fun `stereo wav keeps channels`() {
        val dir = Files.createTempDirectory("duro-wav").toFile()
        val file = dir.resolve("stereo.wav")
        val stereo = AudioFormat.CD_44K_STEREO
        val samples = ShortArray(stereo.samplesPerMillis(50)) { index -> (if (index % 2 == 0) 1_000 else -1_000).toShort() }

        WavIo.write(file, samples, stereo)
        val read = WavIo.read(file)

        assertEquals(2, read.format.channels)
        assertEquals(samples.size, read.samples.size)
        assertEquals(1_000, read.samples[0].toInt())
        assertEquals(-1_000, read.samples[1].toInt())
    }

    @Test
    fun `generated fixtures have expected properties`() {
        val sampleRate = 16_000
        assertTrue(AudioFixtures.silence(sampleRate, 10).all { it == 0.toShort() })
        assertTrue(AudioFixtures.sine(440.0, sampleRate, 10, 8_000).maxOf { abs(it.toInt()) } > 7_000)
        assertTrue(AudioFixtures.toneWithClick(440.0, sampleRate, 100).any { abs(it.toInt()) > 25_000 })
        assertEquals(sampleRate * 250 / 1000, AudioFixtures.sweep(sampleRate, 250).size)
        assertTrue(
            AudioFixtures.humPlusNoise(sampleRate, 100).any { abs(it.toInt()) > 3_500 }
        )
    }

    @Test
    fun `audio diff reports identical signals as close`() {
        val samples = AudioFixtures.sine(440.0, 16_000, 50)
        val result = AudioDiff.compare(samples, samples, tolerance = 8)

        assertTrue(result.isClose)
        assertEquals(0, result.maxAbsoluteError)
        assertEquals(0.0, result.rmsError, 0.0)
        assertEquals(1.0, result.toleranceRatio, 0.0)
    }

    @Test
    fun `audio diff quantifies deviation`() {
        val reference = AudioFixtures.sine(440.0, 16_000, 50)
        val noisy = ShortArray(reference.size) { index ->
            val delta = if (index % 2 == 0) -400 else 400
            (reference[index] + delta).toShort()
        }

        // Допуск 200: отклонение 400 его превышает, поэтому сигнал не «проходит»
        assertFalse("Отклонение должно фиксироваться", AudioDiff.compare(noisy, reference, 200).isClose)
        assertTrue("С допуском 500 отклонение в пределах нормы", AudioDiff.compare(noisy, reference, 500).isClose)

        val result = AudioDiff.compare(noisy, reference, tolerance = 200)
        assertEquals(400, result.maxAbsoluteError)
        assertTrue(result.rmsError in 300.0..450.0)
    }
}
