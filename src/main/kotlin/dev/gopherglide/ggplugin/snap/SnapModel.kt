package dev.gopherglide.ggplugin.snap

import com.google.gson.annotations.SerializedName

data class SnapModel(
    var id: String = "", // Populated manually from filename
    var internalIndex: String = "", // Numeric index used by CLI
    val version: Int = 0,
    val meta: SnapMeta? = null,
    val endpoints: List<EndpointSnap> = emptyList()
)

data class SnapMeta(
    val tag: String = "",
    @SerializedName("start_time")
    val startTime: String = "",
    @SerializedName("end_time")
    val endTime: String = "",
    @SerializedName("peak_rps")
    val peakRps: Double = 0.0,
    @SerializedName("total_requests")
    val totalRequests: Long = 0,
    @SerializedName("config_hash")
    val configHash: String = "",
    @SerializedName("snap_settings")
    val snapSettings: SnapSettings? = null,
    @SerializedName("profile_name")
    val profile: String? = null,
    @SerializedName("final_bias")
    val finalBias: Int? = null,
    val marks: List<SnapMark>? = null,
    @SerializedName("bias_events")
    val biasEvents: List<SnapBiasEvent>? = null,
    @SerializedName("hold_events")
    val holdEvents: List<SnapHoldEvent>? = null,
    /** Who started the run ("user" or "agent"); absent when gg does not say. */
    val origin: String? = null,
    val autopilot: SnapAutopilot? = null
)

/** [source] is "user", "agent" or "autopilot"; absent means the user. */
data class SnapMark(
    val label: String = "",
    @SerializedName("elapsed_s")
    val elapsedSeconds: Double = 0.0,
    val source: String? = null
)

data class SnapBiasEvent(
    val amount: Int = 0,
    val cumulative: Int = 0,
    @SerializedName("elapsed_s")
    val elapsedSeconds: Double = 0.0,
    val source: String? = null
)

/** A hold pinning the rate at [rps], or the release of one when [released] is true. */
data class SnapHoldEvent(
    val rps: Int? = null,
    val released: Boolean? = null,
    @SerializedName("elapsed_s")
    val elapsedSeconds: Double = 0.0,
    val source: String? = null
)

/** What the built-in autopilot did during the run. */
data class SnapAutopilot(
    val mode: String = "",
    val outcome: String? = null,
    @SerializedName("ceiling_rps")
    val ceilingRps: Int? = null,
    val params: SnapAutopilotParams? = null,
    val events: List<SnapAutopilotEvent>? = null
)

data class SnapAutopilotParams(
    @SerializedName("max_p99_ms")
    val maxP99Ms: Double? = null,
    @SerializedName("max_error_rate")
    val maxErrorRate: Double? = null
)

/** [elapsedSeconds] counts from the autopilot's own start, not the run's. */
data class SnapAutopilotEvent(
    val state: String = "",
    val reason: String? = null,
    @SerializedName("hold_rps")
    val holdRps: Int? = null,
    val outcome: String? = null,
    @SerializedName("elapsed_s")
    val elapsedSeconds: Double = 0.0
)

data class SnapSettings(
    @SerializedName("sample_rate")
    val sampleRate: Double = 0.0,
    @SerializedName("max_samples")
    val maxSamples: Int = 0,
    @SerializedName("max_body_kb")
    val maxBodyKb: Int = 0
)

data class EndpointSnap(
    val id: String = "",
    @SerializedName("status_dist")
    val statusDist: Map<String, Double> = emptyMap(),
    val latency: LatencyStats = LatencyStats(),
    @SerializedName("payload_size")
    val payloadSize: PayloadSizeStats = PayloadSizeStats(),
    @SerializedName("error_rate")
    val errorRate: Double = 0.0,
    @SerializedName("request_count")
    val requestCount: Long = 0,
    @SerializedName("body_samples_observed")
    val bodySamplesObserved: Long = 0,
    @SerializedName("body_samples_stored")
    val bodySamplesStored: Long = 0,
    val schema: SchemaSnapshot? = null
)

data class LatencyStats(
    val p50: Double = 0.0,
    val p95: Double = 0.0,
    val p99: Double = 0.0,
    val max: Double = 0.0
)

data class PayloadSizeStats(
    val avg: Double = 0.0,
    val p95: Double = 0.0,
    val max: Double = 0.0
)

data class SchemaSnapshot(
    @SerializedName("sample_count")
    val sampleCount: Int = 0,
    val fields: Map<String, FieldSchema> = emptyMap()
)

data class FieldSchema(
    val type: String = "",
    val presence: Double = 0.0,
    val stability: String = ""
)
