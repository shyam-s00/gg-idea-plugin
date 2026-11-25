package dev.gopherglide.ggplugin.mcp

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class HttpResult(val status: Int, val body: String)

/** Blocking calls to the daemon's plugin endpoints; run them off the EDT. */
class PluginHttp(private val info: DaemonInfo) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    /** Throws [IOException] when the daemon cannot be reached. */
    fun post(path: String, json: String): HttpResult {
        val request = HttpRequest.newBuilder(URI.create(info.baseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer ${info.token}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            HttpResult(response.statusCode(), response.body())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted", e)
        }
    }
}
