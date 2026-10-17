package dev.gopherglide.ggplugin.snap.ui

import dev.gopherglide.ggplugin.snap.ManualChanges
import dev.gopherglide.ggplugin.snap.SnapModel

object ManualChangesBanner {
    /** Null when neither snapshot recorded marks or bias changes. */
    fun forSnaps(baseline: SnapModel, target: SnapModel): MismatchBanner? {
        val notice = ManualChanges.describe(baseline.meta, target.meta) ?: return null
        val caveat = notice.caveat?.let { "<br>$it" }.orEmpty()
        return MismatchBanner.notice("<b>Manual changes during the run:</b> ${notice.summary}$caveat")
    }
}
