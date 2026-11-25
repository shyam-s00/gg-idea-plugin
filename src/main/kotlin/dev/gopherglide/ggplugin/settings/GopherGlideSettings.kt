package dev.gopherglide.ggplugin.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "dev.gopherglide.ggplugin.settings.GopherGlideSettings",
    storages = [Storage("GopherGlideSettings.xml")]
)
@Service(Service.Level.APP)
class GopherGlideSettings : PersistentStateComponent<GopherGlideSettings> {
    var customBinaryPath: String = ""
    var customSnapshotsDir: String = ""
    var dontAskForMissingBinary: Boolean = false
    /** Seconds between headless JSON heartbeats. 0 means "unset" — gg's own 5s default applies. */
    var heartbeatIntervalSeconds: Int = 0
    /** Counts successful runs toward the Marketplace rating prompt; reset by "Remind me later". */
    var successfulRunCount: Int = 0
    var dontShowRatingPrompt: Boolean = false
    var checkForUpdatesOnStartup: Boolean = true
    var lastUpdateCheckMillis: Long = 0L
    /** A release tag the user chose to ignore; a newer one is offered again. */
    var skippedGgVersion: String = ""
    /** Set by "Don't Ask Again" on the offer to connect AI agents. */
    var dontAskForAiAgents: Boolean = false
    /** Run user-started simulations through the shared `gg mcp` daemon (needs gg v1.5.0+). */
    var routeRunsThroughDaemon: Boolean = false
    /** Start the shared daemon when a project opens, so MCP clients attach and agent runs show up. */
    var startDaemonWithProject: Boolean = false
    var daemonMaxRps: Int = 1000
    var daemonMaxDurationMinutes: Int = 10
    var daemonMaxAgentSessions: Int = 2
    /** Comma-separated hosts agents may target; empty means the hosts found in the project's .http files. */
    var daemonAllowHosts: String = ""

    override fun getState(): GopherGlideSettings = this

    override fun loadState(state: GopherGlideSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: GopherGlideSettings
            get() = service()
    }
}
