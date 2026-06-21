package dev.gopherglide.ggplugin.snap

import com.google.gson.annotations.SerializedName

data class PruneReport(
    @SerializedName("dry_run")
    val dryRun: Boolean = false,
    @SerializedName("snap_dir")
    val snapDir: String = "",
    val candidates: List<PruneCandidate> = emptyList(),
    val deleted: Int = 0,
    val errors: List<String> = emptyList()
)

data class PruneCandidate(
    val id: Int = 0,
    val tag: String = "",
    val date: String = "",
    val file: String = "",
    val reason: String = ""
)
