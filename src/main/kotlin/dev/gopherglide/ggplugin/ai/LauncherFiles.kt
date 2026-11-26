package dev.gopherglide.ggplugin.ai

import dev.gopherglide.ggplugin.mcp.DaemonInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * The small wrapper the JetBrains ACP entry runs instead of `gg`, and the files it reads. For `autopilot` it
 * points gg at the IDE's daemon: the one for the current folder, else the only live one of the IDE that
 * started the agent (Air starts it outside the project); otherwise gg runs on its own.
 * The daemon's token lives in a `0600` file next to the script, never in the script.
 */
class LauncherFiles(val dir: Path, private val windows: Boolean = System.getProperty("os.name").lowercase().contains("win")) {
    val script: Path = dir.resolve(if (windows) "gg-launcher.cmd" else "gg-launcher.sh")
    val envFile: Path = dir.resolve("agent.env")
    private val daemons: Path = dir.resolve("daemons")
    private val pending: Path = dir.resolve("pending")

    fun isInstalled(): Boolean = Files.isRegularFile(script)

    /** Writes the script for [ggPath]; true when its content changed. */
    fun install(ggPath: String): Boolean {
        val text = if (windows) cmdScript(ggPath, dir.toString()) else shellScript(ggPath, dir.toString())
        Files.createDirectories(dir)
        if (isInstalled() && Files.readString(script) == text) return false
        Files.writeString(script, text)
        script.toFile().setExecutable(true)
        return true
    }

    /** The optional LLM settings the launcher exports for `autopilot`; an empty map removes the file. */
    fun writeAgentEnv(values: Map<String, String>) {
        val clean = values.filter { (k, v) -> k in ENV_KEYS && v.isNotBlank() && '\n' !in v && '\r' !in v }
        if (clean.isEmpty()) {
            Files.deleteIfExists(envFile)
            return
        }
        Files.createDirectories(dir)
        writePrivate(envFile, clean.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
    }

    fun readAgentEnv(): Map<String, String> {
        if (!Files.isRegularFile(envFile)) return emptyMap()
        return Files.readAllLines(envFile).mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 && p[0] in ENV_KEYS } }.associate { it[0] to it[1] }
    }

    /** Lets the launcher find the daemon started in [workDir]; the token file is readable by this user only. */
    fun publishDaemon(workDir: String, info: DaemonInfo, idePid: Long = ProcessHandle.current().pid()) {
        if (!isInstalled()) return
        Files.createDirectories(daemons)
        val text = "url=${info.baseUrl}/mcp\ntoken=${info.token}\npid=${info.pid}\nide=$idePid\n"
        keysFor(workDir).forEach { writePrivate(daemons.resolve("$it.txt"), text) }
    }

    fun unpublishDaemon(workDir: String) {
        keysFor(workDir).forEach { Files.deleteIfExists(daemons.resolve("$it.txt")) }
    }

    /** Tells the launcher this IDE is starting a daemon for [workDir], so it waits for it instead of running alone. */
    fun markDaemonPending(workDir: String, idePid: Long = ProcessHandle.current().pid()) {
        if (!isInstalled()) return
        Files.createDirectories(pending)
        keysFor(workDir).forEach { writePrivate(pending.resolve("$it.txt"), "pid=$idePid\n") }
    }

    fun clearDaemonPending(workDir: String) {
        keysFor(workDir).forEach { Files.deleteIfExists(pending.resolve("$it.txt")) }
    }

    private fun keysFor(workDir: String): Set<String> =
        setOf(key(workDir), runCatching { key(Path.of(workDir).toRealPath().toString()) }.getOrDefault(key(workDir)))

    private fun writePrivate(file: Path, text: String) {
        Files.writeString(file, text)
        runCatching { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")) }
    }

    companion object {
        /** How many half-seconds the launcher waits for a daemon the IDE is still starting. */
        const val WAIT_TICKS = 120

        val ENV_KEYS = setOf("GEMINI_API_KEY", "GG_AUTOPILOT_MODEL", "GG_AUTOPILOT_BASE_URL", "GG_AUTOPILOT_API_KEY")

        fun defaultDir(): Path = Path.of(System.getProperty("user.home"), ".jetbrains", "gopher-glide")

        /** The file name for a folder: the characters a script can swap without extra tools become `_`. */
        fun key(path: String): String = path.replace(Regex("""[\\/: ]"""), "_")

        fun shellScript(gg: String, dir: String): String = """
            |#!/bin/sh
            |# Written by the Gopher Glide plugin. Lets `gg autopilot --acp` share the IDE's daemon.
            |GG=${shellQuote(gg)}
            |DIR=${shellQuote(dir)}
            |pick() {
            |  [ -f "${'$'}1" ] || return 1
            |  URL=${'$'}(sed -n 's/^url=//p' "${'$'}1")
            |  TOKEN=${'$'}(sed -n 's/^token=//p' "${'$'}1")
            |  PID=${'$'}(sed -n 's/^pid=//p' "${'$'}1")
            |  IDE=${'$'}(sed -n 's/^ide=//p' "${'$'}1")
            |  [ -n "${'$'}URL" ] && [ -n "${'$'}TOKEN" ] && [ -n "${'$'}PID" ] && kill -0 "${'$'}PID" 2>/dev/null
            |}
            |scan() {
            |  N=0
            |  SEEN=" "
            |  FOUND=""
            |  for F in "${'$'}DIR"/daemons/*.txt; do
            |    if pick "${'$'}F" && [ "${'$'}IDE" = "${'$'}1" ]; then
            |      case "${'$'}SEEN" in
            |        *" ${'$'}PID "*) ;;
            |        *) SEEN="${'$'}SEEN${'$'}PID "; N=${'$'}((N + 1)); FOUND="${'$'}F" ;;
            |      esac
            |    fi
            |  done
            |}
            |resolve() {
            |  URL=""
            |  TOKEN=""
            |  KEY=${'$'}(printf '%s' "${'$'}PWD" | tr '\\/: ' '____')
            |  if ! pick "${'$'}DIR/daemons/${'$'}KEY.txt"; then
            |    scan "${'$'}PPID"
            |    [ "${'$'}N" -eq 0 ] && scan ""
            |    if [ "${'$'}N" -ne 1 ] || ! pick "${'$'}FOUND"; then URL=""; TOKEN=""; fi
            |  fi
            |}
            |starting() {
            |  for P in "${'$'}DIR"/pending/*.txt; do
            |    [ -f "${'$'}P" ] || continue
            |    IDE=${'$'}(sed -n 's/^pid=//p' "${'$'}P")
            |    [ -n "${'$'}IDE" ] && kill -0 "${'$'}IDE" 2>/dev/null && return 0
            |  done
            |  return 1
            |}
            |if [ "${'$'}1" = "autopilot" ]; then
            |  resolve
            |  WAITED=0
            |  while [ -z "${'$'}URL" ] && [ "${'$'}WAITED" -lt ${WAIT_TICKS} ] && starting; do
            |    sleep 0.5
            |    WAITED=${'$'}((WAITED + 1))
            |    resolve
            |  done
            |  if [ -n "${'$'}URL" ] && [ -n "${'$'}TOKEN" ]; then
            |    GG_MCP_URL="${'$'}URL"
            |    GG_MCP_TOKEN="${'$'}TOKEN"
            |    export GG_MCP_URL GG_MCP_TOKEN
            |  fi
            |  if [ -f "${'$'}DIR/agent.env" ]; then
            |    while IFS='=' read -r K V; do
            |      case "${'$'}K" in GEMINI_API_KEY|GG_AUTOPILOT_MODEL|GG_AUTOPILOT_BASE_URL|GG_AUTOPILOT_API_KEY) export "${'$'}K=${'$'}V" ;; esac
            |    done < "${'$'}DIR/agent.env"
            |  fi
            |fi
            |exec "${'$'}GG" "${'$'}@"
            |""".trimMargin()

        fun cmdScript(gg: String, dir: String): String = """
            |@echo off
            |rem Written by the Gopher Glide plugin. Lets `gg autopilot --acp` share the IDE's daemon.
            |setlocal EnableDelayedExpansion
            |set "GG=$gg"
            |set "DIR=$dir"
            |if /I "%~1"=="autopilot" (
            |  set "KEY=%CD%"
            |  set "KEY=!KEY:\=_!"
            |  set "KEY=!KEY:/=_!"
            |  set "KEY=!KEY::=_!"
            |  set "KEY=!KEY: =_!"
            |  set "F_URL="
            |  set "F_TOKEN="
            |  set "F_PID="
            |  set "AMBIGUOUS="
            |  call :probe "%DIR%\daemons\!KEY!.txt"
            |  if defined P_URL (
            |    set "F_URL=!P_URL!"
            |    set "F_TOKEN=!P_TOKEN!"
            |  ) else (
            |    for %%F in ("%DIR%\daemons\*.txt") do (
            |      call :probe "%%~fF"
            |      if defined P_URL (
            |        if not defined F_PID (
            |          set "F_URL=!P_URL!"
            |          set "F_TOKEN=!P_TOKEN!"
            |          set "F_PID=!P_PID!"
            |        ) else if not "!P_PID!"=="!F_PID!" set "AMBIGUOUS=1"
            |      )
            |    )
            |    if defined AMBIGUOUS (
            |      set "F_URL="
            |      set "F_TOKEN="
            |    )
            |  )
            |  if defined F_URL if defined F_TOKEN (
            |    set "GG_MCP_URL=!F_URL!"
            |    set "GG_MCP_TOKEN=!F_TOKEN!"
            |  )
            |  if exist "%DIR%\agent.env" (
            |    for /f "usebackq tokens=1,* delims==" %%a in ("%DIR%\agent.env") do set "%%a=%%b"
            |  )
            |)
            |"%GG%" %*
            |exit /b %ERRORLEVEL%
            |
            |:probe
            |set "P_URL="
            |set "P_TOKEN="
            |set "P_PID="
            |if not exist "%~1" exit /b 1
            |for /f "usebackq tokens=1,* delims==" %%a in ("%~1") do (
            |  if "%%a"=="url" set "P_URL=%%b"
            |  if "%%a"=="token" set "P_TOKEN=%%b"
            |  if "%%a"=="pid" set "P_PID=%%b"
            |)
            |if not defined P_PID set "P_URL=" & exit /b 1
            |tasklist /FI "PID eq !P_PID!" /NH 2>nul | find "!P_PID!" >nul || (set "P_URL=" & exit /b 1)
            |exit /b 0
            |""".trimMargin()

        private fun shellQuote(text: String) = "'" + text.replace("'", "'\\''") + "'"
    }
}
