package dev.gopherglide.ggplugin.execution.control

/** Where a user-requested stop currently stands. */
enum class StopPhase {
    NONE,
    AWAITING_ACK,
    FINALIZING,
    INTERRUPTING,
    NO_ACK_INTERRUPTING
}

interface ProcessKiller {
    /** Interrupt signal: gg finalizes its snapshot and exits. */
    fun softKill()

    /** Immediate kill: the snapshot is not written. */
    fun hardKill()
}

interface Scheduler {
    /** Runs [task] after [delayMs]; the returned function cancels it. */
    fun schedule(delayMs: Long, task: Runnable): () -> Unit
}

/**
 * Stop escalation: a clean stop command, a soft kill if gg does not acknowledge it in time, and a hard kill
 * only when the user asks again. Once gg has acknowledged, nothing is killed automatically.
 */
class StopLadder(
    private val channel: StopChannel,
    private val killer: ProcessKiller,
    private val scheduler: Scheduler,
    private val clock: () -> Long,
    private val onPhase: (StopPhase) -> Unit,
    private val ackTimeoutMs: Long = ACK_TIMEOUT_MS,
    private val forceGuardMs: Long = FORCE_GUARD_MS
) {
    private var capabilities: List<String>? = null
    private var started = false
    private var terminated = false
    private var firstRequestAt = 0L
    private var cancelTimer: (() -> Unit)? = null

    var phase = StopPhase.NONE
        private set

    @Synchronized
    fun onStarted(capabilities: List<String>?) {
        this.capabilities = capabilities
        started = true
    }

    @Synchronized
    fun onTerminated() {
        terminated = true
        cancelTimer?.invoke()
        cancelTimer = null
    }

    /** First call asks politely; later calls (after a short guard against double-clicks) force-kill. */
    @Synchronized
    fun requestStop() {
        if (terminated) return
        val now = clock()
        if (phase != StopPhase.NONE) {
            if (now - firstRequestAt >= forceGuardMs) killer.hardKill()
            return
        }
        firstRequestAt = now
        if (started && capabilities?.contains("control.stop") == true) {
            setPhase(StopPhase.AWAITING_ACK)
            cancelTimer = scheduler.schedule(ackTimeoutMs) { onAckTimeout() }
            channel.sendStop(::onStopReply)
        } else {
            setPhase(StopPhase.INTERRUPTING)
            killer.softKill()
        }
    }

    @Synchronized
    private fun onStopReply(reply: ControlReply) {
        if (terminated || phase != StopPhase.AWAITING_ACK) return
        cancelTimer?.invoke()
        cancelTimer = null
        if (reply is ControlReply.Ack) setPhase(StopPhase.FINALIZING) else interruptWithoutAck()
    }

    @Synchronized
    private fun onAckTimeout() {
        if (!terminated && phase == StopPhase.AWAITING_ACK) interruptWithoutAck()
    }

    private fun interruptWithoutAck() {
        setPhase(StopPhase.NO_ACK_INTERRUPTING)
        killer.softKill()
    }

    private fun setPhase(next: StopPhase) {
        phase = next
        onPhase(next)
    }

    companion object {
        const val ACK_TIMEOUT_MS = 2_500L
        const val FORCE_GUARD_MS = 1_000L
    }
}
