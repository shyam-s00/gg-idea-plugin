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

/** Mirrors the JSON heartbeat shape emitted by `gg --headless --reporter json`. */
data class HeartbeatPayload(
    val time: String = "",
    val event: String = "", // "heartbeat" | "started" | "finished" | "snap" | "interrupted" | "error"
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
    val message: String = ""
)
