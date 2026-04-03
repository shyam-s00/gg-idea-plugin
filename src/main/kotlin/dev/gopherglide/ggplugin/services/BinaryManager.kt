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
import com.intellij.util.system.CpuArch
import dev.gopherglide.ggplugin.settings.GopherGlideSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/** Separator used inside the `getLocalVersion` result to keep fields distinct. */
private const val SEP = "\u0001"

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

        // Check plugin temp path first (managed by plugin)
        val downloadedBinary = getDownloadedBinaryPath()
        if (downloadedBinary.exists() && downloadedBinary.canExecute()) {
            return downloadedBinary.absolutePath
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

        return null
    }

    internal fun getDownloadedBinaryPath(): File {
        val pluginDir = File(PathManager.getPluginsPath(), "gopher-glide-binary")
        pluginDir.mkdirs()
        val binaryName = if (SystemInfo.isWindows) "gg.exe" else "gg"
        return File(pluginDir, binaryName)
    }

    fun deleteDownloadedBinary() {
        val binaryFile = getDownloadedBinaryPath()
        if (binaryFile.exists()) {
            val deleted = binaryFile.delete()
            if (!deleted) {
                val renamed = binaryFile.renameTo(File(binaryFile.absolutePath + ".old." + System.currentTimeMillis()))
                if (renamed) {
                    log.info("Could not delete, but renamed old binary: ${binaryFile.absolutePath}")
                } else {
                    log.warn("Failed to delete or rename old binary at ${binaryFile.absolutePath} — it may be in use. The download will still overwrite it.")
                }
            } else {
                log.info("Deleted downloaded binary: ${binaryFile.absolutePath}")
            }
        }
    }

    /**
     * Returns a SEP-separated string: `rawVersionOutput \u0001 binaryPath \u0001 sourceTag`
     * where sourceTag is either "plugin" or "system".
     *
     * Using a separator keeps the binary path out of the version-number regex, preventing
     * false matches when a path component looks like a version (e.g. `/v0.3.0/gg`).
     */
    fun getLocalVersion(): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val binaryPath = resolveBinaryPath()
                if (binaryPath == null) {
                    future.complete(null)
                    return@executeOnPooledThread
                }

                val process = ProcessBuilder(binaryPath, "--version")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()

                val source = if (binaryPath.contains("gopher-glide-binary")) "[plugin]" else "[system]"
                // Structured result: rawOutput SEP binaryPath SEP source
                future.complete("$output$SEP$binaryPath$SEP$source")

            } catch (e: Exception) {
                log.warn("Failed to get local version", e)
                future.complete(null)
            }
        }
        return future
    }

    fun getLatestRemoteVersion(): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Add timestamp to bypass any API caching
                val apiUrl = "https://api.github.com/repos/shyam-s00/gopher-glide/releases/latest?t=${System.currentTimeMillis()}"
                val json = HttpRequests.request(apiUrl)
                    .tuner { it.setRequestProperty("Cache-Control", "no-cache") }
                    .readString(null)

                val tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
                if (tagMatcher.find()) {
                    future.complete(tagMatcher.group(1))
                } else {
                    future.complete(null)
                }
            } catch (e: Exception) {
                log.warn("Failed to fetch remote version", e)
                future.complete(null)
            }
        }
        return future
    }

    fun downloadLatestRelease(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                log.info("Starting download of gopher-glide binary...")

                // 1. Fetch latest version tag from GitHub API
                val apiUrl = "https://api.github.com/repos/shyam-s00/gopher-glide/releases/latest?t=${System.currentTimeMillis()}"
                val json = HttpRequests.request(apiUrl)
                    .tuner { it.setRequestProperty("Cache-Control", "no-cache") }
                    .readString(null)

                val tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
                if (!tagMatcher.find()) {
                    throw Exception("Could not parse latest tag from GitHub API")
                }
                val tag = tagMatcher.group(1) // e.g., "v0.4.1" — the actual latest release tag
                log.info("Found latest release tag: $tag")

                // 2. Construct asset name based on OS and architecture
                val os = when {
                    SystemInfo.isWindows -> "windows"
                    SystemInfo.isMac -> "darwin"
                    else -> "linux"
                }
                val arch = when {
                    CpuArch.isArm64() -> "arm64"
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
                    Decompressor.Zip(tempArchive.toPath()).extract(tempExtractDir.toPath())
                } else {
                    Decompressor.Tar(tempArchive.toPath()).extract(tempExtractDir.toPath())
                }

                FileUtil.delete(tempArchive)

                // 5. Find the binary inside the extracted archive
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

                // Fail fast if the binary was not found in the archive — do NOT fall through to
                // the old destFile check which would silently accept a stale binary if the old
                // file could not be deleted earlier.
                if (foundBinary == null) {
                    FileUtil.delete(tempExtractDir)
                    throw Exception("Binary '$targetBinaryName' not found inside the downloaded archive '$assetName'.")
                }

                val destFile = getDownloadedBinaryPath()
                log.info("Installing binary to: ${destFile.absolutePath}")

                // 6. Atomically replace the destination (REPLACE_EXISTING handles locked/existing files)
                Files.copy(foundBinary.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

                // Wipe the temp directory now that we have a stable copy
                FileUtil.delete(tempExtractDir)

                // 7. Set executable permissions explicitly
                if (SystemInfo.isWindows) {
                    destFile.setExecutable(true)
                } else {
                    // Use POSIX permissions for reliable executable bit on macOS/Linux.
                    // File.setExecutable(true) only sets owner-execute and can silently fail on
                    // some file-systems, causing canExecute() to return false later and
                    // resolveBinaryPath() to fall through to the (potentially older) system PATH binary.
                    try {
                        Files.setPosixFilePermissions(
                            destFile.toPath(),
                            setOf(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE,
                                PosixFilePermission.GROUP_READ,
                                PosixFilePermission.GROUP_EXECUTE,
                                PosixFilePermission.OTHERS_READ,
                                PosixFilePermission.OTHERS_EXECUTE
                            )
                        )
                    } catch (e: Exception) {
                        log.warn("Failed to set POSIX permissions via Files API, falling back to setExecutable", e)
                        if (!destFile.setExecutable(true)) {
                            log.warn("setExecutable(true) also failed for ${destFile.absolutePath} — binary may not be runnable")
                        }
                    }
                }

                if (!destFile.canExecute()) {
                    log.warn("Binary at ${destFile.absolutePath} is not executable after permissions were set")
                }

                if (SystemInfo.isMac) {
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf("xattr", "-d", "com.apple.quarantine", destFile.absolutePath))
                        process.waitFor()
                    } catch (e: Exception) {
                        log.warn("Failed to remove macOS quarantine flag", e)
                    }
                }

                log.info("Successfully downloaded and extracted gopher-glide binary to ${destFile.absolutePath}")
                future.complete(destFile.absolutePath)
            } catch (e: Exception) {
                log.error("Failed to download gopher-glide binary", e)
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * Collects a full diagnostic snapshot useful for troubleshooting version-update issues.
     * Inspects every binary it can locate, runs `--version` on each, and reports
     * file permissions, the resolved PATH order, and the plugin-managed directory.
     */
    fun collectDiagnostics(): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val sb = StringBuilder()
                sb.appendLine("=== Gopher Glide Diagnostics ===")
                sb.appendLine("Timestamp : ${java.time.LocalDateTime.now()}")
                sb.appendLine("OS        : ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
                sb.appendLine("Arch      : ${System.getProperty("os.arch")} | isArm64=${CpuArch.isArm64()}")
                sb.appendLine()

                // --- Plugin-managed binary ---
                val downloadedBinary = getDownloadedBinaryPath()
                sb.appendLine("[Plugin-managed binary]")
                sb.appendLine("  Path      : ${downloadedBinary.absolutePath}")
                sb.appendLine("  Exists    : ${downloadedBinary.exists()}")
                if (downloadedBinary.exists()) {
                    sb.appendLine("  Size      : ${downloadedBinary.length()} bytes")
                    sb.appendLine("  canExecute: ${downloadedBinary.canExecute()}")
                    sb.appendLine("  version   : ${runVersionCommand(downloadedBinary.absolutePath)}")
                }

                // --- Custom path setting ---
                sb.appendLine()
                sb.appendLine("[Custom path setting]")
                val customPath = GopherGlideSettings.instance.customBinaryPath
                if (customPath.isBlank()) {
                    sb.appendLine("  (not set)")
                } else {
                    val f = File(customPath)
                    sb.appendLine("  Path      : $customPath")
                    sb.appendLine("  Exists    : ${f.exists()}")
                    sb.appendLine("  canExecute: ${f.canExecute()}")
                    if (f.exists() && f.canExecute()) {
                        sb.appendLine("  version   : ${runVersionCommand(customPath)}")
                    }
                }

                // --- Resolved binary (what the plugin will actually use) ---
                sb.appendLine()
                sb.appendLine("[Resolved binary (active)]")
                val resolved = resolveBinaryPath()
                if (resolved == null) {
                    sb.appendLine("  NONE — binary not found")
                } else {
                    sb.appendLine("  Path      : $resolved")
                    sb.appendLine("  version   : ${runVersionCommand(resolved)}")
                }

                // --- System PATH scan ---
                sb.appendLine()
                sb.appendLine("[System PATH scan]")
                val binaryName = if (SystemInfo.isWindows) "gg.exe" else "gg"
                val pathEnv = System.getenv("PATH") ?: ""
                var pathHits = 0
                pathEnv.split(File.pathSeparator).forEach { dir ->
                    val f = File(dir, binaryName)
                    if (f.exists()) {
                        pathHits++
                        sb.appendLine("  Found: ${f.absolutePath}  canExecute=${f.canExecute()}")
                        if (f.canExecute()) sb.appendLine("    version: ${runVersionCommand(f.absolutePath)}")
                    }
                }
                if (pathHits == 0) sb.appendLine("  (none found on PATH)")

                future.complete(sb.toString())
            } catch (e: Exception) {
                future.complete("Diagnostics failed: ${e.message}")
            }
        }
        return future
    }

    private fun runVersionCommand(path: String): String {
        return try {
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output.ifBlank { "(empty output)" }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    companion object {
        val instance: BinaryManager
            get() = service()
    }
}
