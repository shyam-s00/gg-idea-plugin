package dev.gopherglide.ggplugin.ai

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import dev.gopherglide.ggplugin.services.BinaryManager
import dev.gopherglide.ggplugin.services.InstalledBinary
import java.nio.file.Files
import java.nio.file.Path

/** Everything the setup dialog and the startup offer need to know about this project's AI connection. */
data class AiStatus(
    val mcp: McpStatus,
    val skillPresent: Boolean,
    val acp: AcpStatus,
    val launcherInstalled: Boolean,
    /** Names of the AI features this IDE has, for the dialog's hint line. */
    val detectedHosts: List<String>
) {
    /** True when an agent could already reach gg through this project or through Air. */
    val anythingLinked: Boolean get() = mcp.health == McpHealth.LINKED || acp.health == AcpHealth.REGISTERED
}

class AiEnvironmentDetector(
    private val acpFile: Path,
    private val launcher: LauncherFiles,
    private val hosts: () -> List<String> = ::installedHosts
) {
    fun inspect(projectDir: Path): AiStatus = AiStatus(
        mcp = McpConfig.inspect(read(projectDir.resolve(".mcp.json"))),
        skillPresent = Files.isRegularFile(projectDir.resolve(".claude/skills/gg/SKILL.md")),
        acp = AcpConfig.inspect(read(acpFile), launcher.script.toString().takeIf { launcher.isInstalled() }),
        launcherInstalled = launcher.isInstalled(),
        detectedHosts = hosts()
    )

    private fun read(path: Path): String? = if (Files.isRegularFile(path)) Files.readString(path) else null

    companion object {
        fun defaultAcpFile(): Path = Path.of(System.getProperty("user.home"), ".jetbrains", "acp.json")

        fun jetBrainsDirPresent(): Boolean = Files.isDirectory(Path.of(System.getProperty("user.home"), ".jetbrains"))

        fun installedHosts(): List<String> = buildList {
            if (PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.ml.llm"))) add("JetBrains AI Assistant")
            if (PluginManagerCore.isPluginInstalled(PluginId.getId("org.jetbrains.junie"))) add("Junie")
            if (ApplicationInfo.getInstance().fullApplicationName.contains("Air", ignoreCase = true)) add("JetBrains Air")
        }
    }
}

/** How the config files should call gg for the binary this IDE uses. */
object AiTargets {
    /** Null when no gg binary can be found. */
    fun forBinary(projectDir: Path, binary: String?, source: InstalledBinary.Source?): AiTarget? {
        binary ?: return null
        return AiTarget(projectDir, if (source == InstalledBinary.Source.SYSTEM) "gg" else binary, binary)
    }

    fun forProject(basePath: String?): AiTarget? {
        val dir = basePath?.let { Path.of(it) } ?: return null
        val binary = BinaryManager.instance.resolveBinaryPath() ?: return null
        return forBinary(dir, binary, BinaryManager.instance.installedBinary(binary).source)
    }
}
