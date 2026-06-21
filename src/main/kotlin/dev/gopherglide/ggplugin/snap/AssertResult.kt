package dev.gopherglide.ggplugin.snap

import com.google.gson.annotations.SerializedName

data class AssertResult(
    val passed: Boolean = false,
    val violations: List<AssertViolation> = emptyList(),
    @SerializedName("diff")
    val diff: DiffResult? = null
)

data class AssertViolation(
    @SerializedName("endpoint_id")
    val endpointId: String = "",
    val verdict: String = "",
    val message: String = ""
)

data class DiffResult(
    val baseline: SnapMeta? = null,
    val current: SnapMeta? = null,
    val endpoints: List<EndpointDiff> = emptyList()
)

data class EndpointDiff(
    val id: String = "",
    val verdict: String = "",
    @SerializedName("baseline_only")
    val baselineOnly: Boolean = false,
    @SerializedName("current_only")
    val currentOnly: Boolean = false
)
