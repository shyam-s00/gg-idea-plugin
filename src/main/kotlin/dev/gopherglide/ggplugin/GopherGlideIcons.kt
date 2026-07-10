package dev.gopherglide.ggplugin

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GopherGlideIcons {
    // plugin.xml references this by reflection (icon="...GopherGlideIcons.SidebarIcon"), which requires
    // an actual field — a Kotlin property with a custom getter compiles to a method, not a field, and
    // silently fails to resolve. @JvmField + eager init matches how AllIcons.* itself is declared.
    @JvmField
    val SidebarIcon: Icon = IconLoader.getIcon("/icons/ggToolIcon.svg", GopherGlideIcons::class.java)

    // Schema field-type icons for the Snap View's schema tree.
    val SchemaString: Icon
        get() = IconLoader.getIcon("/icons/expui/string.svg", GopherGlideIcons::class.java)
    val SchemaNumber: Icon
        get() = IconLoader.getIcon("/icons/expui/sequence.svg", GopherGlideIcons::class.java)
}
