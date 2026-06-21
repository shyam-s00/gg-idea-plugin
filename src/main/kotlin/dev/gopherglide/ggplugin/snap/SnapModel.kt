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
    val snapSettings: SnapSettings? = null
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
