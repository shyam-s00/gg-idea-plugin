package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopLadderTest {
    private class FakeChannel : StopChannel {
        var sends = 0
        var reply: ((ControlReply) -> Unit)? = null
        override fun sendStop(onReply: (ControlReply) -> Unit) { sends++; reply = onReply }
    }

    private class FakeKiller : ProcessKiller {
        var soft = 0
        var hard = 0
        override fun softKill() { soft++ }
        override fun hardKill() { hard++ }
    }

    private class FakeScheduler : Scheduler {
        var now = 0L
        private class Task(val due: Long, val run: Runnable, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        val cancelledCount get() = tasks.count { it.cancelled }
        override fun schedule(delayMs: Long, task: Runnable): () -> Unit {
            val t = Task(now + delayMs, task)
            tasks += t
            return { t.cancelled = true }
        }
        fun advance(ms: Long) {
            now += ms
            tasks.filter { !it.cancelled && it.due <= now }.forEach { it.run.run(); it.cancelled = true }
        }
    }

    private class Rig(capabilities: List<String>? = listOf("control.bias", "control.mark", "control.stop"), started: Boolean = true) {
        val channel = FakeChannel()
        val killer = FakeKiller()
        val scheduler = FakeScheduler()
        val phases = mutableListOf<StopPhase>()
        val ladder = StopLadder(channel, killer, scheduler, { scheduler.now }, { phases += it })

        init { if (started) ladder.onStarted(capabilities) }

        fun ack() = channel.reply!!.invoke(ControlReply.Ack(HeartbeatPayload(event = "ack")))
    }

    @Test
    fun cleanStopWaitsForeverOnceAcknowledged() {
        val rig = Rig()
        rig.ladder.requestStop()
        assertEquals(StopPhase.AWAITING_ACK, rig.ladder.phase)
        assertEquals(1, rig.channel.sends)
        rig.ack()
        assertEquals(StopPhase.FINALIZING, rig.ladder.phase)
        rig.scheduler.advance(10 * 60_000)
        assertEquals(0, rig.killer.soft)
        assertEquals(0, rig.killer.hard)
        assertEquals(listOf(StopPhase.AWAITING_ACK, StopPhase.FINALIZING), rig.phases)
    }

    @Test
    fun acknowledgementCancelsTheAckTimer() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.ack()
        assertEquals(1, rig.scheduler.cancelledCount)
    }

    @Test
    fun missingAckFallsBackToSoftKillAfterTheTimeout() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.scheduler.advance(StopLadder.ACK_TIMEOUT_MS - 1)
        assertEquals(0, rig.killer.soft)
        rig.scheduler.advance(1)
        assertEquals(1, rig.killer.soft)
        assertEquals(StopPhase.NO_ACK_INTERRUPTING, rig.ladder.phase)
        assertEquals(0, rig.killer.hard)
    }

    @Test
    fun failedStopWriteFallsBackToSoftKillAtOnce() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.channel.reply!!.invoke(ControlReply.Failed("Broken pipe"))
        assertEquals(1, rig.killer.soft)
        assertEquals(StopPhase.NO_ACK_INTERRUPTING, rig.ladder.phase)
        rig.scheduler.advance(60_000)
        assertEquals(1, rig.killer.soft)
    }

    @Test
    fun anImmediatelyFailingChannelStillEndsInASoftKill() {
        val rig = Rig()
        val failing = object : StopChannel {
            override fun sendStop(onReply: (ControlReply) -> Unit) = onReply(ControlReply.Failed("control channel closed"))
        }
        val ladder = StopLadder(failing, rig.killer, rig.scheduler, { rig.scheduler.now }, { })
        ladder.onStarted(listOf("control.stop"))
        ladder.requestStop()
        assertEquals(1, rig.killer.soft)
        assertEquals(StopPhase.NO_ACK_INTERRUPTING, ladder.phase)
    }

    @Test
    fun olderBinaryWithoutCapabilitiesSoftKillsDirectly() {
        val rig = Rig(capabilities = null)
        rig.ladder.requestStop()
        assertEquals(0, rig.channel.sends)
        assertEquals(1, rig.killer.soft)
        assertEquals(StopPhase.INTERRUPTING, rig.ladder.phase)
    }

    @Test
    fun emptyCapabilitiesSoftKillDirectly() {
        val rig = Rig(capabilities = emptyList())
        rig.ladder.requestStop()
        assertEquals(0, rig.channel.sends)
        assertEquals(1, rig.killer.soft)
    }

    @Test
    fun stopBeforeTheStartedEventSoftKillsDirectly() {
        val rig = Rig(started = false)
        rig.ladder.requestStop()
        assertEquals(0, rig.channel.sends)
        assertEquals(1, rig.killer.soft)
    }

    @Test
    fun secondRequestAfterTheGuardHardKills() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.ack()
        rig.scheduler.now += StopLadder.FORCE_GUARD_MS
        rig.ladder.requestStop()
        assertEquals(1, rig.killer.hard)
        assertEquals(0, rig.killer.soft)
    }

    @Test
    fun secondRequestInsideTheGuardIsIgnoredSoADoubleClickCannotForceKill() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.scheduler.now += StopLadder.FORCE_GUARD_MS - 1
        rig.ladder.requestStop()
        assertEquals(0, rig.killer.hard)
        assertEquals(1, rig.channel.sends)
    }

    @Test
    fun forceStopWorksWhileStillWaitingForTheAck() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.scheduler.now += StopLadder.FORCE_GUARD_MS
        rig.ladder.requestStop()
        assertEquals(1, rig.killer.hard)
    }

    @Test
    fun processEndingBeforeTheTimeoutCancelsEverything() {
        val rig = Rig()
        rig.ladder.requestStop()
        rig.ladder.onTerminated()
        rig.scheduler.advance(60_000)
        rig.ack()
        assertEquals(0, rig.killer.soft)
        assertEquals(0, rig.killer.hard)
        assertEquals(listOf(StopPhase.AWAITING_ACK), rig.phases)
    }

    @Test
    fun requestsAfterTerminationDoNothing() {
        val rig = Rig()
        rig.ladder.onTerminated()
        rig.ladder.requestStop()
        assertEquals(0, rig.channel.sends)
        assertEquals(0, rig.killer.soft)
        assertTrue(rig.phases.isEmpty())
    }
}
