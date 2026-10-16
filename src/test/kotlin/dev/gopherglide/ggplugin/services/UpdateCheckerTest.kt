package dev.gopherglide.ggplugin.services

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckerTest {
    private val state = object : UpdateCheckState {
        override var enabled = true
        override var lastCheckMillis = 0L
        override var skippedVersion = ""
    }

    @Test
    fun severalProjectsOpeningAtStartCauseOneCheckAndOneOffer() {
        var lookups = 0
        val offers = mutableListOf<UpdateOffer>()
        val checker = UpdateChecker()
        repeat(3) {
            checker.checkOnce(
                policy = {
                    UpdatePolicy(
                        state,
                        { InstalledBinary("/bin/gg", GopherGlideVersion(1, 2, 0), InstalledBinary.Source.SYSTEM) },
                        { lookups++; GopherGlideVersion(1, 3, 0) }
                    )
                },
                background = { it() },
                onOffer = { offers += it }
            )
        }
        assertEquals(1, lookups)
        assertEquals(1, offers.size)
    }
}
