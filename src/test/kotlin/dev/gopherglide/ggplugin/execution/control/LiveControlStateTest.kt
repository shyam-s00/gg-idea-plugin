package dev.gopherglide.ggplugin.execution.control

import dev.gopherglide.ggplugin.execution.HeartbeatPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveControlStateTest {
    private val all = listOf("control.bias", "control.mark", "control.stop")

    private fun ack(bias: Int?) = ControlReply.Ack(HeartbeatPayload(event = "ack", bias = bias))

    private fun active(caps: List<String> = all) = LiveControlState().also { it.onStarted(caps) }

    @Test
    fun nothingIsUsableBeforeTheHandshake() {
        val state = LiveControlState()
        assertEquals(LiveControlState.Handshake.PENDING, state.handshake)
        assertFalse(state.canBias)
        assertFalse(state.canMark)
        assertFalse(state.canReset)
    }

    @Test
    fun missingCapabilitiesMeansAnOlderBinary() {
        val state = LiveControlState().also { it.onStarted(null) }
        assertEquals(LiveControlState.Handshake.LEGACY, state.handshake)
        assertFalse(state.canBias)
        assertFalse(state.canMark)
    }

    @Test
    fun emptyCapabilitiesMeansControlIsOff() {
        val state = LiveControlState().also { it.onStarted(emptyList()) }
        assertEquals(LiveControlState.Handshake.NO_CONTROL, state.handshake)
        assertFalse(state.canBias)
    }

    @Test
    fun fullCapabilitiesEnableTheControls() {
        val state = active()
        assertEquals(LiveControlState.Handshake.ACTIVE, state.handshake)
        assertTrue(state.canBias)
        assertTrue(state.canMark)
    }

    @Test
    fun eachControlFollowsItsOwnCapability() {
        assertTrue(active(listOf("control.bias")).canBias)
        assertFalse(active(listOf("control.bias")).canMark)
        assertTrue(active(listOf("control.mark")).canMark)
        assertFalse(active(listOf("control.mark")).canBias)
    }

    @Test
    fun unknownCapabilitiesAreIgnored() {
        val state = active(listOf("control.bias", "control.teleport"))
        assertTrue(state.canBias)
        assertFalse(state.canMark)
    }

    @Test
    fun stopRequestAndEndDisableTheControls() {
        val stopping = active().also { it.onStopRequested() }
        assertFalse(stopping.canBias)
        assertFalse(stopping.canMark)
        val ended = active().also { it.onEnded() }
        assertFalse(ended.canBias)
    }

    @Test
    fun pendingDeltaShowsAtOnceAndAckReplacesItWithGgsTotal() {
        val state = active()
        val token = state.addPending(10)
        assertEquals(10, state.displayedBias)
        state.resolve(token, ack(10))
        assertEquals(10, state.displayedBias)
    }

    @Test
    fun rapidClicksConvergeToTheLastAckedTotal() {
        val state = active()
        val first = state.addPending(5)
        val second = state.addPending(5)
        val third = state.addPending(-5)
        assertEquals(5, state.displayedBias)
        state.resolve(first, ack(5))
        assertEquals(5, state.displayedBias)
        state.resolve(second, ack(10))
        assertEquals(5, state.displayedBias)
        state.resolve(third, ack(5))
        assertEquals(5, state.displayedBias)
    }

    @Test
    fun heartbeatBiasCorrectsDriftWhileCommandsAreStillPending() {
        val state = active()
        state.addPending(5)
        state.onHeartbeatBias(20)
        assertEquals(25, state.displayedBias)
    }

    @Test
    fun rejectedOrFailedCommandDropsItsDelta() {
        val state = active()
        val rejected = state.addPending(10)
        state.resolve(rejected, ControlReply.Rejected(HeartbeatPayload(event = "error", reason = "invalid_argument")))
        assertEquals(0, state.displayedBias)
        val failed = state.addPending(-5)
        state.resolve(failed, ControlReply.Failed("Broken pipe"))
        assertEquals(0, state.displayedBias)
    }

    @Test
    fun aReplyForAnUnknownTokenIsIgnored() {
        val state = active()
        state.resolve(99, ack(50))
        assertEquals(0, state.displayedBias)
    }

    @Test
    fun resetIsUnavailableAtZero() {
        val state = active()
        assertFalse(state.canReset)
        assertNull(state.resetAmount())
    }

    @Test
    fun resetAmountCancelsTheShownBias() {
        val state = active()
        state.onHeartbeatBias(15)
        assertEquals(-15, state.resetAmount())
        state.onHeartbeatBias(-7)
        assertEquals(7, state.resetAmount())
    }

    @Test
    fun resetAmountCountsDeltasStillInFlight() {
        val state = active()
        state.onHeartbeatBias(10)
        state.addPending(5)
        assertEquals(-15, state.resetAmount())
    }

    @Test
    fun resetCannotBeIssuedTwiceBeforeTheFirstIsAcknowledged() {
        val state = active()
        state.onHeartbeatBias(10)
        state.addPending(state.resetAmount()!!)
        assertEquals(0, state.displayedBias)
        assertFalse(state.canReset)
        assertNull(state.resetAmount())
    }

    @Test
    fun resetIsUnavailableOnceStopIsRequested() {
        val state = active()
        state.onHeartbeatBias(10)
        state.onStopRequested()
        assertNull(state.resetAmount())
    }
}
