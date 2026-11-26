package dev.gopherglide.ggplugin.execution.autopilot

import dev.gopherglide.ggplugin.execution.autopilot.AutopilotArgs.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AutopilotArgsTest {
    @Test
    fun errorPercentIsSentAsAFraction() {
        assertEquals("0.01", AutopilotArgs.parseErrorFraction("1.0")!!.toPlainString())
        assertEquals("0.01", AutopilotArgs.parseErrorFraction("1")!!.toPlainString())
        assertEquals("0.005", AutopilotArgs.parseErrorFraction("0.5")!!.toPlainString())
        assertEquals("0.125", AutopilotArgs.parseErrorFraction(" 12.5 ")!!.toPlainString())
        assertEquals("1", AutopilotArgs.parseErrorFraction("100")!!.toPlainString())
    }

    @Test
    fun errorPercentOutsideZeroToHundredIsRejected() {
        listOf("0", "0.0", "-1", "100.1", "abc", "", "1e2", "1%", "1,5").forEach {
            assertNull(it, AutopilotArgs.parseErrorFraction(it))
        }
    }

    @Test
    fun latencyAcceptsBareMillisecondsOrAUnit() {
        assertEquals(250, AutopilotArgs.parseLatencyMillis("250"))
        assertEquals(250, AutopilotArgs.parseLatencyMillis("250ms"))
        assertEquals(1000, AutopilotArgs.parseLatencyMillis("1s"))
        assertEquals(500, AutopilotArgs.parseLatencyMillis(" 0.5s "))
    }

    @Test
    fun latencyMustBeAtLeastOneMillisecond() {
        listOf("0", "0ms", "0.4ms", "-5", "fast", "", "5m", "1 s").forEach {
            assertNull(it, AutopilotArgs.parseLatencyMillis(it))
        }
    }

    @Test
    fun durationsFollowGoSyntax() {
        assertEquals(90.0, AutopilotArgs.parseDurationSeconds("90s")!!, 0.0)
        assertEquals(300.0, AutopilotArgs.parseDurationSeconds("5m")!!, 0.0)
        assertEquals(5400.0, AutopilotArgs.parseDurationSeconds("1h30m")!!, 0.0)
        assertEquals(150.0, AutopilotArgs.parseDurationSeconds(" 2m30s ")!!, 0.0)
        assertEquals(0.5, AutopilotArgs.parseDurationSeconds("500ms")!!, 0.0)
    }

    @Test
    fun malformedOrZeroDurationsAreRejected() {
        listOf("", "5", "m", "5x", "0s", "-5s", "5m 30s", "1.m", "abc").forEach {
            assertNull(it, AutopilotArgs.parseDurationSeconds(it))
        }
    }

    @Test
    fun sloGuardFlagsUseGgsUnits() {
        val limits = SloLimits(200, BigDecimal("0.02"))
        assertEquals(
            listOf("--autopilot", "slo-guard", "--max-p99", "200ms", "--max-error-rate", "0.02"),
            AutopilotArgs.sloGuard(limits)
        )
    }

    @Test
    fun findCeilingFlagsCarryNoProfile() {
        val args = AutopilotArgs.findCeiling(20, 1000, SloLimits(250, BigDecimal("0.01")), " 5m ")
        assertEquals(
            listOf(
                "--autopilot", "find-ceiling", "--start-rps", "20", "--max-rps", "1000", "--duration", "5m",
                "--max-p99", "250ms", "--max-error-rate", "0.01"
            ),
            args
        )
    }

    @Test
    fun limitsReportTheFirstBadField() {
        assertNull(AutopilotArgs.checkLimits("250ms", "1.0"))
        assertEquals(Field.P99, AutopilotArgs.checkLimits("fast", "1.0")!!.field)
        assertEquals(Field.ERROR_RATE, AutopilotArgs.checkLimits("250ms", "0")!!.field)
    }

    private fun ceiling(
        start: String = "20", max: String = "1000", p99: String = "250ms", error: String = "1.0", duration: String = "5m"
    ) = AutopilotArgs.checkFindCeiling(start, max, p99, error, duration)

    @Test
    fun aFindCeilingFormWithDefaultsPasses() {
        assertNull(ceiling())
    }

    @Test
    fun findCeilingNeedsAtLeastThirtySeconds() {
        assertEquals(Field.DURATION, ceiling(duration = "29s")!!.field)
        assertNull(ceiling(duration = "30s"))
        assertEquals(Field.DURATION, ceiling(duration = "10s")!!.field)
    }

    @Test
    fun findCeilingRatesMustBeOrdered() {
        assertEquals(Field.START_RPS, ceiling(start = "0")!!.field)
        assertEquals(Field.START_RPS, ceiling(start = "x")!!.field)
        assertEquals(Field.MAX_RPS, ceiling(start = "50", max = "20")!!.field)
        assertEquals(Field.MAX_RPS, ceiling(max = "lots")!!.field)
        assertNull(ceiling(start = "50", max = "50"))
    }

    @Test
    fun findCeilingFormChecksTheLimitsAndTheDurationFormat() {
        assertEquals(Field.P99, ceiling(p99 = "0")!!.field)
        assertEquals(Field.ERROR_RATE, ceiling(error = "101")!!.field)
        assertEquals(Field.DURATION, ceiling(duration = "soon")!!.field)
    }
}
