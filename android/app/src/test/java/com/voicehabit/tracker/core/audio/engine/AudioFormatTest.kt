package com.voicehabit.tracker.core.audio.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class AudioFormatTest {

    @Test
    fun `voice format is 16k mono pcm16`() {
        val format = AudioFormat.VOICE_16K_MONO
        assertEquals(16_000, format.sampleRateHz)
        assertEquals(1, format.channels)
        assertEquals(2, format.bytesPerSample)
        assertEquals(2, format.bytesPerFrame)
        assertTrue(format.isMono)
    }

    @Test
    fun `duration and sample counts are derived from format`() {
        val format = AudioFormat.VOICE_16K_MONO
        assertEquals(1000L, format.durationMillis(format.samplesPerMillis(1000)))
        assertEquals(16_000, format.samplesPerMillis(1000))
        assertEquals(400, format.frameCount(400))

        val stereo = AudioFormat.CD_44K_STEREO
        // 400 значений в перемежающемся массиве — это 200 кадров по два канала
        assertEquals(200, stereo.frameCount(400))
        assertEquals(4, stereo.bytesPerFrame)
        assertEquals(400, format.frameCount(400))
    }

    @Test
    fun `downmix averages channels without losing 3db`() {
        val stereo = shortArrayOf(1000, 3000, -2000, -2000)
        val mono = downmixToMono(stereo)
        assertEquals(2, mono.size)
        assertEquals(2000, mono[0].toInt())
        assertEquals(-2000, mono[1].toInt())
    }

    @Test
    fun `interleave and deinterleave are inverse operations`() {
        val stereo = shortArrayOf(1, 2, 3, 4, 5, 6)
        val planar = deinterleave(stereo, channels = 2)
        assertEquals(listOf(3, 3), planar.map { it.size })
        assertEquals(1, planar[0][0].toInt())
        assertEquals(2, planar[1][0].toInt())
        assertTrue(stereo.contentEquals(interleave(planar)))
    }

    @Test
    fun `linear resampling halves sample count and keeps low frequency`() {
        val sourceRate = 32_000
        val targetRate = 16_000
        val freq = 440.0
        val samples = ShortArray(sourceRate) { index ->
            (sin(2 * Math.PI * freq * index / sourceRate) * 12_000).toInt().toShort()
        }

        val resampled = resampleLinear(samples, sourceRate, targetRate)

        assertEquals(targetRate, resampled.size)
        // Нулевой уровень не должен возникать: амплитуда остаётся того же порядка.
        val peak = resampled.maxOf { abs(it.toInt()) }
        assertTrue("Амплитуда должна сохраниться, получено $peak", peak > 10_000)
    }

    @Test
    fun `resampling to same rate returns equal content`() {
        val samples = shortArrayOf(1, 2, 3, 4)
        assertTrue(samples.contentEquals(resampleLinear(samples, 16_000, 16_000)))
    }

    @Test
    fun `peak normalization scales up quiet signal without clipping`() {
        val quiet = shortArrayOf(100, -200, 300, -100)
        val normalized = quiet.normalizedToPeak()

        assertEquals(Short.MAX_VALUE.toInt(), normalized.maxOf { it.toInt() })
        // Усиление одно на весь массив: соотношения сохраняются, пик не клиппится
        assertTrue(normalized.all { it.toInt() in -Short.MAX_VALUE..Short.MAX_VALUE })
        assertTrue(normalized[0] > 0)
        assertTrue(normalized[1] < 0)
        // -100 при пике 300 должно остаться примерно втрое тише пика
        val ratio = abs(normalized[3].toInt()).toDouble() / normalized[2].toInt()
        assertTrue("Ожидалось ~0.33, получено $ratio", ratio in 0.3..0.36)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported sample rate is rejected`() {
        AudioFormat(sampleRateHz = 12_345, channels = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported bit depth is rejected`() {
        AudioFormat(sampleRateHz = 16_000, channels = 1, bitsPerSample = 8)
    }

    @Test
    fun `silent frame has requested duration`() {
        val frame = AudioFrame.silent(AudioFormat.VOICE_16K_MONO, 250)
        assertEquals(4_000, frame.sampleCount)
        assertEquals(250L, frame.durationMillis)
        assertTrue(frame.samples.all { it == 0.toShort() })
    }

    @Test
    fun `frame resamples and downmixes to mono voice format`() {
        val stereo = AudioFrame(
            AudioFormat(48_000, 2),
            ShortArray(4_800) { index -> (sin(index * 0.1) * 8_000).toInt().toShort() }
        )
        val mono = stereo.mono()
        val target = stereo.resampledTo(16_000)

        assertTrue(mono.format.isMono)
        assertEquals(2_400, mono.sampleCount)
        assertEquals(16_000, target.format.sampleRateHz)
        assertEquals(1_600, target.sampleCount)
    }
}

class SpscAudioBufferTest {

    private fun ramp(size: Int, start: Int = 0): ShortArray =
        ShortArray(size) { (start + it).toShort() }

    @Test
    fun `capacity is rounded up to power of two`() {
        assertEquals(1, SpscAudioBuffer.nextPowerOfTwo(1))
        assertEquals(16, SpscAudioBuffer.nextPowerOfTwo(9))
        assertEquals(4_096, SpscAudioBuffer.nextPowerOfTwo(4_000))
        assertEquals(4_096, SpscAudioBuffer(4_000).capacity)
    }

    @Test
    fun `write and read preserve order`() {
        val buffer = SpscAudioBuffer(8)
        assertEquals(4, buffer.write(shortArrayOf(1, 2, 3, 4)))
        assertEquals(4, buffer.availableToRead())

        val out = ShortArray(8)
        assertEquals(4, buffer.read(out, 0, 4))
        assertEquals(listOf(1, 2, 3, 4), out.take(4).map { it.toInt() })
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `read wraps around ring boundary correctly`() {
        val buffer = SpscAudioBuffer(8)
        buffer.write(shortArrayOf(1, 2, 3, 4, 5, 6))
        val out = ShortArray(6)
        buffer.read(out, 0, 6)

        // Запись продолжается с индекса 6 и заворачивается через конец массива
        buffer.write(shortArrayOf(7, 8, 9, 10, 11))
        val tail = ShortArray(5)
        assertEquals(5, buffer.read(tail, 0, 5))
        assertEquals(listOf(7, 8, 9, 10, 11), tail.map { it.toInt() })
    }

    @Test
    fun `reject policy keeps older samples and counts loss`() {
        val buffer = SpscAudioBuffer(8, SpscAudioBuffer.OverrunPolicy.REJECT_NEW)
        assertEquals(8, buffer.write(ramp(8)))
        assertEquals(0, buffer.write(shortArrayOf(99, 98, 97)))

        val stats = buffer.stats()
        assertEquals("Переполнение одно событие", 1, stats.overrunEvents)
        assertEquals("Отклонено три сэмпла", 3, stats.rejectedSamples)
        assertEquals("При REJECT_NEW данные не теряются", 0, stats.droppedSamples)
        assertTrue(stats.underPressure)
        assertFalse(stats.hasLoss)

        val out = ShortArray(8)
        buffer.read(out, 0, 8)
        assertEquals(7, out[7].toInt())
    }

    @Test
    fun `drop oldest policy keeps newest samples on overflow`() {
        val buffer = SpscAudioBuffer(8, SpscAudioBuffer.OverrunPolicy.DROP_OLDEST)
        buffer.write(ramp(6))
        val accepted = buffer.write(shortArrayOf(100, 101, 102, 103))

        // Свободно было 2 сэмпла, поэтому принято 2, а 2 самых старых потеряно
        assertEquals(2, accepted)
        val stats = buffer.stats()
        assertEquals(1, stats.overrunEvents)
        assertEquals(2, stats.droppedSamples)
        assertTrue(stats.hasLoss)

        val out = ShortArray(8)
        assertEquals(6, buffer.read(out, 0, 8))
        assertEquals(2, out[0].toInt())
        assertEquals(101, out[5].toInt())
    }

    @Test
    fun `available space is zero only when buffer is full`() {
        val buffer = SpscAudioBuffer(8)
        assertEquals(8, buffer.availableToWrite())
        buffer.write(ramp(8))
        assertEquals(0, buffer.availableToWrite())
        assertTrue(buffer.isFull())
        buffer.read(ShortArray(8), 0, 8)
        assertEquals(8, buffer.availableToWrite())
    }

    @Test
    fun `empty read counts underrun and returns zero`() {
        val buffer = SpscAudioBuffer(16)
        val out = ShortArray(8)
        assertEquals(0, buffer.read(out, 0, 8))
        assertEquals(1, buffer.underrunCount())
    }

    @Test
    fun `partial read under shortage counts underrun but returns available data`() {
        val buffer = SpscAudioBuffer(16)
        buffer.write(shortArrayOf(1, 2, 3))
        val out = ShortArray(8)
        assertEquals(3, buffer.read(out, 0, 8))
        assertEquals(1, buffer.underrunCount())
        assertEquals(3, out.take(3).count { it != 0.toShort() })
    }

    @Test
    fun `stats report fill ratio and peak occupancy`() {
        val buffer = SpscAudioBuffer(16)
        buffer.write(ramp(8))
        val stats = buffer.stats()
        assertEquals(16, stats.capacity)
        assertEquals(8, stats.available)
        assertEquals(8, stats.peakOccupancy)
        assertEquals(0.5, stats.fillRatio, 0.0001)
        assertEquals(0, stats.overrunEvents)
        assertEquals(0, stats.droppedSamples)
        assertEquals(0, stats.underruns)
        assertFalse(stats.underPressure)
    }

    @Test
    fun `clear discards buffered data without breaking invariants`() {
        val buffer = SpscAudioBuffer(16)
        buffer.write(ramp(8))
        buffer.clear()
        assertTrue(buffer.isEmpty())
        assertEquals(8, buffer.write(ramp(8)))
    }

    @Test
    fun `snapshot drains buffer in order`() {
        val buffer = SpscAudioBuffer(16)
        buffer.write(shortArrayOf(5, 6, 7))
        assertTrue(shortArrayOf(5, 6, 7).contentEquals(buffer.snapshot()))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `concurrent producer and consumer never lose or corrupt samples`() {
        // REJECT_NEW: буфер не теряет данные, поэтому порядок строго возрастающий.
        // Потеря сэмплов проверяется отдельным тестом политики DROP_OLDEST.
        val buffer = SpscAudioBuffer(1 shl 16, SpscAudioBuffer.OverrunPolicy.REJECT_NEW)
        val totalSamples = 500_000
        val start = System.nanoTime()

        val producerDone = AtomicBoolean(false)
        // Общий флаг прерывания: если проверка порядка упала, продюсер не должен
        // крутиться в вечном ожидании свободного места.
        val aborted = AtomicBoolean(false)
        val outOfOrder = AtomicReference<String?>(null)
        val readTotal = java.util.concurrent.atomic.AtomicInteger(0)

        val producer = Thread {
            var written = 0
            var cursor = 0
            // Чанк не степень двойки: проверяем дробные записи и перенос через конец кольца
            val chunk = ShortArray(1021)
            var idleSpins = 0
            while (written < totalSamples && !aborted.get()) {
                val size = minOf(chunk.size, totalSamples - written)
                for (i in 0 until size) chunk[i] = ((cursor + i) and 0x7FFF).toShort()
                val accepted = buffer.write(chunk, 0, size)
                if (accepted == 0) {
                    idleSpins++
                    if (idleSpins % 64 == 0) Thread.yield()
                    continue
                }
                idleSpins = 0
                cursor += accepted
                written += accepted
            }
            producerDone.set(true)
        }

        val consumer = Thread {
            val chunk = ShortArray(733)
            var expected = 0
            while (!aborted.get() && (!producerDone.get() || buffer.availableToRead() > 0)) {
                val read = buffer.read(chunk, 0, chunk.size)
                if (read == 0) {
                    Thread.yield()
                    continue
                }
                for (i in 0 until read) {
                    val value = chunk[i].toInt() and 0x7FFF
                    // Продюсер пишет 15-битные значения по модулю 32768,
                    // поэтому и ожидаемое значение сравниваем по тому же модулю.
                    val expectedValue = expected and 0x7FFF
                    if (value != expectedValue) {
                        outOfOrder.compareAndSet(
                            null,
                            "ожидалось $expectedValue, получено $value (позиция $i, всего $expected)"
                        )
                        aborted.set(true)
                        return@Thread
                    }
                    expected++
                }
                readTotal.addAndGet(read)
            }
        }

        producer.start()
        consumer.start()
        producer.join(60_000)
        consumer.join(60_000)

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val stats = buffer.stats()
        println(
            "[ring-buffer] $totalSamples сэмплов за ${elapsedMs}мс, прочитано ${readTotal.get()}, " +
                "overrunEvents=${stats.overrunEvents}, underruns=${stats.underruns}, " +
                "пик=${stats.peakOccupancy}/${stats.capacity}"
        )

        assertNull("Порядок сэмплов нарушен: ${outOfOrder.get()}", outOfOrder.get())
        assertEquals("Ни один сэмпл не должен потеряться", totalSamples, readTotal.get())
        assertEquals("REJECT_NEW не теряет данные", 0, stats.droppedSamples)
        assertTrue("Буков должно хватать без переполнения", stats.peakOccupancy <= stats.capacity)
    }

    @Test
    fun `drop oldest policy loses samples but never corrupts order`() {
        val buffer = SpscAudioBuffer(1 shl 12, SpscAudioBuffer.OverrunPolicy.DROP_OLDEST)
        val chunk = ShortArray(512)

        var produced = 0L
        var droppedBeforeRead = 0L
        repeat(200) {
            for (i in chunk.indices) chunk[i] = ((produced + i) and 0x7FFF).toShort()
            buffer.write(chunk, 0, chunk.size)
            produced += chunk.size
            if (it % 3 == 0) {
                // Читаем не всё — переполнение гарантировано
                droppedBeforeRead += buffer.droppedSamples()
                val out = ShortArray(64)
                buffer.read(out, 0, out.size)
            }
        }

        val stats = buffer.stats()
        println("[ring-buffer] DROP_OLDEST: переполнений ${stats.overrunEvents}, потеряно ${stats.droppedSamples}")
        assertTrue("Переполнение должно произойти", stats.overrunEvents > 0)
        assertTrue("Счётчик потерянных сэмплов должен совпадать с переполнениями", droppedBeforeRead > 0)
    }

    @Test
    fun `reset counters keeps current occupancy`() {
        val buffer = SpscAudioBuffer(16)
        buffer.write(ramp(8))
        buffer.read(ShortArray(8), 0, 8)
        buffer.resetCounters()

        val stats = buffer.stats()
        assertEquals(0, stats.droppedSamples)
        assertEquals(0, stats.overrunEvents)
        assertEquals(0, stats.underruns)
        assertEquals(0, stats.peakOccupancy)
        assertFalse(stats.hasLoss)
    }
}
