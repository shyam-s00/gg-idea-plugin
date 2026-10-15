package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import dev.gopherglide.ggplugin.services.InstalledBinary.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyNoticesTest {
    private val v12 = GopherGlideVersion(1, 2, 0)
    private val v13 = GopherGlideVersion(1, 3, 0)

    private fun binary(source: Source, version: GopherGlideVersion? = v12) = InstalledBinary("/opt/tools/gg", version, source)

    private fun build(
        installed: InstalledBinary?,
        latest: GopherGlideVersion? = v13,
        runActive: Boolean = false,
        progress: UpgradeProgress = UpgradeProgress.IDLE
    ) = LegacyNotices.build(installed, latest, runActive, progress)

    @Test
    fun beforeTheBinaryIsKnownTheNoticeIsGenericWithoutAButton() {
        val notice = build(null)
        assertEquals("Live Control needs gg v1.3.0+.", notice.text)
        assertEquals(UpgradeButton.HIDDEN, notice.button)
    }

    @Test
    fun aPluginManagedBinaryCanBeUpgradedOnceTheRunHasEnded() {
        val notice = build(binary(Source.PLUGIN))
        assertEquals("Live Control needs gg v1.3.0+ (installed: v1.2.0).", notice.text)
        assertEquals(UpgradeButton.AVAILABLE, notice.button)
        assertNull(notice.buttonHint)
    }

    @Test
    fun theUpgradeWaitsWhileARunIsActive() {
        val notice = build(binary(Source.PLUGIN), runActive = true)
        assertEquals(UpgradeButton.WAIT_FOR_RUN_END, notice.button)
        assertEquals("Upgrade is available when the run ends", notice.buttonHint)
    }

    @Test
    fun aCustomPathBinaryGetsTextWithItsPathAndNoButton() {
        val notice = build(binary(Source.CUSTOM), runActive = false)
        assertEquals(UpgradeButton.HIDDEN, notice.button)
        assertTrue(notice.text.contains("Installed: v1.2.0 at /opt/tools/gg"))
        assertTrue(notice.text.contains("clear the custom path in Settings > Tools > Gopher Glide"))
    }

    @Test
    fun aSystemBinaryGetsTextWithItsPathAndNoButton() {
        val notice = build(binary(Source.SYSTEM))
        assertEquals(UpgradeButton.HIDDEN, notice.button)
        assertTrue(notice.text.contains("at /opt/tools/gg. Update that binary."))
    }

    @Test
    fun noUpgradeIsOfferedWhenTheLatestReleaseIsStillTooOld() {
        val notice = build(binary(Source.PLUGIN), latest = GopherGlideVersion(1, 2, 5))
        assertEquals(UpgradeButton.HIDDEN, notice.button)
        assertTrue(notice.text.endsWith("No release with Live Control is available yet."))
    }

    @Test
    fun noUpgradeIsOfferedWhenTheLatestReleaseCouldNotBeChecked() {
        val notice = build(binary(Source.PLUGIN), latest = null)
        assertEquals(UpgradeButton.HIDDEN, notice.button)
        assertTrue(notice.text.endsWith("Could not check for a newer release."))
    }

    @Test
    fun aDevBuildOfTheMinimumReleaseCountsAsEligible() {
        val notice = build(binary(Source.PLUGIN), latest = GopherGlideVersion(1, 3, 0, "rc1"))
        assertEquals(UpgradeButton.AVAILABLE, notice.button)
    }

    @Test
    fun anUnreadableInstalledVersionIsNamedUnknown() {
        val notice = build(binary(Source.PLUGIN, version = null))
        assertTrue(notice.text.contains("(installed: unknown version)"))
    }

    @Test
    fun whileDownloadingTheButtonShowsProgress() {
        val notice = build(binary(Source.PLUGIN), progress = UpgradeProgress.DOWNLOADING)
        assertEquals(UpgradeButton.RUNNING, notice.button)
        assertTrue(notice.text.endsWith("Upgrading…"))
    }

    @Test
    fun afterTheDownloadTheNoticeSaysTheNextRunWillHaveControl() {
        val notice = build(binary(Source.PLUGIN), progress = UpgradeProgress.DONE)
        assertEquals("gg v1.3.0 is installed. Live Control will be available from the next run.", notice.text)
        assertEquals(UpgradeButton.HIDDEN, notice.button)
    }
}
