package dev.gopherglide.ggplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.gopherglide.ggplugin.notifications.GopherGlideNotifications
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.util.concurrent.atomic.AtomicBoolean

/** Looks for a newer `gg` release once per IDE start, however many projects open. */
@Service(Service.Level.APP)
class UpdateChecker {
    private val started = AtomicBoolean(false)

    fun checkOnStartup(project: Project) {
        checkOnce(
            policy = {
                val status = BinaryStatus.instance
                UpdatePolicy(SettingsUpdateState, status::installed, status::latest)
            },
            background = { ApplicationManager.getApplication().executeOnPooledThread(it) },
            onOffer = { offer -> ApplicationManager.getApplication().invokeLater { GopherGlideNotifications.notifyUpdateAvailable(project, offer) } }
        )
    }

    internal fun checkOnce(policy: () -> UpdatePolicy, background: (() -> Unit) -> Unit, onOffer: (UpdateOffer) -> Unit) {
        if (!started.compareAndSet(false, true)) return
        background {
            val offer = try {
                policy().check()
            } catch (e: Exception) {
                null
            }
            if (offer != null) onOffer(offer)
        }
    }

    private object SettingsUpdateState : UpdateCheckState {
        private val settings get() = GopherGlideSettings.instance
        override var enabled: Boolean
            get() = settings.checkForUpdatesOnStartup
            set(value) { settings.checkForUpdatesOnStartup = value }
        override var lastCheckMillis: Long
            get() = settings.lastUpdateCheckMillis
            set(value) { settings.lastUpdateCheckMillis = value }
        override var skippedVersion: String
            get() = settings.skippedGgVersion
            set(value) { settings.skippedGgVersion = value }
    }

    companion object {
        val instance: UpdateChecker
            get() = service()
    }
}
