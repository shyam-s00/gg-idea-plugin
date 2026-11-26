package dev.gopherglide.ggplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/** Per-project, per-user state; it lives in the workspace file, so it is not shared through version control. */
@State(name = "dev.gopherglide.ggplugin.settings.GopherGlideProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class GopherGlideProjectSettings : PersistentStateComponent<GopherGlideProjectSettings> {
    /** AI agents were connected to this project: its daemon starts with it, and `Allow AI Steer` is offered. */
    var aiLinked: Boolean = false
    /** The one-time offer to connect AI agents has been made for this project. */
    var aiOfferShown: Boolean = false

    override fun getState(): GopherGlideProjectSettings = this

    override fun loadState(state: GopherGlideProjectSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(project: Project): GopherGlideProjectSettings = project.service()
    }
}
