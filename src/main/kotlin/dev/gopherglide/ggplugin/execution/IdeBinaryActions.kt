package dev.gopherglide.ggplugin.execution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import dev.gopherglide.ggplugin.execution.control.BinaryActions
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryStatus
import dev.gopherglide.ggplugin.services.GopherGlideVersion
import dev.gopherglide.ggplugin.services.InstalledBinary
import dev.gopherglide.ggplugin.settings.GopherGlideConfigurable

class IdeBinaryActions(private val project: Project) : BinaryActions {
    override fun loadStatus(onLoaded: (InstalledBinary?, GopherGlideVersion?) -> Unit) {
        BinaryStatus.instance.loadAsync { onLoaded(it.installed, it.latest) }
    }

    override fun install(onDone: (Throwable?) -> Unit) {
        GopherGlideNotifications.downloadWithProgress(project).whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater { onDone(error) }
        }
    }

    override fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, GopherGlideConfigurable::class.java)
    }

    override fun onChanged(listener: () -> Unit): () -> Unit =
        BinaryStatus.instance.addListener { ApplicationManager.getApplication().invokeLater(listener) }
}
