package dev.gopherglide.ggplugin.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.settings.GopherGlideSettings

class GopherGlideStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (GopherGlideSettings.instance.dontAskForMissingBinary) return

        val binaryPath = BinaryManager.instance.resolveBinaryPath()
        if (binaryPath == null) {
            ApplicationManager.getApplication().invokeLater {
                GopherGlideNotifications.notifyBinaryMissing(project)
            }
        }
    }
}
