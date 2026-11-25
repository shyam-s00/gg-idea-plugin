package dev.gopherglide.ggplugin.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** A minimal MCP client over the daemon's HTTP endpoint, standing in for an AI agent. */
class McpTestClient(private val info: DaemonInfo) {
    private val client = HttpClient.newHttpClient()
    private var sessionId: String? = null
    private var next = 1

    init {
        rpc("initialize", """{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"0"}}""")
        rpc("notifications/initialized", "{}", notification = true)
    }

    /** The tool's own JSON result, or its text when it is not JSON (a refusal comes back as text). */
    fun call(tool: String, arguments: String): String {
        val result = rpc("tools/call", """{"name":"$tool","arguments":$arguments}""")
        val content = JsonParser.parseString(result).asJsonObject["result"].asJsonObject["content"].asJsonArray
        return content[0].asJsonObject["text"].asString
    }

    fun json(tool: String, arguments: String): JsonObject = JsonParser.parseString(call(tool, arguments)).asJsonObject

    private fun rpc(method: String, params: String, notification: Boolean = false): String {
        val id = if (notification) "" else ""","id":${next++}"""
        val body = """{"jsonrpc":"2.0","method":"$method","params":$params$id}"""
        val builder = HttpRequest.newBuilder(URI.create(info.baseUrl + "/mcp"))
            .header("Authorization", "Bearer ${info.token}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        response.headers().firstValue("Mcp-Session-Id").ifPresent { sessionId = it }
        val text = response.body()
        return text.lineSequence().firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim() ?: text
    }
}
