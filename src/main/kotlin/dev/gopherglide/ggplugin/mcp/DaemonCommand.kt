package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonParser
import java.security.SecureRandom

/** What the shared daemon is started with. [allowHosts] empty means any host. */
data class DaemonOptions(
    val maxRps: Int,
    val maxDurationMinutes: Int,
    val maxAgentSessions: Int,
    val allowHosts: List<String>,
    val snapDir: String
)

/** Where a started daemon listens: [baseUrl] has no `/mcp` suffix, so `/plugin/...` hangs off it. */
data class DaemonInfo(val baseUrl: String, val token: String, val pid: Long)

/** The daemon's command line, environment and startup line. */
object DaemonCommand {
    fun args(options: DaemonOptions): List<String> =
        listOf(
            "mcp", "--port", "0", "--exit-on-stdin-eof",
            "--max-rps", options.maxRps.toString(),
            "--max-duration", "${options.maxDurationMinutes}m",
            "--max-sessions", options.maxAgentSessions.toString()
        ) + options.allowHosts.flatMap { listOf("--allow-host", it) }

    /** The token goes in the environment, not the arguments, so it stays out of the process list. */
    fun environment(token: String, options: DaemonOptions): Map<String, String> =
        buildMap {
            put("GG_MCP_TOKEN", token)
            if (options.snapDir.isNotBlank()) put("GG_SNAP_DIR", options.snapDir)
        }

    fun newToken(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Reads `{"event":"listening","pid":…,"url":"http://127.0.0.1:<port>/mcp"}`; null for any other line. */
    fun parseListening(line: String, token: String): DaemonInfo? {
        val json = try {
            JsonParser.parseString(line.trim()).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        if (json["event"]?.asString != "listening") return null
        val url = json["url"]?.asString ?: return null
        return DaemonInfo(url.removeSuffix("/mcp"), token, json["pid"]?.asLong ?: 0L)
    }

    /** Comma-separated user input to a clean host list. */
    fun parseHosts(text: String): List<String> =
        text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
