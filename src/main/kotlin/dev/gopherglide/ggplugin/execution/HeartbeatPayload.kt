package dev.gopherglide.ggplugin.execution

import com.google.gson.annotations.SerializedName

/** A single load stage exactly as `gg` resolved it internally, regardless of whether the run came from a `.gg.yaml` `stages:` block or a `--profile` run. Only present on the "started" event, on `gg` v1.1.0+. */
data class StageInfo(
    val name: String = "",
    @SerializedName("duration_seconds")
    val durationSeconds: Double = 0.0,
    @SerializedName("target_rps")
    val targetRps: Int = 0
)

/** The autopilot as a heartbeat reports it while it is running; absent once it is done. */
data class AutopilotBrief(
    val mode: String = "",
    val state: String = ""
)

/** Health over a short recent window, unlike the cumulative figures. From `gg` v1.5.0. */
data class WindowHealthPayload(
    val seconds: Int = 0,
    @SerializedName("p99_ms")
    val p99Ms: Double = 0.0,
    @SerializedName("error_rate")
    val errorRate: Double = 0.0,
    val samples: Int = 0,
    /** True when the sample cap truncated the window. */
    val capped: Boolean = false
)

data class ErrorCode(
    val code: Int = 0,
    val n: Int = 0
)

data class ErrorEndpoint(
    val endpoint: String = "",
    val n: Int = 0,
    val code: Int = 0,
    /** "http_5xx" | "http_4xx" | "transport" | "extract" */
    val kind: String = ""
)

/** What failed over the last [windowSeconds]. From `gg` v1.5.0; absent while nothing has failed. */
data class ErrorMixPayload(
    @SerializedName("window_s")
    val windowSeconds: Int = 0,
    val sampled: Int = 0,
    val transport: Int = 0,
    @SerializedName("http_4xx")
    val http4xx: Int = 0,
    @SerializedName("http_5xx")
    val http5xx: Int = 0,
    val extract: Int = 0,
    @SerializedName("top_codes")
    val topCodes: List<ErrorCode> = emptyList(),
    val capped: Boolean = false,
    @SerializedName("top_endpoints")
    val topEndpoints: List<ErrorEndpoint> = emptyList()
)

/** Mirrors the JSON heartbeat shape emitted by `gg --headless --reporter json`. */
data class HeartbeatPayload(
    val time: String = "",
    val event: String = "", // "heartbeat" | "started" | "finished" | "stopped" | "snap" | "interrupted" | "error" | "ack" | "mark" | "autopilot"
    val stage: Int = 0,
    @SerializedName("total_stages")
    val totalStages: Int = 0,
    /** Full stage breakdown, set only on the "started" event. Null on a pre-v1.1.0 `gg` binary. */
    val stages: List<StageInfo>? = null,
    /** Resolved profile name, set only on "started" for `--profile` runs. Null otherwise. */
    val profile: String? = null,
    @SerializedName("profile_scale")
    val profileScale: Double? = null,
    @SerializedName("target_rps")
    val targetRps: Int = 0,
    @SerializedName("actual_rps")
    val actualRps: Double = 0.0,
    @SerializedName("total_requests")
    val totalRequests: Long = 0,
    @SerializedName("success_count")
    val successCount: Long = 0,
    @SerializedName("failure_count")
    val failureCount: Long = 0,
    @SerializedName("error_rate")
    val errorRate: Double = 0.0,
    @SerializedName("p50_ms")
    val p50Ms: Double = 0.0,
    @SerializedName("p95_ms")
    val p95Ms: Double = 0.0,
    @SerializedName("p99_ms")
    val p99Ms: Double = 0.0,
    val message: String = "",
    /** Set only on "started", on `gg` v1.3.0+. Null on older binaries. */
    @SerializedName("protocol_version")
    val protocolVersion: Int? = null,
    /** Null on a pre-v1.3 binary; present but empty when live control is disabled for the run. */
    val capabilities: List<String>? = null,
    /** Cumulative bias: set on "heartbeat" and bias "ack". Null when absent, so a real 0 is distinguishable. */
    val bias: Int? = null,
    /** Correlation fields on "ack", "mark" and command "error" replies. */
    val id: String? = null,
    val command: String? = null,
    val label: String? = null,
    @SerializedName("elapsed_s")
    val elapsedSeconds: Double? = null,
    /** Set only on a command "error" reply; a fatal run "error" has none. */
    val reason: String? = null,
    /** True on a "mark" whose requested time was moved into the run's span. */
    val clamped: Boolean? = null,
    /** "user" | "agent" | "autopilot"; null when gg does not say. */
    val source: String? = null,
    /** The pinned rate; null when nothing is pinned. */
    @SerializedName("hold_rps")
    val holdRps: Int? = null,
    /** Nested on heartbeats while an autopilot runs; other events use the top-level [mode] and [state]. */
    val autopilot: AutopilotBrief? = null,
    val mode: String? = null,
    val state: String? = null,
    val outcome: String? = null,
    @SerializedName("ceiling_rps")
    val ceilingRps: Int? = null,
    val window: WindowHealthPayload? = null,
    @SerializedName("error_mix")
    val errorMix: ErrorMixPayload? = null
) {
    /** True for an "error" reply to a control command, which does not end the run. */
    val isCommandError: Boolean get() = event == "error" && !reason.isNullOrEmpty()

    /** True when this event means the process is done and no further heartbeats will follow. */
    val isTerminal: Boolean
        get() = when (event) {
            "finished", "stopped", "interrupted" -> true
            "error" -> !isCommandError
            else -> false
        }
}
