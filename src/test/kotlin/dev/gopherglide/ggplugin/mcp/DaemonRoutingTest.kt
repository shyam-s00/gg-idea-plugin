package dev.gopherglide.ggplugin.mcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel

class DaemonRoutingTest : BasePlatformTestCase() {
    fun testARunTheDaemonCannotExpressIsLeftForTheDirectPath() {
        var fellBack = false
        val taken = DaemonRouting.tryRun(project, listOf("--profile", "smoke", "--http-file", "/a.http", "--snap-dir", "/x"), GopherGlideRunPanel()) { fellBack = true }
        assertFalse(taken)
        assertFalse(fellBack)
        assertEquals(GgMcpDaemonService.State.STOPPED, GgMcpDaemonService.getInstance(project).state)
    }

    fun testAnIncompleteRunIsLeftForTheDirectPathToReport() {
        assertFalse(DaemonRouting.tryRun(project, emptyList(), GopherGlideRunPanel()) { })
    }

    fun testTheDaemonIsNotStartedUntilARunNeedsIt() {
        assertEquals(GgMcpDaemonService.State.STOPPED, GgMcpDaemonService.getInstance(project).state)
        assertNull(GgMcpDaemonService.getInstance(project).bridge)
    }
}
