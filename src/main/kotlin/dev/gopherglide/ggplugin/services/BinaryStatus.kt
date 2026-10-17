package dev.gopherglide.ggplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class BinaryStatus {
    private val cache = BinaryStatusCache(
        resolvePath = { BinaryManager.instance.resolveBinaryPath() },
        inspect = { path -> BinaryManager.instance.installedBinary(path) },
        fetchLatest = {
            try {
                GopherGlideVersion.parse(BinaryManager.instance.getLatestRemoteVersion().get(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            } catch (e: Exception) {
                null
            }
        }
    )

    /** Blocking; call off the EDT. [binaryPath] defaults to the binary the next run would use. */
    fun load(binaryPath: String? = null): BinaryStatusCache.Snapshot =
        if (binaryPath == null) cache.load() else cache.load(binaryPath)

    /** Loads on a pooled thread and delivers the result on the EDT. */
    fun loadAsync(binaryPath: String? = null, onLoaded: (BinaryStatusCache.Snapshot) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val snapshot = load(binaryPath)
            ApplicationManager.getApplication().invokeLater { onLoaded(snapshot) }
        }
    }

    fun installed(): InstalledBinary? = cache.installed()

    fun latest(): GopherGlideVersion? = cache.latest()

    fun notifyChanged() = cache.notifyChanged()

    /** The listener runs on whichever thread changed the binary; the result removes it. */
    fun addListener(listener: () -> Unit): () -> Unit = cache.addListener(listener)

    companion object {
        private const val LOOKUP_TIMEOUT_SECONDS = 15L

        val instance: BinaryStatus
            get() = service()
    }
}
