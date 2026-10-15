package dev.gopherglide.ggplugin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GopherGlideVersionTest {
    private fun v(text: String) = GopherGlideVersion.parse(text)!!

    private fun resource(name: String) =
        checkNotNull(javaClass.getResourceAsStream("/lcp/$name")).bufferedReader().readText()

    @Test
    fun parsesTagsAndBareNumbers() {
        assertEquals(GopherGlideVersion(1, 3, 0), v("v1.3.0"))
        assertEquals(GopherGlideVersion(1, 3, 0), v("1.3.0"))
        assertEquals(GopherGlideVersion(10, 20, 30), v("v10.20.30"))
    }

    @Test
    fun parsesPreReleaseSuffixes() {
        assertEquals(GopherGlideVersion(1, 3, 0, "dev"), v("v1.3.0-dev"))
        assertEquals(GopherGlideVersion(1, 4, 0, "rc1"), v("v1.4.0-rc1"))
        assertEquals(GopherGlideVersion(1, 2, 9, "dev"), v("v1.2.9-dev"))
    }

    @Test
    fun parsesTheRealVersionOutputs() {
        assertEquals(GopherGlideVersion(1, 3, 0), v(resource("version-v130.txt")))
        assertEquals(GopherGlideVersion(1, 2, 0), v(resource("version-v120.txt")))
        assertEquals(GopherGlideVersion(1, 2, 9, "dev"), v(resource("version-dev.txt")))
    }

    @Test
    fun theLabelledLineWinsOverOtherNumbersInTheOutput() {
        assertEquals(GopherGlideVersion(1, 3, 0), v("gg (Gopher Glide) 9.9.9\n  Version:    1.3.0\n  Commit: 1.1.1"))
    }

    @Test
    fun anUnlabelledVersionInsideTextIsFound() {
        assertEquals(GopherGlideVersion(1, 3, 0), v("gg (Gopher Glide) 1.3.0 (commit:b16c0ab) built 2026-10-11T01:00:27Z"))
    }

    @Test
    fun garbageBlankAndNullGiveNull() {
        assertNull(GopherGlideVersion.parse(null))
        assertNull(GopherGlideVersion.parse(""))
        assertNull(GopherGlideVersion.parse("   "))
        assertNull(GopherGlideVersion.parse("ERROR: permission denied"))
        assertNull(GopherGlideVersion.parse("(empty output)"))
        assertNull(GopherGlideVersion.parse("1.3"))
        assertNull(GopherGlideVersion.parse("Version: unknown"))
    }

    @Test
    fun hugeNumbersDoNotThrow() {
        assertNull(GopherGlideVersion.parse("v99999999999.1.1"))
    }

    @Test
    fun ordersByNumberThenPreReleaseBeforeRelease() {
        assertTrue(v("1.2.0") < v("1.3.0"))
        assertTrue(v("1.3.0") < v("1.3.1"))
        assertTrue(v("1.9.0") < v("1.10.0"))
        assertTrue(v("1.3.0-dev") < v("1.3.0"))
        assertTrue(v("1.3.0-rc1") < v("1.3.0-rc2"))
        assertEquals(0, v("v1.3.0").compareTo(v("1.3.0")))
    }

    @Test
    fun aDevBuildOfTheMinimumCountsAsSupportingLiveControl() {
        assertTrue(v("v1.3.0-dev").supportsLiveControl)
        assertTrue(v("1.3.0").supportsLiveControl)
        assertTrue(v("1.4.0-rc1").supportsLiveControl)
        assertTrue(v("2.0.0").supportsLiveControl)
    }

    @Test
    fun olderBuildsDoNotSupportLiveControl() {
        assertFalse(v("1.2.0").supportsLiveControl)
        assertFalse(v("1.2.9-dev").supportsLiveControl)
        assertFalse(v("0.9.3").supportsLiveControl)
    }

    @Test
    fun coreComparisonIgnoresPreRelease() {
        assertTrue(v("1.3.0-dev").coreAtLeast(GopherGlideVersion(1, 3, 0)))
        assertFalse(v("1.2.9").coreAtLeast(GopherGlideVersion(1, 3, 0)))
    }

    @Test
    fun printsWithAVPrefix() {
        assertEquals("v1.3.0", v("1.3.0").toString())
        assertEquals("v1.3.0-dev", v("1.3.0-dev").toString())
    }
}
