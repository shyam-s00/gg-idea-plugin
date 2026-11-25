package dev.gopherglide.ggplugin.mcp

/** The hosts a set of `.http` files send requests to. [unresolved] is true when some request's host is not literal. */
data class HostScan(val hosts: Set<String>, val unresolved: Boolean) {
    operator fun plus(other: HostScan) = HostScan(hosts + other.hosts, unresolved || other.unresolved)

    /** Hosts to allow agents, or empty (any host) when the files cannot all be resolved. */
    fun allowList(): List<String> = if (unresolved) emptyList() else hosts.sorted()

    companion object {
        val NONE = HostScan(emptySet(), false)
    }
}

object HttpHosts {
    private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT")
    private val absolute = Regex("""^https?://([^/?#\s]+)""", RegexOption.IGNORE_CASE)

    /** Reads the request line of every request in one `.http` file. */
    fun scan(text: String): HostScan {
        val hosts = mutableSetOf<String>()
        var unresolved = false
        var expectRequest = true
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("###")) {
                expectRequest = true
                continue
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            if (!expectRequest) continue
            expectRequest = false
            val tokens = line.split(Regex("\\s+"))
            val url = if (tokens[0].uppercase() in methods) tokens.getOrNull(1) else tokens[0]
            val host = url?.let { absolute.find(it)?.groupValues?.get(1) }?.substringAfterLast('@')
            if (host == null) unresolved = true else hosts += host
        }
        return HostScan(hosts, unresolved)
    }
}
