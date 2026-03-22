package io.gopherglide.intellij.ggideaplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.HttpRequests
import io.gopherglide.intellij.ggideaplugin.settings.GopherGlideSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CompletableFuture

@Service(Service.Level.APP)
class BinaryManager {
    private val log = Logger.getInstance(BinaryManager::class.java)

    fun resolveBinaryPath(): String? {
        val customPath = GopherGlideSettings.instance.customBinaryPath
        if (customPath.isNotBlank()) {
            val file = File(customPath)
            if (file.exists() && file.canExecute()) {
                return customPath
            }
        }

        // Check PATH
        val pathEnv = System.getenv("PATH")
        val binaryName = if (SystemInfo.isWindows) "gg.exe" else "gg"

        pathEnv?.split(File.pathSeparator)?.forEach { dir ->
            val file = File(dir, binaryName)
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }

        // Check plugin temp path
        val downloadedBinary = getDownloadedBinaryPath()
        if (downloadedBinary.exists() && downloadedBinary.canExecute()) {
            return downloadedBinary.absolutePath
        }

        return null
    }

    private fun getDownloadedBinaryPath(): File {
        val pluginDir = File(PathManager.getPluginsPath(), "gopher-glide")
        pluginDir.mkdirs()
        val binaryName = if (SystemInfo.isWindows) "gg.exe" else "gg"
        return File(pluginDir, binaryName)
    }

    fun downloadLatestRelease(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                log.info("Starting download of gopher-glide binary...")
                val os = when {
                    SystemInfo.isWindows -> "windows"
                    SystemInfo.isMac -> "darwin"
                    else -> "linux"
                }
                val arch = when {
                    SystemInfo.isAarch64 -> "arm64"
                    else -> "amd64"
                }

                val baseUrl = GopherGlideSettings.instance.releaseUrl.removeSuffix("/")
                val url = "$baseUrl/gg_${os}_${arch}"
                val dest = getDownloadedBinaryPath()

                HttpRequests.request(url).saveToFile(dest, null)

                if (SystemInfo.isUnix || SystemInfo.isMac) {
                    val path = dest.toPath()
                    val perms = Files.getPosixFilePermissions(path)
                    perms.add(PosixFilePermission.OWNER_EXECUTE)
                    Files.setPosixFilePermissions(path, perms)
                }
                if (SystemInfo.isMac) {
                    val process = Runtime.getRuntime().exec(arrayOf("xattr", "-d", "com.apple.quarantine", dest.absolutePath))
                    process.waitFor()
                }

                log.info("Successfully downloaded gopher-glide binary to ${dest.absolutePath}")
                future.complete(dest.absolutePath)
            } catch (e: Exception) {
                log.error("Failed to download gopher-glide binary", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    companion object {
        val instance: BinaryManager
            get() = service()
    }
}
