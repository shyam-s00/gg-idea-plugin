package dev.gopherglide.ggplugin.ai

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.JBColor
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import javax.swing.JComponent

/** What the setup dialog returns: the choices, plus the daemon limits that go to Settings. */
data class AiDialogResult(val request: AiRequest, val maxRps: Int, val maxDurationMinutes: Int, val maxSessions: Int, val allowHosts: String)

/** Connects AI agents to gg: one place to see what is linked, and to link or repair it. */
class ConfigureAiAgentsDialog(
    project: Project,
    private val view: SetupView,
    private val hostsLine: String,
    private val agentEnv: Map<String, String>,
    private val preview: (AiRequest, (String) -> Unit) -> Unit
) : DialogWrapper(project) {
    private val mcpBox = JBCheckBox("Link this project's .mcp.json (gg mcp --attach)", view.defaultMcp && !view.mcpBlocked)
    private val replaceMcpBox = JBCheckBox(view.replaceMcp.orEmpty(), false)
    private val skillBox = JBCheckBox("Install the agent skill (.claude/skills/gg/SKILL.md)", view.defaultSkill)
    private val excludeBox = JBCheckBox("Keep .mcp.json out of version control (adds it to .git/info/exclude)", view.offerExclude)
    private val acpBox = JBCheckBox("Register gg autopilot in JetBrains Air (~/.jetbrains/acp.json)", view.defaultAcp && !view.acpBlocked)
    private val replaceSkillBox = JBCheckBox(view.replaceSkill, false)
    private val replaceAcpBox = JBCheckBox(view.replaceAcp, false)

    private val geminiKey = JBPasswordField().apply { text = agentEnv["GEMINI_API_KEY"].orEmpty() }
    private val model = JBTextField(agentEnv["GG_AUTOPILOT_MODEL"].orEmpty())
    private val baseUrl = JBTextField(agentEnv["GG_AUTOPILOT_BASE_URL"].orEmpty())
    private val apiKey = JBPasswordField().apply { text = agentEnv["GG_AUTOPILOT_API_KEY"].orEmpty() }

    private val settings = GopherGlideSettings.instance
    private val maxRps = JBIntSpinner(settings.daemonMaxRps, 1, 1_000_000)
    private val maxDuration = JBIntSpinner(settings.daemonMaxDurationMinutes, 1, 24 * 60)
    private val maxSessions = JBIntSpinner(settings.daemonMaxAgentSessions, 1, 20)
    private val allowHosts = JBTextField(settings.daemonAllowHosts)

    init {
        title = "Connect AI Agents"
        setOKButtonText("Apply")
        followTick(skillBox, replaceSkillBox)
        followTick(acpBox, replaceAcpBox)
        init()
    }

    val result: AiDialogResult
        get() = AiDialogResult(request(), maxRps.number, maxDuration.number, maxSessions.number, allowHosts.text.trim())

    fun request(): AiRequest = AiRequest(
        mcp = mcpBox.isSelected,
        skill = skillBox.isSelected,
        acp = acpBox.isSelected,
        replaceMcp = replaceMcpBox.isSelected,
        replaceAcp = replaceAcpBox.isSelected,
        replaceSkill = replaceSkillBox.isSelected,
        excludeFromGit = excludeBox.isSelected,
        agentEnv = mapOf(
            "GEMINI_API_KEY" to String(geminiKey.password),
            "GG_AUTOPILOT_MODEL" to model.text.trim(),
            "GG_AUTOPILOT_BASE_URL" to baseUrl.text.trim(),
            "GG_AUTOPILOT_API_KEY" to String(apiKey.password)
        ).takeIf { acpBox.isSelected }
    )

    override fun createCenterPanel(): JComponent = panel {
        row {
            label("Lets Claude Code, Cursor, Junie, JetBrains AI Assistant and JetBrains Air start and steer gg runs that show live in the Run tab.")
        }
        if (hostsLine.isNotBlank()) row { label(hostsLine).applyToComponent { foreground = JBColor.GRAY } }

        group("Status") {
            row("Shared daemon:") { label(view.daemon) }
            view.daemonNote?.let { note -> row { label(note).applyToComponent { foreground = JBColor(0xB26A00, 0xE8B341) } } }
            row(".mcp.json:") { label(view.mcp) }
            row("Agent skill:") { label(view.skill) }
            row("JetBrains Air:") { label(view.acp) }
        }

        group("Set up") {
            row { cell(mcpBox).enabled(!view.mcpBlocked) }
            if (view.replaceMcp != null) row { cell(replaceMcpBox) }
            if (view.offerExclude) row { cell(excludeBox) }
            row { cell(skillBox).enabled(!view.mcpBlocked) }
            row { cell(replaceSkillBox).applyToComponent { border = javax.swing.BorderFactory.createEmptyBorder(0, 20, 0, 0) } }
            row { cell(acpBox).enabled(!view.acpBlocked) }
            row { label("Its slash commands (/dry-run, /find-ceiling, /slo-guard, /triage) need no API key.").applyToComponent { foreground = JBColor.GRAY } }
            row { cell(replaceAcpBox).applyToComponent { border = javax.swing.BorderFactory.createEmptyBorder(0, 20, 0, 0) } }
            row {
                cell(ActionLink("Preview changes…") { showPreview() })
            }
        }

        collapsibleGroup("Optional: a language model for open-ended questions") {
            row("Gemini API key:") { cell(geminiKey).align(AlignX.FILL) }
            row("Model:") { cell(model).align(AlignX.FILL) }
            row("OpenAI-compatible URL:") { cell(baseUrl).align(AlignX.FILL) }
            row("Its API key:") { cell(apiKey).align(AlignX.FILL) }
            row { label("Stored as plain text in ~/.jetbrains/gopher-glide/agent.env, readable by you only.").applyToComponent { foreground = JBColor.GRAY } }
        }

        collapsibleGroup("Shared daemon limits for agents") {
            row("Limits:") {
                label("max RPS")
                cell(maxRps)
                label("max duration")
                cell(maxDuration)
                label("min, runs at once")
                cell(maxSessions)
            }
            row("Hosts agents may target:") { cell(allowHosts).align(AlignX.FILL) }
            row { label("Empty means the hosts in this project's .http files, or any host if one can't be read.").applyToComponent { foreground = JBColor.GRAY } }
        }
    }

    private fun followTick(parent: JBCheckBox, child: JBCheckBox) {
        child.isEnabled = parent.isSelected
        parent.addItemListener { child.isEnabled = parent.isSelected }
    }

    private fun showPreview() {
        preview(request()) { text -> PreviewDialog(contentPane, text).show() }
    }

    private class PreviewDialog(parent: java.awt.Component, private val text: String) : DialogWrapper(parent, false) {
        init {
            title = "Changes"
            setOKButtonText("Close")
            init()
        }

        override fun createActions() = arrayOf(okAction)

        override fun createCenterPanel(): JComponent =
            com.intellij.ui.components.JBScrollPane(com.intellij.ui.components.JBTextArea(text).apply {
                isEditable = false
                font = com.intellij.util.ui.UIUtil.getLabelFont().deriveFont(com.intellij.util.ui.UIUtil.getLabelFont().size2D - 1f)
                rows = 18
                columns = 80
            })
    }
}
