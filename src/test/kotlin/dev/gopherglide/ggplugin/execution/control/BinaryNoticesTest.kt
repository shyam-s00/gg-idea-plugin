package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import dev.gopherglide.ggplugin.services.InstalledBinary.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryNoticesTest {
    private val v12 = GopherGlideVersion(1, 2, 0)
    private val v13 = GopherGlideVersion(1, 3, 0)

    private fun binary(source: Source, version: GopherGlideVersion? = v12) = InstalledBinary("/opt/tools/gg", version, source)

    private fun forRun(
        installed: InstalledBinary?,
        latest: GopherGlideVersion? = v13,
        runActive: Boolean = false,
        progress: InstallProgress = InstallProgress.IDLE
    ) = BinaryNotices.forRun(installed, latest, runActive, progress)

    private fun idle(
        installed: InstalledBinary?,
        latest: GopherGlideVersion? = v13,
        progress: InstallProgress = InstallProgress.IDLE
    ) = BinaryNotices.idle(installed, latest, progress)

    @Test
    fun beforeTheBinaryIsKnownTheRunNoticeIsGenericWithoutAButton() {
        val notice = forRun(null)
        assertEquals("Live Control needs gg v1.3.0+.", notice.text)
        assertEquals(NoticeButton.NONE, notice.button)
    }

    @Test
    fun aPluginManagedBinaryCanBeUpgradedOnceTheRunHasEnded() {
        val notice = forRun(binary(Source.PLUGIN))
        assertEquals("Live Control needs gg v1.3.0+ (installed: v1.2.0).", notice.text)
        assertEquals(NoticeButton.UPGRADE, notice.button)
        assertTrue(notice.buttonEnabled)
        assertNull(notice.hint)
    }

    @Test
    fun theUpgradeWaitsWhileARunIsActive() {
        val notice = forRun(binary(Source.PLUGIN), runActive = true)
        assertEquals(NoticeButton.UPGRADE, notice.button)
        assertFalse(notice.buttonEnabled)
        assertEquals("Upgrade is available when the run ends", notice.hint)
    }

    @Test
    fun aCustomPathBinaryGetsItsPathAndAnOpenSettingsButton() {
        val notice = forRun(binary(Source.CUSTOM))
        assertEquals(NoticeButton.OPEN_SETTINGS, notice.button)
        assertTrue(notice.text.contains("Installed: v1.2.0 at /opt/tools/gg"))
        assertTrue(notice.text.contains("clear the custom path in Settings"))
        assertFalse(notice.settingsLink)
    }

    @Test
    fun aSystemBinaryOffersAnInstallThatLeavesItUntouched() {
        val notice = forRun(binary(Source.SYSTEM))
        assertEquals(NoticeButton.INSTALL, notice.button)
        assertEquals("Install gg v1.3.0", notice.buttonLabel)
        assertTrue(notice.buttonEnabled)
        assertTrue(notice.text.contains("Installed: v1.2.0 at /opt/tools/gg."))
        assertTrue(notice.hint!!.contains("not changed"))
        assertTrue(notice.settingsLink)
    }

    @Test
    fun theInstallIsAllowedWhileARunIsActive() {
        val notice = forRun(binary(Source.SYSTEM), runActive = true)
        assertEquals(NoticeButton.INSTALL, notice.button)
        assertTrue(notice.buttonEnabled)
    }

    @Test
    fun noDownloadIsOfferedWhenTheLatestReleaseIsStillTooOld() {
        listOf(Source.PLUGIN, Source.SYSTEM).forEach {
            val notice = forRun(binary(it), latest = GopherGlideVersion(1, 2, 5))
            assertEquals(NoticeButton.NONE, notice.button)
            assertTrue(notice.text.endsWith("No release with Live Control is available yet."))
        }
    }

    @Test
    fun noDownloadIsOfferedWhenTheLatestReleaseCouldNotBeChecked() {
        listOf(Source.PLUGIN, Source.SYSTEM).forEach {
            val notice = forRun(binary(it), latest = null)
            assertEquals(NoticeButton.NONE, notice.button)
            assertTrue(notice.text.endsWith("Could not check for a newer release."))
        }
    }

    @Test
    fun aPreReleaseOfTheMinimumReleaseCountsAsEligible() {
        val notice = forRun(binary(Source.PLUGIN), latest = GopherGlideVersion(1, 3, 0, "rc1"))
        assertEquals(NoticeButton.UPGRADE, notice.button)
    }

    @Test
    fun anUnreadableInstalledVersionIsNamedUnknown() {
        val notice = forRun(binary(Source.PLUGIN, version = null))
        assertTrue(notice.text.contains("(installed: unknown version)"))
    }

    @Test
    fun whileDownloadingTheButtonIsDisabledAndShowsProgress() {
        val upgrade = forRun(binary(Source.PLUGIN), progress = InstallProgress.DOWNLOADING)
        assertEquals(NoticeButton.UPGRADE, upgrade.button)
        assertFalse(upgrade.buttonEnabled)
        assertTrue(upgrade.text.endsWith("Upgrading…"))
        val install = forRun(binary(Source.SYSTEM), progress = InstallProgress.DOWNLOADING)
        assertEquals(NoticeButton.INSTALL, install.button)
        assertTrue(install.text.endsWith("Installing…"))
    }

    @Test
    fun afterTheDownloadTheNoticeSaysTheNextRunWillHaveControl() {
        listOf(Source.PLUGIN, Source.SYSTEM).forEach {
            val notice = forRun(binary(it), progress = InstallProgress.DONE)
            assertEquals("gg v1.3.0 is installed. Live Control will be available from the next run.", notice.text)
            assertEquals(NoticeButton.NONE, notice.button)
        }
    }

    @Test
    fun idleMakesNoClaimWithoutAKnownVersion() {
        assertNull(idle(null))
        assertNull(idle(binary(Source.SYSTEM, version = null)))
    }

    @Test
    fun idleWithACurrentBinaryIsAQuietLine() {
        val notice = idle(binary(Source.SYSTEM, version = GopherGlideVersion(1, 3, 1)))
        assertNotNull(notice)
        assertTrue(notice!!.quiet)
        assertEquals("Live Control available (gg v1.3.1)", notice.text)
        assertEquals(NoticeButton.NONE, notice.button)
    }

    @Test
    fun idleWithAnOlderBinaryShowsTheSameNoticeAsARunWould() {
        assertEquals(forRun(binary(Source.SYSTEM)), idle(binary(Source.SYSTEM)))
        assertEquals(forRun(binary(Source.CUSTOM)), idle(binary(Source.CUSTOM)))
        assertFalse(idle(binary(Source.PLUGIN))!!.quiet)
    }
}
