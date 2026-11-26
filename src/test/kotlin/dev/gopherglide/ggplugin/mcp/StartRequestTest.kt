package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonParser
import dev.gopherglide.ggplugin.mcp.StartRequest.Routable
import dev.gopherglide.ggplugin.mcp.StartRequest.Unroutable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartRequestTest {
    private fun body(vararg args: String) = (StartRequest.fromArgs(args.toList()) as Routable).body

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun aProfileRunNamesTheFileProfileAndOverrides() {
        assertEquals(
            json("""{"http_path":"/p/a.http","profile":"smoke","peak_rps":50,"duration":"2m","snap":true,"snap_tag":"v2"}"""),
            body("--profile", "smoke", "--http-file", "/p/a.http", "--peak-rps", "50", "--duration", "2m", "--snap", "--snap-tag", "v2")
        )
    }

    @Test
    fun aPlainProfileRunHasNoOptionalFields() {
        assertEquals(json("""{"http_path":"/p/a.http","profile":"load"}"""), body("--profile", "load", "--http-file", "/p/a.http"))
    }

    @Test
    fun aConfigRunSendsTheConfigPathAndKeepsItsSnapshotOptions() {
        assertEquals(
            json("""{"config_path":"/p/x.gg.yaml","snap":true,"snap_tag":"nightly"}"""),
            body("/p/x.gg.yaml", "--snap", "--snap-tag", "nightly")
        )
    }

    @Test
    fun aGuardedProfileRunCarriesTheLimitsInTheDaemonsUnits() {
        val request = body(
            "--profile", "spike", "--http-file", "/p/a.http", "--autopilot", "slo-guard", "--max-p99", "200ms", "--max-error-rate", "0.02"
        )
        assertEquals(json("""{"mode":"slo-guard","max_p99_ms":200,"max_error_rate":0.02}"""), request["autopilot"])
        assertEquals("spike", request["profile"].asString)
    }

    @Test
    fun findCeilingSendsNoProfileAndTurnsTheDurationIntoItsTimeLimit() {
        val request = body(
            "--http-file", "/p/a.http", "--autopilot", "find-ceiling", "--start-rps", "20", "--max-rps", "500",
            "--duration", "5m", "--max-p99", "250ms", "--max-error-rate", "0.01", "--snap"
        )
        assertEquals(
            json("""{"http_path":"/p/a.http","autopilot":{"mode":"find-ceiling","max_p99_ms":250,"max_error_rate":0.01,"start_rps":20,"max_rps":500,"max_duration":"5m"},"snap":true}"""),
            request
        )
        assertTrue(!request.has("profile") && !request.has("duration"))
    }

    @Test
    fun shortFlagsAreUnderstood() {
        assertEquals(json("""{"http_path":"/a.http","profile":"smoke","peak_rps":5,"duration":"9s"}"""), body("-p", "smoke", "--http-file", "/a.http", "-r", "5", "-d", "9s"))
    }

    private fun reason(vararg args: String) = (StartRequest.fromArgs(args.toList()) as Unroutable).reason

    @Test
    fun aFlagTheDaemonCannotExpressSendsTheRunDirect() {
        assertTrue(reason("--profile", "smoke", "--http-file", "/a.http", "--snap-dir", "/x").contains("--snap-dir"))
        assertTrue(reason("--profile", "smoke", "--http-file", "/a.http", "--insecure").contains("--insecure"))
    }

    @Test
    fun anIncompleteRunIsNotRoutable() {
        assertTrue(reason("--profile", "smoke").isNotEmpty())
        assertTrue(reason("--http-file", "/a.http").isNotEmpty())
        assertTrue(reason().isNotEmpty())
        assertTrue(reason("--profile").contains("no value"))
    }

    @Test
    fun findCeilingCannotBeCombinedWithAScheduleOrAConfig() {
        assertTrue(reason("--http-file", "/a.http", "--autopilot", "find-ceiling", "--profile", "smoke").contains("schedule"))
        assertTrue(reason("/p/x.gg.yaml", "--autopilot", "find-ceiling").contains("schedule"))
    }

    @Test
    fun malformedNumbersAreNotRoutable() {
        assertTrue(reason("--profile", "smoke", "--http-file", "/a.http", "--peak-rps", "lots").isNotEmpty())
        assertTrue(reason("--profile", "smoke", "--http-file", "/a.http", "--autopilot", "slo-guard", "--max-p99", "fast").isNotEmpty())
    }

    @Test
    fun aStrayPositionalArgumentIsNotRoutable() {
        assertTrue(reason("--profile", "smoke", "--http-file", "/a.http", "extra").contains("extra"))
    }
}
