package com.voicehabit.tracker.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Встроенные ключи ИИ: чтение через рефлексию не должно падать —
 * ни когда полей нет, ни когда CI/local.properties их подставили.
 */
class BundledKeysTest {

    @Test
    fun `bundled keys read without crash and are stable`() {
        assertNotNull(BundledKeys.groq)
        assertNotNull(BundledKeys.gemini)
        assertEquals(BundledKeys.groq, BundledKeys.groq)
        assertEquals(BundledKeys.gemini, BundledKeys.gemini)
    }
}
