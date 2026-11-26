package dev.gopherglide.ggplugin.ai

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** The command and arguments an MCP or ACP config runs for gg. */
data class GgEntry(val command: String, val args: List<String>) {
    val display: String get() = (listOf(command) + args).joinToString(" ")
}

enum class McpHealth {
    NO_FILE,
    MALFORMED,
    NO_ENTRY,
    /** The `gg` entry attaches to the IDE's daemon. */
    LINKED,
    /** The `gg` entry is gg's default and starts a server of its own, which the IDE never sees. */
    NEEDS_ATTACH,
    DIFFERENT
}

data class McpStatus(val health: McpHealth, val entry: GgEntry?, val otherServers: List<String>)

/** Reads and edits the `gg` server in a workspace `.mcp.json`. */
object McpConfig {
    const val SERVER = "gg"
    val ATTACH_ARGS = listOf("mcp", "--attach")
    private val DEFAULT_ARGS = listOf("mcp")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun inspect(text: String?): McpStatus {
        if (text == null) return McpStatus(McpHealth.NO_FILE, null, emptyList())
        val servers = servers(parse(text) ?: return McpStatus(McpHealth.MALFORMED, null, emptyList()))
        val others = servers?.keySet()?.filter { it != SERVER }.orEmpty()
        val entry = servers?.get(SERVER)?.let(::entryOf) ?: return McpStatus(McpHealth.NO_ENTRY, null, others)
        val health = when (entry.args) {
            ATTACH_ARGS -> McpHealth.LINKED
            DEFAULT_ARGS -> McpHealth.NEEDS_ATTACH
            else -> McpHealth.DIFFERENT
        }
        return McpStatus(health, entry, others)
    }

    /** The file with the `gg` entry set to attach to the daemon; everything else is kept. */
    fun withAttach(text: String): String = edit(text) { it.add("args", JsonArray().also { args -> ATTACH_ARGS.forEach(args::add) }) }

    fun withCommand(text: String, command: String): String = edit(text) { it.add("command", JsonPrimitive(command)) }

    private fun edit(text: String, change: (JsonObject) -> Unit): String {
        val root = parse(text) ?: error("not valid JSON")
        val entry = servers(root)?.getAsJsonObject(SERVER) ?: error("no gg entry")
        change(entry)
        return gson.toJson(root) + if (text.endsWith("\n")) "\n" else ""
    }

    private fun servers(root: JsonObject): JsonObject? = root["mcpServers"]?.takeIf { it.isJsonObject }?.asJsonObject

    internal fun parse(text: String): JsonObject? = try {
        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
    } catch (e: Exception) {
        null
    }

    internal fun entryOf(element: JsonElement): GgEntry? {
        val json = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val command = json["command"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val args = json["args"]?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
        return GgEntry(command, args)
    }
}

enum class AcpHealth { NO_FILE, MALFORMED, NO_ENTRY, REGISTERED, DIFFERENT }

data class AcpStatus(val health: AcpHealth, val entry: GgEntry?, val otherAgents: List<String>)

/** Reads the `gg autopilot` agent in the user-wide `~/.jetbrains/acp.json`. */
object AcpConfig {
    const val AGENT = "gg autopilot"
    val ARGS = listOf("autopilot", "--acp")

    /** [launcher] is the script a registration by this plugin runs; any other command counts as different. */
    fun inspect(text: String?, launcher: String?): AcpStatus {
        if (text == null) return AcpStatus(AcpHealth.NO_FILE, null, emptyList())
        val root = McpConfig.parse(text) ?: return AcpStatus(AcpHealth.MALFORMED, null, emptyList())
        val agents = root["agent_servers"]?.takeIf { it.isJsonObject }?.asJsonObject
        val others = agents?.keySet()?.filter { it != AGENT }.orEmpty()
        val entry = agents?.get(AGENT)?.let(McpConfig::entryOf) ?: return AcpStatus(AcpHealth.NO_ENTRY, null, others)
        val registered = launcher != null && entry.command == launcher && entry.args == ARGS
        return AcpStatus(if (registered) AcpHealth.REGISTERED else AcpHealth.DIFFERENT, entry, others)
    }
}

/** One line of `gg mcp init` output: what happened to a file, and why when it was skipped. */
data class InitLine(val status: String, val path: String, val reason: String?)

object InitOutput {
    private val line = Regex("""^(would-)?(created|added|replaced|updated|unchanged|skipped|removed)\s+(\S.*)$""")

    fun parse(stdout: String): List<InitLine> {
        val result = mutableListOf<InitLine>()
        val lines = stdout.lines()
        lines.forEachIndexed { index, text ->
            val match = line.matchEntire(text.trimEnd()) ?: return@forEachIndexed
            val reason = lines.getOrNull(index + 1)
                ?.takeIf { it.startsWith(" ") && it.isNotBlank() && !it.trimStart().startsWith("+") }
                ?.trim()
            result += InitLine(match.groupValues[2], match.groupValues[3].trim(), reason)
        }
        return result
    }

    /** What happened to the file that [pathSuffix] names, or null when init did not mention it. */
    fun forFile(lines: List<InitLine>, pathSuffix: String): InitLine? = lines.firstOrNull { it.path.endsWith(pathSuffix) }
}
