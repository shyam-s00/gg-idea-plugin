package dev.gopherglide.ggplugin

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object GopherGlideIcons {
    val SidebarIcon: Icon
        get() = IconLoader.getIcon("/icons/ggToolIcon.svg", GopherGlideIcons::class.java)
}
