package dev.gopherglide.ggplugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

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
                
                // 1. Fetch latest version tag from GitHub API
                val apiUrl = "https://api.github.com/repos/shyam-s00/gopher-glide/releases/latest"
                val json = HttpRequests.request(apiUrl).readString(null)
                
                val tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
                if (!tagMatcher.find()) {
                    throw Exception("Could not parse latest tag from GitHub API")
                }
                val tag = tagMatcher.group(1) // e.g., "v0.4.0"
                log.info("Found latest release tag: $tag")

                // 2. Construct asset name based on OS and architecture
                val os = when {
                    SystemInfo.isWindows -> "windows"
                    SystemInfo.isMac -> "darwin"
                    else -> "linux"
                }
                val arch = when {
                    SystemInfo.isAarch64 -> "arm64"
                    else -> "amd64"
                }

                val isZip = SystemInfo.isWindows
                val extension = if (isZip) ".zip" else ".tar.gz"
                
                val assetName = "gg-$tag-$os-$arch$extension"
                val url = "https://github.com/shyam-s00/gopher-glide/releases/download/$tag/$assetName"
                
                // 3. Download the archive to a temp file
                val tempArchive = FileUtil.createTempFile("gg-download", extension)
                log.info("Downloading from: $url")
                HttpRequests.request(url).saveToFile(tempArchive, null)

                // 4. Extract to a temporary directory
                val tempExtractDir = FileUtil.createTempDirectory("gg-extract", null)
                
                if (isZip) {
                    Decompressor.Zip(tempArchive).extract(tempExtractDir)
                } else {
                    Decompressor.Tar(tempArchive).extract(tempExtractDir)
                }
                
                FileUtil.delete(tempArchive)

                // 5. Find the binary, move it to the final location, and clean up
                val targetBinaryName = if (SystemInfo.isWindows) "gg.exe" else "gg"
                var foundBinary: File? = null
                
                FileUtil.processFilesRecursively(tempExtractDir) { file ->
                    if (file.isFile && file.name == targetBinaryName) {
                        foundBinary = file
                        false // Stop processing once found
                    } else {
                        true
                    }
                }

                val dest = getDownloadedBinaryPath()
                if (foundBinary != null) {
                    FileUtil.copy(foundBinary!!, dest)
                }
                
                // Wipe the temp directory (deletes all the sample yaml/http files and folders)
                FileUtil.delete(tempExtractDir)

                if (!dest.exists()) {
                    throw Exception("Binary '$targetBinaryName' not found inside the downloaded archive.")
                }

                // 6. Make executable and handle macOS quarantine
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

                log.info("Successfully downloaded and extracted gopher-glide binary to ${dest.absolutePath}")
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
