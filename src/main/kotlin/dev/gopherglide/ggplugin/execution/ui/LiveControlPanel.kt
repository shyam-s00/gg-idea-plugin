package dev.gopherglide.ggplugin.execution.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.AutopilotBrief
import dev.gopherglide.ggplugin.execution.annotations.AnnotationSource
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
import dev.gopherglide.ggplugin.execution.autopilot.AutopilotOutcomes
import dev.gopherglide.ggplugin.execution.autopilot.SloGuardDialog
import dev.gopherglide.ggplugin.execution.autopilot.SloLimits
import dev.gopherglide.ggplugin.execution.control.BinaryActions
import dev.gopherglide.ggplugin.execution.control.BinaryNotice
import dev.gopherglide.ggplugin.execution.control.BinaryNotices
import dev.gopherglide.ggplugin.execution.control.ControlReply
import dev.gopherglide.ggplugin.execution.control.InstallProgress
import dev.gopherglide.ggplugin.execution.control.LiveControlState
import dev.gopherglide.ggplugin.execution.control.MarkLabels
import dev.gopherglide.ggplugin.execution.control.NoticeButton
import dev.gopherglide.ggplugin.execution.control.RunControls
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * Bias badge, stepper buttons, mark, rate hold and autopilot controls for a running process, or an
 * explanation when the binary has no live control. Before a run it shows only what is known about the installed binary. Must be used on the EDT.
 */
class LiveControlPanel(
    private val elapsedSeconds: () -> Double,
    private val onAnnotation: (RunAnnotation) -> Unit,
    private val promptForLabel: (parent: Component) -> String? = ::askForMarkLabel,
    private val binaryActions: BinaryActions = BinaryActions.None,
    private val promptForRate: (parent: Component, suggested: Int?) -> Int? = ::askForHoldRate,
    private val promptForLimits: (parent: Component) -> SloLimits? = ::askForSloLimits
) : JPanel(BorderLayout()) {
    private var state = LiveControlState()
    private var controls: RunControls? = null
    private var runActive = false
    private val marks = mutableListOf<RunAnnotation.Mark>()
    private var installed: InstalledBinary? = null
    private var latest: GopherGlideVersion? = null
    private var installProgress = InstallProgress.IDLE
    private var installError: String? = null
    private var beforeFirstRun = true
    private var noticeButtonKind = NoticeButton.NONE
    private var stopListening: () -> Unit = {}
    private var lastTargetRps: Int? = null

    private val badge = JBLabel().apply {
        isOpaque = true
        font = font.deriveFont(Font.BOLD)
        toolTipText = "Cumulative RPS added to the active stage. The effective rate never drops below 1 RPS."
    }
    private val hint = JBLabel().apply { foreground = JBColor.GRAY }
    private val lastMark = JBLabel().apply {
        foreground = JBColor(0xB8860B, 0xE8B341)
        isVisible = false
    }
    private val info = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        isOpaque = false
        isVisible = false
    }
    private val steps = listOf(-10, -5, 5, 10).map { amount -> amount to button(formatSigned(amount)) { send(amount) } }
    private val resetButton = button("Reset") { state.resetAmount()?.let(::send) }
    private val markButton = button("Mark…") { dropMark() }
    private var settingAgentSteer = false
    private val agentSteerBox = JBCheckBox("🤖 Allow AI Steer").apply {
        toolTipText = "AI agents can only read this run's telemetry unless this is checked."
        isFocusable = false
        addActionListener { if (!settingAgentSteer) toggleAgentSteer(isSelected) }
    }
    private val agentSteerRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
        isOpaque = false
        isVisible = false
        add(agentSteerBox)
    }
    private val holdBadge = JBLabel().apply {
        isOpaque = true
        font = font.deriveFont(Font.BOLD)
        foreground = JBColor(0x8A3F0A, 0xF0B27A)
        background = JBColor(0xFFEBDD, 0x3D2E22)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xE59A5C, 0x8A5A32)), JBUI.Borders.empty(1, 8)
        )
        toolTipText = "The rate is pinned and ignores the profile's ramp until released."
        isVisible = false
    }
    private val holdButton = button("Hold RPS") { toggleHold() }
    private val autopilotPill = JBLabel().apply {
        isOpaque = true
        font = font.deriveFont(Font.BOLD)
        foreground = JBColor(0x0D47A1, 0x9CCBFF)
        background = JBColor(0xE3F0FF, 0x263646)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0x8DBBE8, 0x3F6C96)), JBUI.Borders.empty(1, 8)
        )
        isVisible = false
    }
    private val guardButton = button("🛡️ Guard SLO…") { startGuard() }
    private val stopAutopilotButton = button("Stop Autopilot (Take Over)") { stopAutopilot() }
    private val autopilotSeparator = JSeparator(SwingConstants.VERTICAL).apply { preferredSize = JBUI.size(2, 20) }

    private val strip = JPanel(BorderLayout())
    private val noticeText = JBLabel()
    private val noticeHint = JBLabel().apply { foreground = JBColor.GRAY }
    private val noticeIcon = JBLabel(AllIcons.General.Information)
    private val noticeButton = button("") { onNoticeButton() }
    private val settingsLink = ActionLink("Open Settings") { binaryActions.openSettings() }
    private val notice = JPanel(WrapLayout(FlowLayout.LEFT, 8, 2))
    private val noticeBorder = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(JBColor(0x9DBBE8, 0x3B5A8C)),
        BorderFactory.createEmptyBorder(4, 8, 4, 8)
    )

    init {
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        buildStrip()
        buildNotice()
        add(strip, BorderLayout.NORTH)
        add(notice, BorderLayout.SOUTH)
        isVisible = false
        refresh()
    }

    private fun buildStrip() {
        strip.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(0xD3D3D3, 0x4A4D54)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        )
        val caption = JBLabel("LIVE CONTROL").apply {
            font = font.deriveFont(Font.PLAIN, font.size - 2f)
            foreground = JBColor.GRAY
        }
        val controlsRow = JPanel(WrapLayout(FlowLayout.LEFT, 8, 2)).apply {
            isOpaque = false
            add(caption)
            add(badge)
            steps.forEach { add(it.second) }
            add(resetButton)
            add(JSeparator(SwingConstants.VERTICAL).apply { preferredSize = JBUI.size(2, 20) })
            add(markButton)
            add(holdBadge)
            add(holdButton)
            add(autopilotSeparator)
            add(autopilotPill)
            add(guardButton)
            add(stopAutopilotButton)
        }
        info.add(hint)
        info.add(lastMark)
        strip.add(controlsRow, BorderLayout.NORTH)
        strip.add(agentSteerRow, BorderLayout.CENTER)
        strip.add(info, BorderLayout.SOUTH)
    }

    private fun buildNotice() {
        notice.background = JBColor(0xE8F1FF, 0x1F2B3D)
        notice.border = noticeBorder
        notice.add(noticeIcon)
        notice.add(noticeText)
        notice.add(noticeButton)
        notice.add(noticeHint)
        notice.add(settingsLink)
        notice.isVisible = false
    }

    override fun addNotify() {
        super.addNotify()
        stopListening = binaryActions.onChanged { if (beforeFirstRun) loadIdleStatus() }
        loadIdleStatus()
    }

    override fun removeNotify() {
        stopListening()
        stopListening = {}
        super.removeNotify()
    }

    private fun loadIdleStatus() {
        binaryActions.loadStatus { binary, newest ->
            if (!beforeFirstRun) return@loadStatus
            installed = binary
            latest = newest
            installProgress = InstallProgress.IDLE
            refresh()
        }
    }

    /** Starts a fresh run: shown, but disabled until the process reports what it supports. */
    fun reset() {
        beforeFirstRun = false
        state = LiveControlState()
        controls = null
        runActive = false
        marks.clear()
        lastTargetRps = null
        setAgentSteerSelected(false)
        agentSteerRow.isVisible = false
        installed = null
        latest = null
        installProgress = InstallProgress.IDLE
        installError = null
        lastMark.isVisible = false
        showHint("")
        isVisible = true
        refresh()
    }

    fun bind(controls: RunControls) {
        this.controls = controls
        agentSteerRow.isVisible = controls.agentSteerSupported
        runActive = true
        refresh()
    }

    fun onStarted(capabilities: List<String>?) {
        state.onStarted(capabilities)
        if (state.handshake == LiveControlState.Handshake.LEGACY) loadBinaryInfo()
        refresh()
    }

    fun onHeartbeat(bias: Int, holdRps: Int? = null, autopilot: AutopilotBrief? = null, targetRps: Int? = null) {
        targetRps?.let { lastTargetRps = it }
        state.onHeartbeatBias(bias)
        state.onHoldReported(holdRps)
        state.onHeartbeatAutopilot(autopilot)
        refresh()
    }

    fun onAutopilotEvent(mode: String?, autopilotState: String?) {
        state.onAutopilotEvent(mode, autopilotState)
        refresh()
    }

    /** A mark nobody here asked for, such as one the autopilot drops. */
    fun onUnsolicitedMark(label: String, elapsedSeconds: Double, source: AnnotationSource) {
        val mark = RunAnnotation.Mark(label, elapsedSeconds, source)
        marks.add(mark)
        marks.sortBy { it.elapsedSeconds }
        onAnnotation(mark)
        showLastMark()
        refresh()
    }

    fun onStopRequested() {
        state.onStopRequested()
        refresh()
    }

    fun onEnded() {
        state.onEnded()
        runActive = false
        refresh()
    }

    private fun loadBinaryInfo() {
        val sentWith = state
        controls?.loadBinaryInfo { binary, newest ->
            if (sentWith !== state) return@loadBinaryInfo
            installed = binary
            latest = newest
            refresh()
        }
    }

    private fun onNoticeButton() {
        when (noticeButtonKind) {
            NoticeButton.UPGRADE, NoticeButton.INSTALL -> startInstall()
            NoticeButton.OPEN_SETTINGS -> binaryActions.openSettings()
            NoticeButton.NONE -> Unit
        }
    }

    private fun startInstall() {
        val sentWith = state
        installProgress = InstallProgress.DOWNLOADING
        installError = null
        refresh()
        binaryActions.install { error ->
            if (sentWith !== state) return@install
            installProgress = if (error == null) InstallProgress.DONE else InstallProgress.IDLE
            installError = error?.message ?: error?.javaClass?.simpleName
            refresh()
        }
    }

    private fun send(amount: Int) {
        val controls = controls ?: return
        if (!state.canBias) return
        val sentWith = state
        val token = state.addPending(amount)
        refresh()
        controls.sendBias(amount) { reply -> onBiasReply(sentWith, token, amount, reply) }
    }

    private fun onBiasReply(sentWith: LiveControlState, token: Int, amount: Int, reply: ControlReply) {
        if (sentWith !== state) return
        state.resolve(token, reply)
        when (reply) {
            is ControlReply.Ack -> {
                showHint("")
                onAnnotation(RunAnnotation.BiasChange(amount, reply.payload.bias ?: state.displayedBias, elapsedSeconds()))
            }
            else -> showFailure(reply)
        }
        refresh()
    }

    private fun toggleAgentSteer(allow: Boolean) {
        val controls = controls ?: return
        val sentWith = state
        controls.setAgentSteer(allow) { reply ->
            if (sentWith !== state) return@setAgentSteer
            if (reply is ControlReply.Ack) {
                showHint("")
            } else {
                setAgentSteerSelected(!allow)
                showFailure(reply)
            }
            refresh()
        }
    }

    private fun setAgentSteerSelected(value: Boolean) {
        settingAgentSteer = true
        agentSteerBox.isSelected = value
        settingAgentSteer = false
    }

    private fun toggleHold() {
        val controls = controls ?: return
        val sentWith = state
        if (state.holdRps != null) {
            if (!state.canRelease) return
            controls.sendRelease { reply -> onHoldReply(sentWith, reply) }
            return
        }
        if (!state.canHold) return
        val rps = promptForRate(this, lastTargetRps) ?: return
        if (!state.canHold) {
            showHint("The run ended before the hold could be sent")
            return
        }
        controls.sendHold(rps) { reply -> onHoldReply(sentWith, reply) }
    }

    private fun onHoldReply(sentWith: LiveControlState, reply: ControlReply) {
        if (sentWith !== state) return
        if (reply is ControlReply.Ack) {
            showHint("")
            state.onHoldReported(reply.payload.holdRps)
            val at = reply.payload.elapsedSeconds ?: elapsedSeconds()
            onAnnotation(RunAnnotation.HoldChange(reply.payload.holdRps, at, AnnotationSource.fromWire(reply.payload.source)))
        } else {
            showFailure(reply)
        }
        refresh()
    }

    private fun startGuard() {
        val controls = controls ?: return
        if (!state.canStartAutopilot) return
        val limits = promptForLimits(this) ?: return
        if (!state.canStartAutopilot) {
            showHint("The run ended before the guard could start")
            return
        }
        val sentWith = state
        controls.startAutopilot(GUARD_MODE, limits.maxP99Ms, limits.maxErrorRate.toDouble()) { reply -> onAutopilotReply(sentWith, reply) }
    }

    private fun stopAutopilot() {
        val controls = controls ?: return
        if (!state.canStopAutopilot) return
        val sentWith = state
        controls.stopAutopilot { reply -> onAutopilotReply(sentWith, reply) }
    }

    private fun onAutopilotReply(sentWith: LiveControlState, reply: ControlReply) {
        if (sentWith !== state) return
        if (reply is ControlReply.Ack) showHint("") else showFailure(reply)
        refresh()
    }

    private fun dropMark() {
        if (!state.canMark) return
        val clickedAt = elapsedSeconds()
        val label = promptForLabel(this)?.let(MarkLabels::normalize) ?: return
        val controls = controls
        if (controls == null || !state.canMark) {
            showHint("The run ended before the mark could be sent")
            return
        }
        val sentWith = state
        val at = clickedAt.takeIf { state.canMarkWithElapsed }?.let { Math.round(it * 1000) / 1000.0 }
        controls.sendMark(label, at) { reply -> onMarkReply(sentWith, label, clickedAt, reply) }
    }

    private fun onMarkReply(sentWith: LiveControlState, label: String, clickedAt: Double, reply: ControlReply) {
        if (sentWith !== state) return
        if (reply is ControlReply.MarkRecorded) {
            showHint("")
            val mark = RunAnnotation.Mark(reply.payload.label ?: label, reply.payload.elapsedSeconds ?: clickedAt)
            marks.add(mark)
            marks.sortBy { it.elapsedSeconds }
            onAnnotation(mark)
            showLastMark()
        } else {
            showFailure(reply)
        }
        refresh()
    }

    private fun showFailure(reply: ControlReply) {
        when (reply) {
            is ControlReply.Rejected -> showHint("gg rejected a command: ${reply.payload.reason} — ${reply.payload.message}", error = true)
            is ControlReply.Failed -> showHint("Command failed: ${reply.reason}", error = true)
            else -> Unit
        }
    }

    private fun showLastMark() {
        val last = marks.last()
        val badge = if (last.source == AnnotationSource.USER) "" else "${last.source.badge} "
        val shown = badge + if (last.label.length > MAX_SHOWN_LABEL) last.label.take(MAX_SHOWN_LABEL) + "…" else last.label
        lastMark.text = "Last mark: “$shown” at ${formatElapsed(last.elapsedSeconds)} · ${marks.size} ${if (marks.size == 1) "mark" else "marks"}"
        lastMark.toolTipText = marks.joinToString(prefix = "<html>", separator = "<br>", postfix = "</html>") {
            val badge = if (it.source == AnnotationSource.USER) "" else "${it.source.badge} "
            "${formatElapsed(it.elapsedSeconds)}&nbsp;&nbsp;$badge${escapeHtml(it.label)}"
        }
        lastMark.isVisible = true
        syncInfoRow()
    }

    private fun refresh() {
        agentSteerBox.isEnabled = state.handshake == LiveControlState.Handshake.ACTIVE && !state.isOver
        val legacy = !beforeFirstRun && state.handshake == LiveControlState.Handshake.LEGACY
        val model = when {
            beforeFirstRun -> BinaryNotices.idle(installed, latest, installProgress)
            legacy -> BinaryNotices.forRun(installed, latest, runActive, installProgress)
            else -> null
        }
        strip.isVisible = !beforeFirstRun && !legacy
        notice.isVisible = model != null
        if (model != null) renderNotice(model)
        if (beforeFirstRun) isVisible = model != null
        if (!beforeFirstRun) when (state.handshake) {
            LiveControlState.Handshake.PENDING -> if (hint.text.isEmpty()) showHint("Waiting for gg to report capabilities…")
            LiveControlState.Handshake.NO_CONTROL -> showHint("Live control is off for this run")
            LiveControlState.Handshake.ACTIVE -> if (hint.text.startsWith("Waiting")) showHint("")
            LiveControlState.Handshake.LEGACY -> Unit
        }
        val shown = state.handshake == LiveControlState.Handshake.ACTIVE
        val bias = state.displayedBias
        badge.text = if (shown) "Bias ${formatSigned(bias)} RPS" else "Bias —"
        applyBadgeColors(if (shown) bias else 0)
        steps.forEach { it.second.isEnabled = state.canBias }
        resetButton.isEnabled = state.canReset
        markButton.isEnabled = state.canMark
        refreshHoldAndAutopilot(shown)
    }

    private fun refreshHoldAndAutopilot(shown: Boolean) {
        val hold = state.holdRps
        holdBadge.isVisible = shown && hold != null
        holdBadge.text = "📌 Hold ${hold ?: ""} RPS"
        holdButton.isVisible = state.offersHold
        holdButton.text = if (hold != null) "Release Hold" else "Hold RPS"
        holdButton.isEnabled = if (hold != null) state.canRelease else state.canHold

        val brief = state.autopilot
        autopilotSeparator.isVisible = state.offersHold || state.offersAutopilot
        autopilotPill.isVisible = shown && brief != null
        autopilotPill.text = brief?.let { AutopilotOutcomes.pill(it.mode, it.state, hold) }.orEmpty()
        guardButton.isVisible = state.offersAutopilot && brief == null
        guardButton.isEnabled = state.canStartAutopilot
        stopAutopilotButton.isVisible = state.offersAutopilot && brief != null
        stopAutopilotButton.isEnabled = state.canStopAutopilot

        val lock = if (state.autopilotActive) AUTOPILOT_OWNS_RATE else null
        (steps.map { it.second } + listOf(resetButton, holdButton)).forEach { it.toolTipText = lock }
    }

    private fun renderNotice(model: BinaryNotice) {
        notice.isOpaque = !model.quiet
        notice.border = if (model.quiet) BorderFactory.createEmptyBorder(4, 0, 0, 0) else noticeBorder
        noticeIcon.isVisible = !model.quiet
        noticeText.text = model.text
        noticeText.foreground = if (model.quiet) JBColor.GRAY else UIUtil.getLabelForeground()
        noticeButtonKind = model.button
        noticeButton.isVisible = model.button != NoticeButton.NONE
        noticeButton.text = model.buttonLabel
        noticeButton.isEnabled = model.buttonEnabled
        val failure = installError?.let { "${if (model.button == NoticeButton.UPGRADE) "Upgrade" else "Install"} failed: $it" }
        noticeHint.text = failure ?: model.hint.orEmpty()
        noticeHint.foreground = if (failure != null) JBColor.RED else JBColor.GRAY
        settingsLink.isVisible = model.settingsLink
    }

    private fun showHint(text: String, error: Boolean = false) {
        hint.text = text
        hint.foreground = if (error) JBColor.RED else JBColor.GRAY
        syncInfoRow()
    }

    private fun syncInfoRow() {
        info.isVisible = hint.text.isNotEmpty() || lastMark.isVisible
    }

    private fun applyBadgeColors(bias: Int) {
        val (fg, bg, line) = when {
            bias > 0 -> Triple(JBColor(0x1B5E20, 0x8BDB96), JBColor(0xE0F2E1, 0x25402B), JBColor(0x8BC995, 0x3F7B49))
            bias < 0 -> Triple(JBColor(0xB71C1C, 0xF7788A), JBColor(0xFDE7E9, 0x4A2328), JBColor(0xE59AA3, 0x7B3F47))
            else -> Triple(JBColor.GRAY, JBColor(0xF0F0F0, 0x2B2D30), JBColor(0xD0D0D0, 0x4A4D54))
        }
        badge.foreground = fg
        badge.background = bg
        badge.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(line), JBUI.Borders.empty(1, 8)
        )
    }

    private fun button(text: String, onClick: () -> Unit) = JButton(text).apply {
        isFocusable = false
        margin = JBUI.insets(1, 8)
        addActionListener { onClick() }
    }

    private fun escapeHtml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val MAX_SHOWN_LABEL = 40
        const val GUARD_MODE = "slo-guard"
        const val AUTOPILOT_OWNS_RATE = "The autopilot owns the rate. Stop it to steer by hand."
    }
}

private fun askForMarkLabel(parent: Component): String? = Messages.showInputDialog(
    parent,
    "Label for this point in the run:",
    "Drop Mark",
    null,
    "",
    object : InputValidatorEx {
        override fun checkInput(inputString: String?) = MarkLabels.normalize(inputString.orEmpty()) != null
        override fun canClose(inputString: String?) = checkInput(inputString)
        override fun getErrorText(inputString: String?) = if (checkInput(inputString)) null else "Enter a label"
    }
)

private fun askForHoldRate(parent: Component, suggested: Int?): Int? = Messages.showInputDialog(
    parent,
    "Rate to pin, in requests per second:",
    "Hold RPS",
    null,
    suggested?.toString().orEmpty(),
    object : InputValidatorEx {
        override fun checkInput(inputString: String?) = inputString?.trim()?.toIntOrNull()?.let { it >= 1 } == true
        override fun canClose(inputString: String?) = checkInput(inputString)
        override fun getErrorText(inputString: String?) = if (checkInput(inputString)) null else "Enter a whole number of at least 1"
    }
)?.trim()?.toIntOrNull()

private fun askForSloLimits(parent: Component): SloLimits? =
    SloGuardDialog(parent).let { if (it.showAndGet()) it.limits else null }
