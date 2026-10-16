package dev.gopherglide.ggplugin.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.UpdateOffer
import dev.gopherglide.ggplugin.settings.GopherGlideConfigurable
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.CompletableFuture

const val GOPHER_GLIDE_NOTIFICATION_GROUP_ID = "Gopher Glide"
private const val ACTIONS_BEFORE_RATING_PROMPT = 3
private const val MARKETPLACE_REVIEWS_URL = "https://plugins.jetbrains.com/plugin/30983-gopher-glide/reviews"

object GopherGlideNotifications {
    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup(GOPHER_GLIDE_NOTIFICATION_GROUP_ID)

    /**
     * Call once per successfully completed gg action — a traffic simulation run or a `snap assert` —
     * regardless of whether the run/assert itself passed. Shows a one-time rating prompt at the 3rd.
     */
    fun onSuccessfulAction() {
        val settings = GopherGlideSettings.instance
        if (settings.dontShowRatingPrompt) return
        settings.successfulRunCount++
        if (settings.successfulRunCount >= ACTIONS_BEFORE_RATING_PROMPT) {
            notifyRatingPrompt()
        }
    }

    private fun notifyRatingPrompt() {
        val notification = group().createNotification(
            "Enjoying Gopher Glide?",
            "A quick rating/review on the Marketplace helps a lot.",
            NotificationType.INFORMATION
        )
        notification.addAction(NotificationAction.createSimple("Rate on Marketplace") {
            notification.expire()
            GopherGlideSettings.instance.dontShowRatingPrompt = true
            BrowserUtil.browse(MARKETPLACE_REVIEWS_URL)
        })
        notification.addAction(NotificationAction.createSimple("Remind Me Later") {
            notification.expire()
            GopherGlideSettings.instance.successfulRunCount = 0
        })
        notification.addAction(NotificationAction.createSimple("Don't Ask Again") {
            notification.expire()
            GopherGlideSettings.instance.dontShowRatingPrompt = true
        })
        notification.notify(null)
    }

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

    fun notifyUpdateAvailable(project: Project, offer: UpdateOffer) {
        val notification = group().createNotification(
            "Gopher Glide ${offer.latest} is available",
            "You have ${offer.installed}.",
            NotificationType.INFORMATION
        )
        notification.addAction(NotificationAction.createSimple("Update Now") {
            notification.expire()
            downloadWithProgress(project)
        })
        notification.addAction(NotificationAction.createSimple("Open Settings") {
            notification.expire()
            ShowSettingsUtil.getInstance().showSettingsDialog(project, GopherGlideConfigurable::class.java)
        })
        notification.addAction(NotificationAction.createSimple("Skip This Version") {
            notification.expire()
            GopherGlideSettings.instance.skippedGgVersion = offer.latest.toString()
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
