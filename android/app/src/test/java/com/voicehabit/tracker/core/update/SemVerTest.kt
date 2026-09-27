package com.voicehabit.tracker.core.update.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parsesPlainVersions() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("  v1.2.3  "))
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
        assertEquals(SemVer(1, 0, 0), SemVer.parse("1"))
    }

    @Test
    fun parsesPrereleaseAndIgnoresBuildMetadata() {
        assertEquals(SemVer(1, 0, 0, "beta"), SemVer.parse("v1.0.0-beta"))
        assertEquals(SemVer(1, 0, 0, "beta.2"), SemVer.parse("1.0.0-beta.2"))
        // build-metadata не влияет на равенство версий
        assertEquals(SemVer.parse("1.2.3"), SemVer.parse("1.2.3+build.42"))
    }

    @Test
    fun rejectsGarbageInsteadOfCrashing() {
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
        assertNull(SemVer.parse("   "))
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("v1.2.3.4.5"))
        assertNull(SemVer.parse("version one"))
        assertNull(SemVer.parse("1.2.x"))
    }

    @Test
    fun ordersReleasesCorrectly() {
        assertTrue(SemVer.parse("1.2.4")!! > SemVer.parse("1.2.3")!!)
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
        assertTrue(SemVer.parse("2.0.0")!! > SemVer.parse("1.99.99")!!)
        assertEquals(0, SemVer.parse("1.2.3")!!.compareTo(SemVer.parse("v1.2.3")!!))
    }

    @Test
    fun releaseIsNewerThanPrerelease() {
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("1.0.0-rc.1")!!)
        assertTrue(SemVer.parse("1.0.0-beta")!! > SemVer.parse("1.0.0-alpha")!!)
        assertTrue(SemVer.parse("1.0.0-beta.2")!! > SemVer.parse("1.0.0-beta.1")!!)
    }
}
