package dev.gopherglide.ggplugin.execution

import com.google.gson.JsonParser
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.execution.ui.LiveControlPanel
import java.io.File
import javax.swing.JButton

/** Drives real sessions against real binaries; skipped unless GG_V13, GG_V12 and SPIKE_DIR are set (GG_V15 for the v1.5 test). */
class GopherGlideRunSessionLiveTest : BasePlatformTestCase() {
    private val v13 get() = System.getenv("GG_V13")
    private val v12 get() = System.getenv("GG_V12")
    private val work get() = System.getenv("SPIKE_DIR")

    private fun enabled() = v13 != null && v12 != null && work != null

    private fun waitUntil(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $what; labels=${labelsOf(lastPanel)}" }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            Thread.sleep(25)
        }
    }

    private lateinit var lastPanel: GopherGlideRunPanel

    private fun labelsOf(panel: GopherGlideRunPanel): List<String> =
        UIUtil.findComponentsOfType(panel, JBLabel::class.java).map { it.text.orEmpty() }

    private fun status(panel: GopherGlideRunPanel): String =
        labelsOf(panel).firstOrNull { it.startsWith("●") || it.startsWith("■") || it.startsWith("✓") || it.startsWith("✗") || it == "Starting…" } ?: ""

    private fun snapDir(name: String) = File(work, "live-$name").also { it.deleteRecursively(); it.mkdirs() }

    private fun snaps(dir: File) = dir.listFiles { f -> f.extension == "snap" }?.toList().orEmpty()

    private fun args(dir: File, duration: String = "40s", vararg extra: String) =
        listOf("--profile", "smoke", "-d", duration, "-r", "10", "--http-file", "$work/test.http", "--snap", "--snap-dir", dir.path) + extra

    private fun startRunning(binary: String, args: List<String>): Pair<GopherGlideRunSession, GopherGlideRunPanel> {
        val panel = GopherGlideRunPanel()
        lastPanel = panel
        val session = GopherGlideRunSession(project, binary, args, panel)
        session.start()
        waitUntil("started") { status(panel).startsWith("● RUNNING") }
        return session to panel
    }

    fun testCleanStopOnV13WritesTheSnapshot() {
        if (!enabled()) return
        val dir = snapDir("clean")
        val (session, panel) = startRunning(v13!!, args(dir))
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
        assertTrue(status(panel), status(panel).startsWith("■ STOPPED — ✓ Snapshot saved"))
        assertEquals(1, snaps(dir).size)
    }

    private fun click(panel: GopherGlideRunPanel, text: String) =
        UIUtil.findComponentsOfType(panel, JButton::class.java).first { it.text == text }.doClick()

    fun testStripClicksReachGgInOrderAndAreRecordedInTheSnapshot() {
        if (!enabled()) return
        val dir = snapDir("bias")
        val (session, panel) = startRunning(v13!!, args(dir))
        waitUntil("strip enabled") { labelsOf(panel).contains("Bias 0 RPS") }
        click(panel, "+10")
        click(panel, "+10")
        click(panel, "−5")
        assertTrue(labelsOf(panel).contains("Bias +15 RPS"))
        click(panel, "Reset")
        waitUntil("badge back to zero") { labelsOf(panel).contains("Bias 0 RPS") }
        Thread.sleep(500)
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }

        val meta = JsonParser.parseString(snaps(dir).single().readText()).asJsonObject.getAsJsonObject("meta")
        val events = meta.getAsJsonArray("bias_events").map { it.asJsonObject }
        assertEquals(listOf(10, 10, -5, -15), events.map { it["amount"].asInt })
        assertEquals(listOf(10, 20, 15, 0), events.map { it["cumulative"].asInt })
        assertEquals(0, meta["final_bias"].asInt)
    }

    fun testMarksReachGgWithTheirLabelsAndGgsTime() {
        if (!enabled()) return
        val dir = snapDir("marks")
        val (session, panel) = startRunning(v13!!, args(dir))
        val tricky = "say \"hi\" ✓ 日本 <&> back\\slash"
        val replies = mutableListOf<ControlReply>()
        session.sendMark("cache-warm", null) { replies += it }
        waitUntil("first reply") { replies.size == 1 }
        Thread.sleep(1_200)
        session.sendMark(tricky, null) { replies += it }
        waitUntil("second reply") { replies.size == 2 }
        val recorded = replies.map { it as ControlReply.MarkRecorded }.map { it.payload }
        assertEquals(listOf("cache-warm", tricky), recorded.map { it.label })
        assertTrue(recorded[1].elapsedSeconds!! > recorded[0].elapsedSeconds!!)
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }

        val marks = JsonParser.parseString(snaps(dir).single().readText()).asJsonObject
            .getAsJsonObject("meta").getAsJsonArray("marks").map { it.asJsonObject }
        assertEquals(listOf("cache-warm", tricky), marks.map { it["label"].asString })
        assertEquals(recorded[1].elapsedSeconds!!, marks[1]["elapsed_s"].asDouble, 0.001)
    }

    fun testMarkKeepsTheTimeItWasAskedForOnV15() {
        val v15 = System.getenv("GG_V15")
        if (v15 == null || work == null) return
        val dir = snapDir("mark-elapsed")
        val (session, panel) = startRunning(v15, args(dir))
        Thread.sleep(3_000)
        val replies = mutableListOf<ControlReply>()
        session.sendMark("early", 1.0) { replies += it }
        session.sendMark("future", 900.0) { replies += it }
        waitUntil("both replies") { replies.size == 2 }
        val (early, future) = replies.map { (it as ControlReply.MarkRecorded).payload }
        assertEquals(1.0, early.elapsedSeconds!!, 0.001)
        assertTrue(future.clamped == true)
        assertTrue(future.elapsedSeconds!! < 900.0)
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }

        val marks = JsonParser.parseString(snaps(dir).single().readText()).asJsonObject
            .getAsJsonObject("meta").getAsJsonArray("marks").map { it.asJsonObject }
        assertEquals(1.0, marks.first { it["label"].asString == "early" }["elapsed_s"].asDouble, 0.001)
    }

    private fun v15Binary(): String? = System.getenv("GG_V15")?.takeIf { work != null }

    fun testHoldShowsInTheStripAndReleasesOnV15() {
        val v15 = v15Binary() ?: return
        val (session, panel) = startRunning(v15, args(snapDir("hold")))
        val replies = mutableListOf<ControlReply>()
        session.sendHold(15) { replies += it }
        waitUntil("hold ack") { replies.size == 1 }
        assertEquals(15, (replies[0] as ControlReply.Ack).payload.holdRps)
        waitUntil("hold badge") { labelsOf(panel).contains("📌 Hold 15 RPS") }
        assertTrue(UIUtil.findComponentsOfType(panel, JButton::class.java).any { it.text == "Release Hold" })
        session.sendRelease { replies += it }
        waitUntil("release ack") { replies.size == 2 }
        waitUntil("badge cleared") { !labelsOf(panel).contains("📌 Hold 15 RPS") }
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
    }

    fun testGuardLocksTheStepsAndStopHandsTheRateBackOnV15() {
        val v15 = v15Binary() ?: return
        val (session, panel) = startRunning(v15, args(snapDir("guard")))
        val replies = mutableListOf<ControlReply>()
        session.startAutopilot("slo-guard", 250, 0.01) { replies += it }
        waitUntil("guard ack") { replies.size == 1 }
        waitUntil("pill") { labelsOf(panel).any { it.startsWith("⚡ Autopilot: SLO Guard") } }
        val plus = UIUtil.findComponentsOfType(panel, JButton::class.java).first { it.text == "+5" }
        assertFalse(plus.isEnabled)
        session.sendBias(5) { replies += it }
        waitUntil("bias refused") { replies.size == 2 }
        assertEquals("autopilot_active", (replies[1] as ControlReply.Rejected).payload.reason)
        session.stopAutopilot { replies += it }
        waitUntil("stop ack") { replies.size == 3 }
        waitUntil("steps back") { plus.isEnabled }
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
    }

    fun testFindCeilingRunEndsWithItsOutcomeAndTheAutopilotsMarksOnV15() {
        val v15 = v15Binary() ?: return
        val dir = snapDir("ceiling")
        val flags = arrayOf("--autopilot", "find-ceiling", "--start-rps", "10", "--max-rps", "25", "--step-interval", "2s", "--duration", "40s")
        val panel = GopherGlideRunPanel()
        lastPanel = panel
        val session = GopherGlideRunSession(
            project, v15, listOf("--http-file", "$work/test.http", "--snap", "--snap-dir", dir.path) + flags, panel
        )
        session.start()
        waitUntil("started") { status(panel).startsWith("● RUNNING") }
        waitUntil("pill") { labelsOf(panel).any { it.startsWith("⚡ Autopilot: Find Ceiling") } }
        waitUntil("run to end", timeoutMs = 60_000) { !panel.isRunning() }
        assertTrue(status(panel), status(panel).startsWith("✓ FINISHED — ⚡ No ceiling found up to 25 RPS cap"))
        assertTrue(labelsOf(panel).any { it.startsWith("Last mark: “⚡ ") })
        assertEquals(1, snaps(dir).size)
    }

    private fun effectivelyVisible(panel: GopherGlideRunPanel, c: java.awt.Component): Boolean =
        generateSequence(c) { it.parent }.takeWhile { it !== panel.parent }.all { it.isVisible }

    fun testOlderBinaryShowsTheNoticeWithItsRealVersionAndPath() {
        if (!enabled()) return
        val (session, panel) = startRunning(v12!!, args(snapDir("legacy-notice")))
        waitUntil("notice with the installed version") { labelsOf(panel).any { it.contains("Installed: v1.2.0 at") } }
        val plus = UIUtil.findComponentsOfType(panel, JButton::class.java).first { it.text == "+10" }
        assertFalse(effectivelyVisible(panel, plus))
        assertTrue(labelsOf(panel).any { it.contains(v12!!) })
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
    }

    fun testInstalledBinaryReadsTheRealVersionAndSource() {
        if (!enabled()) return
        val manager = dev.gopherglide.ggplugin.services.BinaryManager.instance
        val settings = dev.gopherglide.ggplugin.settings.GopherGlideSettings.instance
        val saved = settings.customBinaryPath
        try {
            settings.customBinaryPath = ""
            val v13Info = manager.installedBinary(v13!!)
            assertEquals(dev.gopherglide.ggplugin.services.GopherGlideVersion(1, 3, 0), v13Info.version)
            assertEquals(dev.gopherglide.ggplugin.services.InstalledBinary.Source.SYSTEM, v13Info.source)
            assertEquals(dev.gopherglide.ggplugin.services.GopherGlideVersion(1, 2, 0), manager.installedBinary(v12!!).version)

            settings.customBinaryPath = v12!!
            assertEquals(dev.gopherglide.ggplugin.services.InstalledBinary.Source.CUSTOM, manager.installedBinary(v12!!).source)
            assertNull(manager.installedBinary("$work/does-not-exist").version)
        } finally {
            settings.customBinaryPath = saved
        }
    }

    fun testStripIsOffWithControlDisabled() {
        if (!enabled()) return

        val (noneSession, nonePanel) = startRunning(v13!!, args(snapDir("none-strip"), "40s", "--control", "none"))
        assertTrue(isStripVisible(nonePanel))
        assertTrue(labelsOf(nonePanel).contains("Live control is off for this run"))
        assertFalse(UIUtil.findComponentsOfType(nonePanel, JButton::class.java).first { it.text == "+10" }.isEnabled)
        noneSession.requestStop()
        waitUntil("none end") { !nonePanel.isRunning() }
    }

    private fun isStripVisible(panel: GopherGlideRunPanel): Boolean =
        UIUtil.findComponentsOfType(panel, LiveControlPanel::class.java).single().isVisible

    fun testStopOnV12SoftKillsAndStillWritesTheSnapshot() {
        if (!enabled()) return
        val dir = snapDir("legacy")
        val (session, panel) = startRunning(v12!!, args(dir))
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
        assertTrue(status(panel), status(panel).startsWith("■ STOPPED"))
        assertEquals(1, snaps(dir).size)
    }

    fun testStopWithControlDisabledSoftKillsAndWritesTheSnapshot() {
        if (!enabled()) return
        val dir = snapDir("none")
        val (session, panel) = startRunning(v13!!, args(dir, "40s", "--control", "none"))
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
        assertTrue(status(panel), status(panel).startsWith("■ STOPPED"))
        assertEquals(1, snaps(dir).size)
    }

    fun testNewRunReplacingAnOldOneIsNotMarkedAsExited() {
        if (!enabled()) return
        val (first, panel) = startRunning(v13!!, args(snapDir("replace-a")))
        first.detach()
        val second = GopherGlideRunSession(project, v13!!, args(snapDir("replace-b"), "4s"), panel)
        second.start()
        waitUntil("second run to finish") { status(panel).startsWith("✓ FINISHED") }
        assertTrue(status(panel), status(panel).startsWith("✓ FINISHED"))
    }

    private fun stubBinary(name: String, trap: String): String {
        val script = File(work, name)
        script.writeText(
            "#!/bin/sh\n$trap\n" +
                "echo '{\"event\":\"started\",\"total_stages\":1,\"capabilities\":[\"control.stop\"],\"message\":\"stub started\"}'\n" +
                "while true; do sleep 0.1; done\n"
        )
        script.setExecutable(true)
        return script.path
    }

    fun testSilentProcessIsInterruptedAfterTheAckTimeout() {
        if (!enabled()) return
        val (session, panel) = startRunning(stubBinary("stub-silent.sh", "trap 'exit 0' INT TERM"), emptyList())
        val t0 = System.currentTimeMillis()
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
        val took = System.currentTimeMillis() - t0
        assertTrue("ended after $took ms", took in 2_000..8_000)
    }

    fun testForceStopKillsAProcessThatIgnoresTheInterrupt() {
        if (!enabled()) return
        val (session, panel) = startRunning(stubBinary("stub-stubborn.sh", "trap '' INT TERM"), emptyList())
        session.requestStop()
        waitUntil("stopping status") { panel.isStopping() }
        Thread.sleep(3_500)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue("still stopping", panel.isRunning() && panel.isStopping())
        session.requestStop()
        waitUntil("run to end") { !panel.isRunning() }
        assertTrue(status(panel), status(panel).startsWith("✗ EXITED (code 137)"))
    }
}
