package dev.gopherglide.ggplugin.snap

import com.google.gson.annotations.SerializedName

data class SnapModel(
    var id: String = "", // Populated manually from filename
    var internalIndex: String = "", // Numeric index used by CLI
    val version: Int = 0,
    val meta: SnapMeta? = null
)

data class SnapMeta(
    val tag: String = "",
    @SerializedName("start_time")
    val startTime: String = "",
    @SerializedName("total_requests")
    val totalRequests: Long = 0,
    @SerializedName("peak_rps")
    val peakRps: Double = 0.0
)
