package dev.gopherglide.ggplugin.execution

import com.google.gson.annotations.SerializedName

/** Mirrors the JSON heartbeat shape emitted by `gg --headless --reporter json`. */
data class HeartbeatPayload(
    val time: String = "",
    val event: String = "", // "heartbeat" | "started" | "finished" | "snap" | "interrupted" | "error"
    val stage: Int = 0,
    @SerializedName("total_stages")
    val totalStages: Int = 0,
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
