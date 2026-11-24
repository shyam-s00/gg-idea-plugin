package dev.gopherglide.ggplugin.services

import dev.gopherglide.ggplugin.services.BinaryFeatureGate.Decision
import dev.gopherglide.ggplugin.services.InstalledBinary.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryFeatureGateTest {
    private val v14 = GopherGlideVersion(1, 4, 0)
    private val v15 = GopherGlideVersion(1, 5, 0)

    private fun binary(source: Source, version: GopherGlideVersion? = v14) = InstalledBinary("/opt/gg", version, source)

    @Test
    fun aCurrentBinaryProceeds() {
        assertEquals(Decision.Proceed, BinaryFeatureGate.decide(binary(Source.SYSTEM, v15), v15))
        assertEquals(Decision.Proceed, BinaryFeatureGate.decide(binary(Source.PLUGIN, GopherGlideVersion(1, 6, 1)), v15))
    }

    @Test
    fun anUnknownBinaryOrVersionProceedsAndLetsGgSpeakForItself() {
        assertEquals(Decision.Proceed, BinaryFeatureGate.decide(null, v15))
        assertEquals(Decision.Proceed, BinaryFeatureGate.decide(binary(Source.SYSTEM, null), v15))
    }

    @Test
    fun anOlderPluginOrSystemBinaryIsOfferedTheLatestRelease() {
        listOf(Source.PLUGIN, Source.SYSTEM).forEach { source ->
            val decision = BinaryFeatureGate.decide(binary(source), v15)
            assertTrue(decision is Decision.Upgrade)
            assertEquals(v15, (decision as Decision.Upgrade).latest)
        }
    }

    @Test
    fun aCustomBinaryIsNeverReplaced() {
        assertTrue(BinaryFeatureGate.decide(binary(Source.CUSTOM), v15) is Decision.UpdateCustom)
    }

    @Test
    fun withoutAReleaseThatHasTheAutopilotThereIsNothingToOffer() {
        assertTrue(BinaryFeatureGate.decide(binary(Source.SYSTEM), v14) is Decision.NoRelease)
        assertTrue(BinaryFeatureGate.decide(binary(Source.SYSTEM), null) is Decision.NoRelease)
    }

    @Test
    fun aPreReleaseOfV15CountsAsV15() {
        assertEquals(Decision.Proceed, BinaryFeatureGate.decide(binary(Source.SYSTEM, GopherGlideVersion(1, 5, 0, "dev")), v15))
    }
}
