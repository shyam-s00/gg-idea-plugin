package dev.gopherglide.ggplugin.notifications

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.settings.GopherGlideConfigurable
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.CompletableFuture

const val GOPHER_GLIDE_NOTIFICATION_GROUP_ID = "Gopher Glide"

object GopherGlideNotifications {
    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup(GOPHER_GLIDE_NOTIFICATION_GROUP_ID)

    fun notifyBinaryMissing(project: Project) {
        val notification = group().createNotification(
            "Gopher Glide binary not found",
            "Download it now, or point the plugin at an existing install.",
            NotificationType.WARNING
        )
        notification.addAction(NotificationAction.createSimple("Download Now") {
            notification.expire()
            downloadWithProgress(project)
        })
        notification.addAction(NotificationAction.createSimple("Set Custom Path...") {
            notification.expire()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, GopherGlideConfigurable::class.java)
        })
        notification.addAction(NotificationAction.createSimple("Don't ask again") {
            notification.expire()
            GopherGlideSettings.instance.dontAskForMissingBinary = true
        })
        notification.notify(project)
    }

    /** Wraps [BinaryManager.downloadLatestRelease] with a sticky "downloading" notification, used by both the
     * proactive first-run check and the existing lazy download-on-first-Run path. */
    fun downloadWithProgress(project: Project?): CompletableFuture<String> {
        val sticky = group().createNotification(
            "Gopher Glide",
            "Downloading Gopher Glide…",
            NotificationType.INFORMATION
        )
        sticky.notify(project)

        val future = BinaryManager.instance.downloadLatestRelease()
        future.whenComplete { _, error ->
            ApplicationManager.getApplication().invokeLater {
                sticky.expire()
                if (error == null) {
                    val message = if (SystemInfo.isWindows) {
                        "Gopher Glide downloaded successfully. If Windows SmartScreen blocks it, right-click the binary → Properties → \"Unblock\"."
                    } else {
                        "Gopher Glide downloaded successfully."
                    }
                    group().createNotification("Gopher Glide", message, NotificationType.INFORMATION).notify(project)
                }
            }
        }
        return future
    }
}
