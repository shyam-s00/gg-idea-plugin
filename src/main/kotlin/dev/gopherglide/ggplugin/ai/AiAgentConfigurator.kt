package dev.gopherglide.ggplugin.ai

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Runs `gg` with arguments and returns what it printed. */
fun interface GgRunner {
    class Output(val exitCode: Int, val stdout: String, val stderr: String)

    fun run(args: List<String>): Output

    companion object {
        fun forBinary(binary: String): GgRunner = GgRunner { args ->
            val output = CapturingProcessHandler(GeneralCommandLine(binary).withParameters(args)).runProcess(30_000)
            Output(output.exitCode, output.stdout, output.stderr)
        }
    }
}

/** What the user chose in the setup dialog. */
data class AiRequest(
    val mcp: Boolean,
    val skill: Boolean,
    val acp: Boolean,
    /** Replace a `gg` entry that is not ours instead of leaving it. */
    val replaceMcp: Boolean = false,
    val replaceAcp: Boolean = false,
    /** Rewrite the skill file even when it differs from the current one. */
    val replaceSkill: Boolean = false,
    val excludeFromGit: Boolean = false,
    /** LLM settings for the ACP agent, written to the launcher's own file; null leaves them as they are. */
    val agentEnv: Map<String, String>? = null
)

/** Where setup writes, and how gg is called from the config files. */
data class AiTarget(
    val projectDir: Path,
    /** The `command` of the `.mcp.json` entry: `gg` when the IDE itself uses the gg on PATH, else a full path. */
    val mcpCommand: String,
    /** The gg the launcher runs. */
    val ggBinary: String
) {
    val mcpFile: Path get() = projectDir.resolve(".mcp.json")
    val skillFile: Path get() = projectDir.resolve(".claude/skills/gg/SKILL.md")
    val commandIsPath: Boolean get() = mcpCommand != "gg"
}

enum class Outcome { DONE, UNCHANGED, SKIPPED, FAILED }

data class StepResult(val what: String, val outcome: Outcome, val detail: String? = null)

data class ApplyResult(val steps: List<StepResult>) {
    val failed: Boolean get() = steps.any { it.outcome == Outcome.FAILED }

    /** True when the project ends up with something an agent can connect to. */
    val connected: Boolean get() = steps.any { it.what in CONNECTING && (it.outcome == Outcome.DONE || it.outcome == Outcome.UNCHANGED) }

    private companion object {
        val CONNECTING = setOf(AiAgentConfigurator.MCP, AiAgentConfigurator.ACP)
    }
}

/**
 * Sets up the files that connect AI agents to gg by running `gg mcp init`, which keeps other servers and
 * agents, never overwrites without `--force`, and edits `acp.json` in place. This adds the one thing it does
 * not: pointing `.mcp.json` at the IDE's daemon.
 */
class AiAgentConfigurator(
    private val gg: GgRunner,
    private val launcher: LauncherFiles,
    private val acpFile: Path,
    private val tempDir: () -> Path = { Files.createTempDirectory("gg-acp-init") }
) {
    fun apply(target: AiTarget, request: AiRequest): ApplyResult {
        val steps = mutableListOf<StepResult>()
        if (request.mcp) steps += applyMcp(target, request)
        if (request.skill) steps += applySkill(target, request)
        if (request.acp) steps += applyAcp(target, request)
        if (request.excludeFromGit && request.mcp && target.commandIsPath) steps += excludeFromGit(target.projectDir)
        return ApplyResult(steps)
    }

    /** The changes [apply] would make, as text: gg's own `--print` output plus the edits this adds. */
    fun preview(target: AiTarget, request: AiRequest): String {
        val sections = mutableListOf<String>()
        if (request.mcp) sections += previewMcp(target, request)
        if (request.skill) {
            sections += "Agent skill\n" + printed(skillArgs(target, request.replaceSkill)) +
                if (request.replaceSkill) "\nYour .mcp.json is left as it is." else ""
        }
        if (request.acp) sections += previewAcp(target, request)
        if (request.excludeFromGit && request.mcp && target.commandIsPath) sections += "Git\nAdd .mcp.json to .git/info/exclude"
        return sections.joinToString("\n\n").ifBlank { "Nothing selected." }
    }

    private fun applyMcp(target: AiTarget, request: AiRequest): StepResult {
        val existing = read(target.mcpFile)
        val status = McpConfig.inspect(existing)
        return when (status.health) {
            McpHealth.MALFORMED -> failed(MCP, ".mcp.json is not valid JSON. Fix or remove it first.")
            McpHealth.LINKED -> StepResult(MCP, Outcome.UNCHANGED, "already attaches to the IDE's daemon")
            McpHealth.NEEDS_ATTACH -> attach(target)
            McpHealth.NO_FILE, McpHealth.NO_ENTRY -> initThenAttach(target, force = false)
            McpHealth.DIFFERENT ->
                if (request.replaceMcp) initThenAttach(target, force = true)
                else StepResult(MCP, Outcome.SKIPPED, "kept your own gg entry (${status.entry?.display})")
        }
    }

    private fun initThenAttach(target: AiTarget, force: Boolean): StepResult {
        val args = listOf("--ide", "jetbrains", "--dir", target.projectDir.toString(), "--gg-path", target.mcpCommand) + if (force) listOf("--force") else emptyList()
        val output = init(args)
        if (output.exitCode != 0) return failed(MCP, message(output))
        val line = InitOutput.forFile(InitOutput.parse(output.stdout), ".mcp.json")
        if (line?.status == "skipped") return StepResult(MCP, Outcome.SKIPPED, line.reason)
        return attach(target)
    }

    private fun attach(target: AiTarget): StepResult = try {
        val text = Files.readString(target.mcpFile)
        val edited = McpConfig.withAttach(text)
        if (edited != text) Files.writeString(target.mcpFile, edited)
        StepResult(MCP, Outcome.DONE, "gg mcp --attach")
    } catch (e: Exception) {
        failed(MCP, "could not update .mcp.json: ${e.message}")
    }

    private fun applySkill(target: AiTarget, request: AiRequest): StepResult {
        if (!request.mcp && McpConfig.inspect(read(target.mcpFile)).health != McpHealth.LINKED) {
            return StepResult(SKILL, Outcome.SKIPPED, "needs the .mcp.json link")
        }
        val mcpBefore = read(target.mcpFile)
        val output = init(skillArgs(target, request.replaceSkill))
        if (request.replaceSkill && mcpBefore != null) restore(target.mcpFile, mcpBefore)
        if (output.exitCode != 0) return failed(SKILL, message(output))
        val line = InitOutput.forFile(InitOutput.parse(output.stdout), "SKILL.md") ?: return failed(SKILL, "gg did not report the skill file")
        return when (line.status) {
            "created", "added", "replaced", "updated" -> StepResult(SKILL, Outcome.DONE)
            "unchanged" -> StepResult(SKILL, Outcome.UNCHANGED)
            else -> StepResult(SKILL, Outcome.SKIPPED, line.reason)
        }
    }

    /** `--force` would also rewrite a different `gg` entry in `.mcp.json`, which the skill step must not touch. */
    private fun restore(file: Path, text: String) {
        try {
            if (read(file) != text) Files.writeString(file, text)
        } catch (_: Exception) {
        }
    }

    private fun skillArgs(target: AiTarget, force: Boolean) =
        listOf("--ide", "claude", "--dir", target.projectDir.toString(), "--gg-path", target.mcpCommand) + if (force) listOf("--force") else emptyList()

    private fun applyAcp(target: AiTarget, request: AiRequest): StepResult {
        val status = AcpConfig.inspect(read(acpFile), launcher.script.toString().takeIf { launcher.isInstalled() })
        if (status.health == AcpHealth.MALFORMED) return failed(ACP, "$acpFile is not valid JSON. Fix or move it; nothing was written.")
        return try {
            launcher.install(target.ggBinary)
            request.agentEnv?.let(launcher::writeAgentEnv)
            registerAcp(request.replaceAcp)
        } catch (e: Exception) {
            failed(ACP, "could not write the launcher: ${e.message}")
        }
    }

    private fun registerAcp(force: Boolean): StepResult {
        val scratch = tempDir()
        try {
            val args = acpArgs(scratch) + if (force) listOf("--force") else emptyList()
            val output = init(args)
            if (output.exitCode != 0) return failed(ACP, message(output))
            val line = InitOutput.forFile(InitOutput.parse(output.stdout), acpFile.fileName.toString())
                ?: return failed(ACP, "gg did not report the ACP file")
            return when (line.status) {
                "created", "added", "replaced", "updated" -> StepResult(ACP, Outcome.DONE, "restart the IDE to pick it up")
                "unchanged" -> StepResult(ACP, Outcome.UNCHANGED)
                else -> StepResult(ACP, Outcome.SKIPPED, line.reason)
            }
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun acpArgs(scratch: Path) =
        listOf("--ide", "jetbrains", "--dir", scratch.toString(), "--gg-path", launcher.script.toString(), "--register-acp", "--acp-file", acpFile.toString())

    private fun previewMcp(target: AiTarget, request: AiRequest): String {
        val status = McpConfig.inspect(read(target.mcpFile))
        val title = "Workspace .mcp.json"
        return when (status.health) {
            McpHealth.MALFORMED -> "$title\n.mcp.json is not valid JSON; fix or remove it first."
            McpHealth.LINKED -> "$title\nAlready attaches to the IDE's daemon."
            McpHealth.NEEDS_ATTACH -> "$title\nChange the gg entry's args from [mcp] to [mcp, --attach]."
            McpHealth.DIFFERENT ->
                if (request.replaceMcp) {
                    "$title\n" + printed(initMcpArgs(target, force = true)) + "\nThen set args to [mcp, --attach]."
                } else {
                    "$title\nKeeps your gg entry (${status.entry?.display})."
                }
            else -> "$title\n" + printed(initMcpArgs(target, force = false)) + "\nThen set args to [mcp, --attach]."
        }
    }

    private fun previewAcp(target: AiTarget, request: AiRequest): String {
        val lines = mutableListOf("JetBrains Air acp.json")
        lines += "Write the launcher ${launcher.script} (runs ${target.ggBinary})."
        lines += printed(acpArgs(target.projectDir) + if (request.replaceAcp) listOf("--force") else emptyList())
        return lines.joinToString("\n")
    }

    private fun initMcpArgs(target: AiTarget, force: Boolean) =
        listOf("--ide", "jetbrains", "--dir", target.projectDir.toString(), "--gg-path", target.mcpCommand) + if (force) listOf("--force") else emptyList()

    private fun printed(args: List<String>): String {
        val output = init(args + "--print")
        return if (output.exitCode == 0) output.stdout.trim() else message(output)
    }

    private fun init(args: List<String>) = gg.run(listOf("mcp", "init") + args)

    private fun message(output: GgRunner.Output) =
        output.stderr.lineSequence().lastOrNull { it.isNotBlank() }?.trim() ?: "gg mcp init exited with code ${output.exitCode}"

    private fun read(path: Path): String? = if (Files.isRegularFile(path)) Files.readString(path) else null

    private fun failed(what: String, detail: String) = StepResult(what, Outcome.FAILED, detail)

    private fun excludeFromGit(projectDir: Path): StepResult {
        val exclude = projectDir.resolve(".git/info/exclude")
        if (!Files.isDirectory(projectDir.resolve(".git"))) return StepResult(GIT, Outcome.SKIPPED, "not a git checkout")
        return try {
            Files.createDirectories(exclude.parent)
            val current = if (Files.exists(exclude)) Files.readString(exclude) else ""
            if (current.lines().any { it.trim() == ".mcp.json" || it.trim() == "/.mcp.json" }) return StepResult(GIT, Outcome.UNCHANGED)
            Files.writeString(exclude, current + (if (current.isEmpty() || current.endsWith("\n")) "" else "\n") + ".mcp.json\n")
            StepResult(GIT, Outcome.DONE, "added .mcp.json to .git/info/exclude")
        } catch (e: Exception) {
            failed(GIT, e.message ?: "could not write .git/info/exclude")
        }
    }

    companion object {
        const val MCP = ".mcp.json"
        const val SKILL = "Agent skill"
        const val ACP = "JetBrains Air"
        const val GIT = "Git exclude"
    }
}
