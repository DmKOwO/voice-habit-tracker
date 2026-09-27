package com.voicehabit.tracker.presentation.home

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class TaskToggleGateTest {

    @Test
    fun `second tap inside debounce window is dropped`() {
        val now = AtomicLong(1_000L)
        val gate = TaskToggleGate(clock = now::get, debounceMillis = 300L)

        assertTrue(gate.shouldProceed("t1"))
        now.set(1_100L)
        assertFalse("Тап через 100 мс — дребезг", gate.shouldProceed("t1"))
        now.set(1_301L)
        assertTrue("Тап через 301 мс — осознанный", gate.shouldProceed("t1"))
    }

    @Test
    fun `debounce is per task`() {
        val now = AtomicLong(1_000L)
        val gate = TaskToggleGate(clock = now::get, debounceMillis = 300L)

        assertTrue(gate.shouldProceed("t1"))
        // Другая задача тем же пальцем — не дребезг.
        assertTrue(gate.shouldProceed("t2"))
    }

    @Test
    fun `concurrent toggles of one task are serialized`() = runTest {
        val gate = TaskToggleGate(debounceMillis = 0L)
        val order = mutableListOf<String>()
        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()

        val first = async {
            gate.serialized("t1") {
                order += "first-start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                order += "first-end"
            }
        }
        firstStarted.await()
        val second = async {
            gate.serialized("t1") {
                order += "second"
            }
        }
        releaseFirst.complete(Unit)
        awaitAll(first, second)

        assertEquals(listOf("first-start", "first-end", "second"), order)
    }
}
