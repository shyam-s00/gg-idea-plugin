package dev.gopherglide.ggplugin.execution.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.gopherglide.ggplugin.execution.annotations.RunAnnotation
import dev.gopherglide.ggplugin.execution.annotations.formatElapsed
import dev.gopherglide.ggplugin.execution.annotations.formatSigned
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
 * Bias badge, stepper buttons and mark button for a running process, or an explanation when the binary
 * has no live control. Before a run it shows only what is known about the installed binary. Must be used on the EDT.
 */
class LiveControlPanel(
    private val elapsedSeconds: () -> Double,
    private val onAnnotation: (RunAnnotation) -> Unit,
    private val promptForLabel: (parent: Component) -> String? = ::askForMarkLabel,
    private val binaryActions: BinaryActions = BinaryActions.None
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
        }
        info.add(hint)
        info.add(lastMark)
        strip.add(controlsRow, BorderLayout.NORTH)
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
        runActive = true
        refresh()
    }

    fun onStarted(capabilities: List<String>?) {
        state.onStarted(capabilities)
        if (state.handshake == LiveControlState.Handshake.LEGACY) loadBinaryInfo()
        refresh()
    }

    fun onHeartbeat(bias: Int) {
        state.onHeartbeatBias(bias)
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

    private fun dropMark() {
        if (!state.canMark) return
        val label = promptForLabel(this)?.let(MarkLabels::normalize) ?: return
        val controls = controls
        if (controls == null || !state.canMark) {
            showHint("The run ended before the mark could be sent")
            return
        }
        val sentWith = state
        controls.sendMark(label) { reply -> onMarkReply(sentWith, label, reply) }
    }

    private fun onMarkReply(sentWith: LiveControlState, label: String, reply: ControlReply) {
        if (sentWith !== state) return
        if (reply is ControlReply.MarkRecorded) {
            showHint("")
            val mark = RunAnnotation.Mark(reply.payload.label ?: label, reply.payload.elapsedSeconds ?: elapsedSeconds())
            marks.add(mark)
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
        val shown = if (last.label.length > MAX_SHOWN_LABEL) last.label.take(MAX_SHOWN_LABEL) + "…" else last.label
        lastMark.text = "Last mark: “$shown” at ${formatElapsed(last.elapsedSeconds)} · ${marks.size} ${if (marks.size == 1) "mark" else "marks"}"
        lastMark.toolTipText = marks.joinToString(prefix = "<html>", separator = "<br>", postfix = "</html>") {
            "${formatElapsed(it.elapsedSeconds)}&nbsp;&nbsp;${escapeHtml(it.label)}"
        }
        lastMark.isVisible = true
        syncInfoRow()
    }

    private fun refresh() {
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
