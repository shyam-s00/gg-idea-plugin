package dev.gopherglide.ggplugin.snap

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.io.File

object SnapDataManager {
    private val log = Logger.getInstance(SnapDataManager::class.java)
    private val gson = Gson()

    fun getSnapshotsDir(): File {
        val settings = GopherGlideSettings.instance
        if (settings.customSnapshotsDir.isNotBlank()) {
            return File(settings.customSnapshotsDir)
        }

        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        val path = when {
            osName.contains("mac") -> "$userHome/Library/Application Support/gg/snapshots"
            osName.contains("win") -> System.getenv("APPDATA") + "\\gg\\snapshots"
            else -> "$userHome/.config/gg/snapshots"
        }
        return File(path)
    }

    fun loadSnaps(): List<SnapModel> {
        val dir = getSnapshotsDir()
        if (!dir.exists() || !dir.isDirectory) {
            log.info("Snap directory does not exist: ${dir.absolutePath}")
            return emptyList()
        }

        val snaps = mutableListOf<SnapModel>()
        val files = dir.listFiles { file -> file.name.endsWith(".snap") } ?: return emptyList()

        for (file in files) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val snap = gson.fromJson(json, SnapModel::class.java)
                if (snap != null) {
                    snap.id = file.nameWithoutExtension
                    snaps.add(snap)
                }
            } catch (e: Exception) {
                log.warn("Failed to parse snap file: ${file.name}", e)
            }
        }

        // Sort descending by timestamp
        val sortedSnaps = snaps.sortedByDescending { it.meta?.startTime ?: "" }
        val total = sortedSnaps.size
        sortedSnaps.forEachIndexed { index, snap ->
            snap.internalIndex = (total - index).toString()
        }
        return sortedSnaps
    }
}
