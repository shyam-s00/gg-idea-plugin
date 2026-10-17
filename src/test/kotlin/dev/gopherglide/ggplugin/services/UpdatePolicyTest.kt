package dev.gopherglide.ggplugin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatePolicyTest {
    private class State(
        override var enabled: Boolean = true,
        override var lastCheckMillis: Long = 0L,
        override var skippedVersion: String = ""
    ) : UpdateCheckState

    private val day = 24 * 60 * 60_000L
    private var now = 10 * day
    private var lookups = 0
    private var installedBinary: InstalledBinary? = InstalledBinary("/usr/local/bin/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.SYSTEM)
    private var release: GopherGlideVersion? = GopherGlideVersion(1, 3, 0)
    private val state = State()

    private fun policy() = UpdatePolicy(state, { installedBinary }, { lookups++; release }, { now })

    @Test
    fun anOlderBinaryGetsAnOffer() {
        assertEquals(UpdateOffer(GopherGlideVersion(1, 2, 0), GopherGlideVersion(1, 3, 0)), policy().check())
        assertEquals(now, state.lastCheckMillis)
    }

    @Test
    fun theToggleOffMakesNoNetworkCall() {
        state.enabled = false
        assertNull(policy().check())
        assertEquals(0, lookups)
    }

    @Test
    fun aCheckWithinADayMakesNoNetworkCall() {
        state.lastCheckMillis = now - day + 1
        assertNull(policy().check())
        assertEquals(0, lookups)
    }

    @Test
    fun aCheckAfterADayIsMadeAgain() {
        state.lastCheckMillis = now - day
        assertEquals(GopherGlideVersion(1, 3, 0), policy().check()?.latest)
    }

    @Test
    fun aClockSetBackwardsDoesNotBlockTheCheckForever() {
        state.lastCheckMillis = now + 5 * day
        assertEquals(GopherGlideVersion(1, 3, 0), policy().check()?.latest)
    }

    @Test
    fun offlineIsSilentAndDoesNotUseUpTheDay() {
        release = null
        assertNull(policy().check())
        assertEquals(0L, state.lastCheckMillis)
    }

    @Test
    fun aCurrentOrNewerBinaryGetsNoOffer() {
        release = GopherGlideVersion(1, 2, 0)
        assertNull(policy().check())
        release = GopherGlideVersion(1, 1, 0)
        assertNull(policy().check())
    }

    @Test
    fun aDevBuildOfTheLatestReleaseGetsNoOffer() {
        installedBinary = InstalledBinary("/p/gg", GopherGlideVersion(1, 3, 0, "dev"), InstalledBinary.Source.PLUGIN)
        assertNull(policy().check())
    }

    @Test
    fun aSkippedVersionIsNotOfferedButANewerOneIs() {
        state.skippedVersion = "v1.3.0"
        assertNull(policy().check())
        state.lastCheckMillis = 0L
        release = GopherGlideVersion(1, 3, 1)
        assertEquals(GopherGlideVersion(1, 3, 1), policy().check()?.latest)
    }

    @Test
    fun noBinaryOrUnreadableVersionMakesNoClaimAndNoNetworkCall() {
        installedBinary = null
        assertNull(policy().check())
        installedBinary = InstalledBinary("/p/gg", null, InstalledBinary.Source.SYSTEM)
        assertNull(policy().check())
        assertEquals(0, lookups)
    }

    @Test
    fun aCustomPathBinaryIsLeftAlone() {
        installedBinary = InstalledBinary("/opt/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.CUSTOM)
        assertNull(policy().check())
        assertEquals(0, lookups)
    }
}
