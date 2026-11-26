package dev.gopherglide.ggplugin.ai

import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.ui.GopherGlideRunPanel
import dev.gopherglide.ggplugin.mcp.DaemonInfo
import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService
import dev.gopherglide.ggplugin.mcp.DaemonRunSession
import dev.gopherglide.ggplugin.mcp.IdeBridgeHost
import dev.gopherglide.ggplugin.mcp.PluginBridge
import dev.gopherglide.ggplugin.mcp.PluginHttp
import dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings
import java.util.concurrent.Executor
import com.intellij.openapi.actionSystem.CommonDataKeys

class AiProjectStateTest : BasePlatformTestCase() {
    private val settings get() = GopherGlideProjectSettings.getInstance(project)

    override fun tearDown() {
        try {
            settings.aiLinked = false
            settings.aiOfferShown = false
        } finally {
            super.tearDown()
        }
    }

    fun testNothingIsConnectedUntilSetupHasRun() {
        assertFalse(settings.aiLinked)
        assertFalse(settings.aiOfferShown)
    }

    fun testTheRunTabButtonInvitesSetupBeforeAnythingIsConnected() {
        assertEquals("AI Agents: Set Up…", AiButton.look(false, null).text)
        assertEquals("AI Agents: Set Up…", AiButton.look(false, GgMcpDaemonService.State.RUNNING).text)
    }

    fun testOnceConnectedItSaysReadyOnlyWhileTheDaemonRuns() {
        assertEquals("AI Agents: Ready", AiButton.look(true, GgMcpDaemonService.State.RUNNING).text)
        listOf(null, GgMcpDaemonService.State.STOPPED, GgMcpDaemonService.State.STARTING, GgMcpDaemonService.State.FAILED).forEach {
            val look = AiButton.look(true, it)
            assertEquals("AI Agents: Daemon Off", look.text)
            assertTrue(look.description.contains("will not show here"))
        }
    }

    fun testOnlyTheRunToolbarGetsTheChangingText() {
        val event = TestActionEvent.createTestEvent(SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build())
        val before = event.presentation.text
        ConfigureAiAgentsAction().update(event)
        assertEquals(before, event.presentation.text)
        assertTrue(event.presentation.isEnabledAndVisible)
    }

    fun testAllowAiSteerIsOfferedOnlyOnceAgentsAreConnected() {
        val host = IdeBridgeHost(project)
        assertFalse(host.agentsLinked())
        settings.aiLinked = true
        assertTrue(host.agentsLinked())
    }

    fun testASessionOnAProjectWithoutAgentsCannotBeSharedBack() {
        val info = DaemonInfo("http://127.0.0.1:1", "t", 0)
        fun session(shareable: Boolean) = DaemonRunSession(
            GopherGlideRunPanel(), PluginHttp(info), AnnotationSource.USER, {}, {}, Executor { it.run() }, { it() }, 600, shareable
        )
        assertFalse(session(false).agentSteerSupported)
        assertTrue(session(true).agentSteerSupported)
    }

    fun testTheBridgeAsksTheHostWhetherAgentsAreConnectedWhenAUserRunStarts() {
        val info = DaemonInfo("http://127.0.0.1:1", "t", 0)
        var linked = false
        val host = object : dev.gopherglide.ggplugin.mcp.BridgeHost {
            override fun panelIfFree(): GopherGlideRunPanel? = null
            override fun runEnded() {}
            override fun agentRunQueued() {}
            override fun agentsLinked() = linked
            override fun edt(action: () -> Unit) = action()
        }
        val bridge = PluginBridge(info, host, PluginHttp(info), Executor { }) 
        try {
            val body = com.google.gson.JsonParser.parseString("""{"profile":"smoke"}""").asJsonObject
            assertFalse(bridge.startUserRun(body, GopherGlideRunPanel()).agentSteerSupported)
            linked = true
            assertTrue(bridge.startUserRun(body, GopherGlideRunPanel()).agentSteerSupported)
        } finally {
            bridge.close()
        }
    }
}
