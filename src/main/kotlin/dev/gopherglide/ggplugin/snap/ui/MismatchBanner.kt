package dev.gopherglide.ggplugin.snap.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import dev.gopherglide.ggplugin.snap.SnapModel
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel

/**
 * Yellow warning shown when the compared snapshots used different profiles (or different config
 * hashes, for legacy snapshots without a profile), since the comparison may not be like for like.
 */
class MismatchBanner private constructor(message: String, icon: Icon) : JPanel(BorderLayout(JBUI.scale(8), 0)) {

    init {
        background = JBColor(0xFFF8DC, 0x5C4A1A)
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor(0xE8D48A, 0x8A6D1F), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 12)
        )
        add(JBLabel(icon), BorderLayout.WEST)
        add(JBLabel("<html>$message</html>"), BorderLayout.CENTER)
    }

    companion object {
        /** The same banner with an information icon, for notices that are not mismatches. */
        fun notice(message: String) = MismatchBanner(message, AllIcons.General.Information)

        /** Returns null (no banner) when the two snapshots' configuration is comparable. */
        fun forSnaps(snapA: SnapModel, snapB: SnapModel): MismatchBanner? {
            val profileA = snapA.meta?.profile?.ifBlank { null }
            val profileB = snapB.meta?.profile?.ifBlank { null }

            val message = if (profileA != null && profileB != null) {
                if (profileA != profileB) {
                    "<b>Profile mismatch:</b> baseline used profile \"$profileA\", target used \"$profileB\". " +
                        "Re-running the baseline with the target's profile is recommended."
                } else {
                    null
                }
            } else {
                // One or both snapshots predate the profile field — fall back to config hash.
                val hashA = snapA.meta?.configHash?.ifBlank { null }
                val hashB = snapB.meta?.configHash?.ifBlank { null }
                if (hashA != null && hashB != null && hashA != hashB) {
                    "<b>Configuration mismatch:</b> snapshot configurations do not match and may lead to " +
                        "inconsistent results. Re-running the baseline with the target's configuration is recommended."
                } else {
                    null
                }
            }

            return message?.let { MismatchBanner(it, AllIcons.General.Warning) }
        }
    }
}
