package dev.gopherglide.ggplugin.execution.control

/**
 * What the live-control strip may do and show for one run. Not thread-safe: use on the EDT only.
 * The shown bias is the last value gg reported plus the deltas still waiting for their reply.
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

    val displayedBias: Int get() = reportedBias + pending.values.sum()

    val canBias: Boolean get() = usable(BIAS)
    val canMark: Boolean get() = usable(MARK)
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

    fun onStopRequested() {
        stopRequested = true
    }

    fun onEnded() {
        ended = true
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
    }
}
