package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.mcp.GgMcpDaemonService.State

/** The words and defaults of the setup dialog for one project's current state; what is already set up shows ticked. */
data class SetupView(
    val daemon: String,
    /** Set when agent runs would not appear in the IDE, and why. */
    val daemonNote: String?,
    val mcp: String,
    val skill: String,
    val acp: String,
    val defaultMcp: Boolean,
    val defaultSkill: Boolean,
    val defaultAcp: Boolean,
    /** The label of the "replace" box, or null when there is no entry of someone else's to replace. */
    val replaceMcp: String?,
    /** The overwrite boxes for the skill and Air are always offered, so a stale or edited file can be redone. */
    val replaceSkill: String,
    val replaceAcp: String,
    /** Whether to offer keeping `.mcp.json` out of git, which only helps when it holds a machine-specific path. */
    val offerExclude: Boolean,
    val mcpBlocked: Boolean,
    val acpBlocked: Boolean
)

object AiSetupViews {
    fun of(status: AiStatus, daemon: State, daemonError: String?, target: AiTarget?, gitCheckout: Boolean, jetBrainsDirPresent: Boolean): SetupView {
        val mcp = status.mcp
        val acp = status.acp
        val daemonText = when (daemon) {
            State.RUNNING -> "Running"
            State.STARTING -> "Starting…"
            State.FAILED -> "Failed: ${daemonError ?: "unknown reason"}"
            State.STOPPED -> "Not running"
        }
        val linkedSomewhere = status.anythingLinked
        val note = if (daemon == State.RUNNING) null else {
            "Agent runs will not appear in the IDE until the daemon is running." +
                if (linkedSomewhere) " An MCP client still works on its own, but its runs stay invisible here." else ""
        }
        return SetupView(
            daemon = daemonText,
            daemonNote = note,
            mcp = when (mcp.health) {
                McpHealth.NO_FILE -> "Not set up"
                McpHealth.MALFORMED -> "Not valid JSON. Fix or remove it first."
                McpHealth.NO_ENTRY -> "No gg entry" + others(mcp.otherServers.size, "server")
                McpHealth.LINKED -> "Linked: ${mcp.entry?.display}"
                McpHealth.NEEDS_ATTACH -> "Set up, but not attached to the IDE's daemon (${mcp.entry?.display})"
                McpHealth.DIFFERENT -> "Has its own gg entry: ${mcp.entry?.display}"
            },
            skill = if (status.skillPresent) "Installed" else "Not installed",
            acp = when (acp.health) {
                AcpHealth.NO_FILE -> "No acp.json yet"
                AcpHealth.MALFORMED -> "Not valid JSON. Fix or move it first."
                AcpHealth.NO_ENTRY -> "gg autopilot is not registered" + others(acp.otherAgents.size, "agent")
                AcpHealth.REGISTERED -> "Registered through the IDE launcher"
                AcpHealth.DIFFERENT -> "Registered differently: ${acp.entry?.display}"
            },
            defaultMcp = mcp.health != McpHealth.MALFORMED && mcp.health != McpHealth.DIFFERENT,
            defaultSkill = mcp.health != McpHealth.MALFORMED,
            defaultAcp = acp.health == AcpHealth.REGISTERED ||
                ((acp.health == AcpHealth.NO_ENTRY || acp.health == AcpHealth.NO_FILE) && jetBrainsDirPresent),
            replaceMcp = mcp.entry?.takeIf { mcp.health == McpHealth.DIFFERENT }?.let { "Replace the existing gg entry (currently: ${it.display})" },
            replaceSkill = "Overwrite the skill file even if it differs",
            replaceAcp = acp.entry?.takeIf { acp.health == AcpHealth.DIFFERENT }?.let { "Replace the existing gg autopilot entry (currently: ${it.display})" }
                ?: "Overwrite the gg autopilot entry even if it differs",
            offerExclude = gitCheckout && target?.commandIsPath == true,
            mcpBlocked = mcp.health == McpHealth.MALFORMED,
            acpBlocked = acp.health == AcpHealth.MALFORMED
        )
    }

    private fun others(count: Int, noun: String) = if (count == 0) "" else " ($count other ${noun}${if (count == 1) "" else "s"} kept)"
}
