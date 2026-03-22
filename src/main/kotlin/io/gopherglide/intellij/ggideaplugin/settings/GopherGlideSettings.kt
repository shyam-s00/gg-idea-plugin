package io.gopherglide.intellij.ggideaplugin.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "io.gopherglide.intellij.ggideaplugin.settings.GopherGlideSettings",
    storages = [Storage("GopherGlideSettings.xml")]
)
@Service(Service.Level.APP)
class GopherGlideSettings : PersistentStateComponent<GopherGlideSettings> {
    var customBinaryPath: String = ""
    var releaseUrl: String = "https://github.com/shyam/gopher-glide/releases/latest/download"

    override fun getState(): GopherGlideSettings = this

    override fun loadState(state: GopherGlideSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: GopherGlideSettings
            get() = service()
    }
}
