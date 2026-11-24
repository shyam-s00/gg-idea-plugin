package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.execution.AutopilotBrief

/**
 * What the live-control strip may do and show for one run. Not thread-safe: use on the EDT only.
 * The shown bias is the last value gg reported plus the deltas still waiting for their reply.
 * The pinned rate and the running autopilot come from heartbeats, so they stay right when gg changes them itself.
 */
class LiveControlState {
    enum class Handshake {
        PENDING,
        /** The binary predates live control (no capabilities reported). */
        LEGACY,
        /** Live control is switched off for this run (empty capabilities). */
        NO_CONTROL,
        ACTIVE
    }

    var handshake = Handshake.PENDING
        private set

    private var capabilities = emptySet<String>()
    private var stopRequested = false
    private var ended = false
    private var reportedBias = 0
    private val pending = LinkedHashMap<Int, Int>()
    private var nextToken = 0

    /** The pinned rate, or null when none is. Survives the autopilot that set it. */
    var holdRps: Int? = null
        private set

    /** The autopilot that is running, or null. While set, gg refuses manual bias and hold. */
    var autopilot: AutopilotBrief? = null
        private set

    val displayedBias: Int get() = reportedBias + pending.values.sum()

    val autopilotActive: Boolean get() = autopilot != null

    val canBias: Boolean get() = usable(BIAS) && !autopilotActive
    val canMark: Boolean get() = usable(MARK)

    /** Whether a mark can carry the time it was asked for instead of the time gg receives it. */
    val canMarkWithElapsed: Boolean get() = canMark && MARK_ELAPSED in capabilities
    /** Whether the binary has these controls at all, so the strip can hide them on older ones. */
    val offersHold: Boolean get() = handshake == Handshake.ACTIVE && HOLD in capabilities
    val offersAutopilot: Boolean get() = handshake == Handshake.ACTIVE && AUTOPILOT in capabilities

    val canHold: Boolean get() = usable(HOLD) && !autopilotActive
    val canRelease: Boolean get() = usable(HOLD) && holdRps != null && !autopilotActive
    val canStartAutopilot: Boolean get() = usable(AUTOPILOT) && !autopilotActive
    val canStopAutopilot: Boolean get() = usable(AUTOPILOT) && autopilotActive
    val canReset: Boolean get() = canBias && displayedBias != 0

    /** The amount that brings the shown bias back to zero, or null when there is nothing to reset. */
    fun resetAmount(): Int? = if (canReset) -displayedBias else null

    fun onStarted(capabilities: List<String>?) {
        handshake = when {
            capabilities == null -> Handshake.LEGACY
            capabilities.isEmpty() -> Handshake.NO_CONTROL
            else -> Handshake.ACTIVE
        }
        this.capabilities = capabilities?.toSet().orEmpty()
    }

    fun onHeartbeatBias(bias: Int) {
        reportedBias = bias
    }

    /** From a heartbeat or a hold/release ack; null means nothing is pinned. */
    fun onHoldReported(holdRps: Int?) {
        this.holdRps = holdRps
    }

    fun onHeartbeatAutopilot(autopilot: AutopilotBrief?) {
        this.autopilot = autopilot
    }

    /** An "autopilot" event: any state but "done" means it is still running. */
    fun onAutopilotEvent(mode: String?, state: String?) {
        autopilot = if (state == null || state == AUTOPILOT_DONE) null else AutopilotBrief(mode.orEmpty(), state)
    }

    fun onStopRequested() {
        stopRequested = true
    }

    fun onEnded() {
        ended = true
        autopilot = null
        pending.clear()
    }

    /** Registers a bias command about to be sent; the token identifies it in [resolve]. */
    fun addPending(amount: Int): Int {
        val token = nextToken++
        pending[token] = amount
        return token
    }

    /** Settles a pending command: an ack carries gg's new total, anything else just drops the delta. */
    fun resolve(token: Int, reply: ControlReply) {
        val amount = pending.remove(token) ?: return
        if (reply is ControlReply.Ack) reportedBias = reply.payload.bias ?: (reportedBias + amount)
    }

    private fun usable(capability: String) =
        handshake == Handshake.ACTIVE && capability in capabilities && !stopRequested && !ended

    private companion object {
        const val BIAS = "control.bias"
        const val MARK = "control.mark"
        const val MARK_ELAPSED = "control.mark.elapsed"
        const val HOLD = "control.hold"
        const val AUTOPILOT = "control.autopilot"
        const val AUTOPILOT_DONE = "done"
    }
}
