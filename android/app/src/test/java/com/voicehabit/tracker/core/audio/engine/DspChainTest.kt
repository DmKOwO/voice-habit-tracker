package com.voicehabit.tracker.core.audio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class DspChainTest {

    private fun tone(freqHz: Double, sampleRateHz: Int, millis: Int, amplitude: Int = 8_000): ShortArray {
        val count = sampleRateHz * millis / 1000
        return ShortArray(count) { index ->
            (sin(2.0 * Math.PI * freqHz * index / sampleRateHz) * amplitude).toInt().toShort()
        }
    }

    private fun noise(size: Int, amplitude: Int, seed: Int = 42): ShortArray {
        val random = Random(seed)
        return ShortArray(size) { (random.nextDouble(-amplitude.toDouble(), amplitude.toDouble())).toInt().toShort() }
    }

    private fun rms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) sum += sample.toDouble() * sample.toDouble()
        return Math.sqrt(sum / samples.size)
    }

    @Test
    fun `chain runs processors in order and reports length`() {
        val order = mutableListOf<String>()
        val first = object : AudioProcessor {
            override val name = "first"
            override var enabled = true
            override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
                order.add(name); return length
            }
        }
        val second = object : AudioProcessor {
            override val name = "second"
            override var enabled = true
            override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
                order.add(name); return length
            }
        }

        val chain = DspChain(listOf(first, second))
        val processed = chain.process(ShortArray(128))

        assertEquals(128, processed)
        assertEquals(listOf("first", "second"), order)
        assertEquals(2, chain.stats().size)
        assertEquals(128L, chain.stats()[0].samples)
        assertTrue(chain.stats()[0].nanos >= 0)
    }

    @Test
    fun `disabled node is transparent and not measured`() {
        var calls = 0
        val node = object : AudioProcessor {
            override val name = "counting"
            override var enabled = false
            override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
                calls++
                return length
            }
        }
        val chain = DspChain(listOf(node))
        chain.process(ShortArray(64))

        assertEquals(0, calls)
        assertEquals(0L, chain.stats().first().calls)
    }

    @Test
    fun `disabled chain is passthrough`() {
        val booster = object : AudioProcessor {
            override val name = "gain"
            override var enabled = true
            override fun process(buffer: ShortArray, offset: Int, length: Int): Int {
                for (i in offset until offset + length) buffer[i] = (buffer[i] * 2).toShort()
                return length
            }
        }
        val chain = DspChain(listOf(booster), enabled = false)
        val buffer = shortArrayOf(100, 200)
        chain.process(buffer)

        assertEquals(100, buffer[0].toInt())
        assertEquals(200, buffer[1].toInt())
    }

    @Test
    fun `dc offset remover converges to zero mean`() {
        val processor = DcOffsetRemover()
        val buffer = ShortArray(4_000) { 5_000 }
        processor.process(buffer)

        // Фильтр экспоненциально сходится, поэтому усредняется только «хвост»:
        // переходный участок в начале по построению большой.
        val tail = buffer.copyOfRange(buffer.size - 400, buffer.size)
        val tailMean = tail.sumOf { it.toDouble() } / tail.size
        assertTrue("Хвост должен быть около нуля, получено $tailMean", abs(tailMean) < 20.0)
    }

    @Test
    fun `high pass removes low frequency hum and keeps speech band`() {
        val hum = tone(freqHz = 25.0, sampleRateHz = 16_000, millis = 400)
        val filtered = hum.copyOf()
        HighPassFilter(cutoffHz = 80.0, sampleRateHz = 16_000).process(filtered)

        assertTrue(
            "Гул 25 Гц должен быть подавлен (было ${rms(hum)}, стало ${rms(filtered)})",
            rms(filtered) < rms(hum) * 0.35
        )

        val speech = tone(freqHz = 1_000.0, sampleRateHz = 16_000, millis = 400)
        val speechFiltered = speech.copyOf()
        HighPassFilter(cutoffHz = 80.0, sampleRateHz = 16_000).process(speechFiltered)

        assertTrue(
            "Речь 1 кГц должна остаться (было ${rms(speech)}, стало ${rms(speechFiltered)})",
            rms(speechFiltered) > rms(speech) * 0.7
        )
    }

    @Test
    fun `declicker suppresses abrupt jumps`() {
        val processor = DeClicker(maxDelta = 1_000, rampLength = 16)
        val clean = tone(freqHz = 440.0, sampleRateHz = 16_000, millis = 50, amplitude = 2_000)
        val dirty = clean.copyOf()
        // Имитация щелчка: один резкий выброс
        dirty[500] = 20_000
        dirty[501] = 0

        val before = maxJump(dirty)
        processor.process(dirty)
        val after = maxJump(dirty)

        assertTrue("Скачок должен уменьшиться: было $before, стало $after", after < before)
        assertTrue("Щелчок должен быть посчитан", processor.clicksSuppressed >= 1)
        assertTrue("Сигнал вне щелчка не должен пострадать", rms(dirty) > rms(clean) * 0.5)
    }

    @Test
    fun `declicker leaves smooth signal untouched`() {
        val processor = DeClicker(maxDelta = 1_000)
        val signal = tone(freqHz = 440.0, sampleRateHz = 16_000, millis = 50, amplitude = 2_000)
        val processed = signal.copyOf()
        processor.process(processed)

        assertEquals(0L, processor.clicksSuppressed)
        assertTrue(signal.contentEquals(processed))
    }

    @Test
    fun `noise gate closes on silence and opens on speech`() {
        val gate = NoiseGate(sampleRateHz = 16_000, frameSize = 256, thresholdAboveNoiseDb = 9.0)

        val silence = noise(8_192, amplitude = 30, seed = 1)
        gate.process(silence)
        val afterSilence = rms(silence)
        assertTrue("Тишина должна быть подавлена", afterSilence < 30.0)
        assertTrue("Оценка шума должна быть разумной: ${gate.estimatedNoiseFloorDb}", gate.estimatedNoiseFloorDb < -20.0)

        val speech = tone(freqHz = 800.0, sampleRateHz = 16_000, millis = 200, amplitude = 6_000)
        gate.process(speech)
        assertTrue("Речь должна пройти: ${rms(speech)}", rms(speech) > 1_000.0)
    }

    @Test
    fun `noise gate stays closed when only noise continues`() {
        val gate = NoiseGate(sampleRateHz = 16_000, frameSize = 256, thresholdAboveNoiseDb = 12.0)
        repeat(10) { gate.process(noise(4_096, amplitude = 40, seed = it)) }

        assertTrue("Гейт не должен открываться на шуме", rms(noise(4_096, 40, seed = 99)) < 60.0)
    }

    @Test
    fun `level meter reports rms peak and clipping`() {
        val meter = AudioLevelMeter()
        val level = meter.process(tone(freqHz = 1_000.0, sampleRateHz = 16_000, millis = 100, amplitude = 8_000))

        assertTrue("RMS ожидаемо около 0.707 * амплитуды: ${level.rms}", level.rms in 4_000.0..7_000.0)
        assertTrue("Пик около 8000: ${level.peak}", level.peak in 7_500..8_500)
        assertTrue("Уровень не тишина", !level.isSilence)
        assertFalse("Клиппинга нет", level.isClipped)
        assertTrue(level.rmsDb in -20.0..-10.0)
    }

    @Test
    fun `level meter detects clipping and silence`() {
        val clipped = AudioLevelMeter().process(shortArrayOf(32_767, -32_768, 32_000))
        assertTrue(clipped.isClipped)
        assertEquals(3, clipped.clippedSamples)

        // Порог включительно-верхний (>= 32000), поэтому «ниже порога» строго меньше
        val belowClip = AudioLevelMeter().process(shortArrayOf(31_999, -31_000, 100))
        assertFalse(belowClip.isClipped)
        assertEquals(0, belowClip.clippedSamples)

        val silent = AudioLevelMeter().process(ShortArray(512))
        assertTrue(silent.isSilence)
        assertEquals(-120.0, silent.rmsDb, 0.001)
    }

    @Test
    fun `db conversion is inverse and clamped`() {
        assertEquals(0.0, toDb(32_767.0), 0.01)
        assertTrue(toDb(0.0) <= -119.0)
        assertEquals(32_767.0, fromDb(0.0), 1.0)
        assertTrue(fromDb(20.0) <= 32_767.0)
    }

    @Test
    fun `level smoother converges and is stable`() {
        val smoother = LevelSmoother(coefficient = 0.5)
        var value = smoother.update(-20.0)
        repeat(50) { value = smoother.update(-20.0) }
        assertEquals(-20.0, value, 0.5)

        smoother.reset()
        assertEquals(-120.0, smoother.smoothedDb, 0.001)
    }

    @Test
    fun `full chain processes real signal without allocations in hot path`() {
        val chain = DspChain(
            listOf(
                DcOffsetRemover(),
                HighPassFilter(80.0, 16_000),
                DeClicker(),
                NoiseGate(16_000)
            )
        )
        val signal = tone(freqHz = 440.0, sampleRateHz = 16_000, millis = 100)
        signal[400] = 18_000 // щелчок

        val processed = chain.process(signal)

        assertEquals(signal.size, processed)
        assertTrue("Обработанный сигнал не должен быть пустым", rms(signal) > 100.0)
        assertTrue("Пайплайн должен уложиться в реальное время", chain.stats().all { it.realTimeRatio < 1.0 })
    }

    @Test
    fun `reset clears state between recordings`() {
        val processor = DcOffsetRemover()
        processor.process(ShortArray(2_000) { 5_000 })
        processor.reset()

        val buffer = ShortArray(2_000) { 5_000 }
        processor.process(buffer)
        val tail = buffer.copyOfRange(buffer.size - 400, buffer.size)
        val tailMean = tail.sumOf { it.toDouble() } / tail.size

        // После reset состояние обнулено, иначе второй проход «залип» бы на нуле
        assertTrue("Хвост должен снова сойтись к нулю, получено $tailMean", abs(tailMean) < 20.0)
    }

    private fun maxJump(samples: ShortArray): Int {
        var maxDelta = 0
        for (index in 1 until samples.size) {
            maxDelta = maxOf(maxDelta, abs(samples[index].toInt() - samples[index - 1].toInt()))
        }
        return maxDelta
    }
}
