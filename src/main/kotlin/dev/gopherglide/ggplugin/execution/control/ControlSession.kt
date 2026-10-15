package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** The outcome of one command; callbacks may run on any thread. */
sealed class ControlReply {
    data class Ack(val payload: HeartbeatPayload) : ControlReply()
    data class MarkRecorded(val payload: HeartbeatPayload) : ControlReply()
    data class Rejected(val payload: HeartbeatPayload) : ControlReply()
    /** The command never reached the process (write failed, channel closed, or stop already requested). */
    data class Failed(val reason: String) : ControlReply()
}

/** What the stop ladder needs from the channel. */
interface StopChannel {
    fun sendStop(onReply: (ControlReply) -> Unit)
}

/**
 * Writes commands to a run's stdin and matches replies back to them by id. Writes go through [executor],
 * which must run tasks one at a time in submission order so commands reach the process in click order.
 */
class ControlSession(private val output: OutputStream, private val executor: Executor) : StopChannel {
    private val pending = ConcurrentHashMap<String, (ControlReply) -> Unit>()
    private val biasIds = AtomicInteger()
    private val markIds = AtomicInteger()
    private val stopIds = AtomicInteger()

    @Volatile private var closed = false
    @Volatile private var stopRequested = false

    fun sendBias(amount: Int, onReply: (ControlReply) -> Unit = {}) {
        if (!accepting(onReply)) return
        enqueue(ControlCommand.Bias("b_${biasIds.incrementAndGet()}", amount), onReply)
    }

    fun sendMark(label: String, onReply: (ControlReply) -> Unit = {}) {
        if (!accepting(onReply)) return
        enqueue(ControlCommand.Mark("m_${markIds.incrementAndGet()}", label), onReply)
    }

    override fun sendStop(onReply: (ControlReply) -> Unit) {
        if (closed) return onReply(ControlReply.Failed("control channel closed"))
        stopRequested = true
        enqueue(ControlCommand.Stop("s_${stopIds.incrementAndGet()}"), onReply)
    }

    /** Feeds one parsed stdout event; replies to a pending command complete it. */
    fun onEvent(payload: HeartbeatPayload) {
        val id = payload.id ?: return
        val reply = when {
            payload.event == "ack" -> ControlReply.Ack(payload)
            payload.event == "mark" -> ControlReply.MarkRecorded(payload)
            payload.isCommandError -> ControlReply.Rejected(payload)
            else -> return
        }
        pending.remove(id)?.invoke(reply)
    }

    /** Fails everything still waiting and refuses further commands. */
    fun close() {
        closed = true
        pending.keys.toList().forEach { fail(it, "control channel closed") }
    }

    private fun accepting(onReply: (ControlReply) -> Unit): Boolean {
        val refusal = when {
            closed -> "control channel closed"
            stopRequested -> "stop already requested"
            else -> return true
        }
        onReply(ControlReply.Failed(refusal))
        return false
    }

    private fun enqueue(command: ControlCommand, onReply: (ControlReply) -> Unit) {
        pending[command.id] = onReply
        try {
            executor.execute { write(command) }
        } catch (e: RejectedExecutionException) {
            fail(command.id, "control channel closed")
        }
    }

    private fun write(command: ControlCommand) {
        if (closed) return fail(command.id, "control channel closed")
        try {
            output.write("${command.toLine()}\n".toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (e: IOException) {
            closed = true
            fail(command.id, e.message ?: "write failed")
        }
    }

    private fun fail(id: String, reason: String) {
        pending.remove(id)?.invoke(ControlReply.Failed(reason))
    }
}
