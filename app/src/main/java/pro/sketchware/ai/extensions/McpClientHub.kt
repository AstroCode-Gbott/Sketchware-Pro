package pro.sketchware.ai.extensions

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import pro.sketchware.ai.provider.ToolDefinition
import pro.sketchware.ai.provider.ToolParameterProperty
import pro.sketchware.ai.shell.LinuxSubsystem
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class McpRemoteTool(
    val serverId: String,
    val name: String,
    val description: String,
    val parameters: List<ToolParameterProperty>
) {
    val qualifiedName: String
        get() = "mcp__${serverId.replace('-', '_')}__${name.replace('-', '_')}"
}

data class McpConnectorConfig(
    val id: String,
    val name: String,
    val transport: String, // "http" or "stdio"
    val url: String = "",
    val command: String = "",
    val inLinux: Boolean = true,
    val linuxDistro: String = "debian",
    val headers: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val source: String = "user", // "user", "builtin", or "plugin:<id>"
    val cachedTools: List<McpRemoteTool> = emptyList(),
    val lastStatus: String = "Belum disinkronkan"
)

/**
 * Outbound MCP (Model Context Protocol) Client Connector Hub.
 *
 * Connects Sketchware Pro's on-device AI Agent to external MCP servers over:
 * 1) `http` (Streamable HTTP / JSON-RPC 2.0 POST, supporting both `application/json` and `text/event-stream` responses)
 * 2) `stdio` (Subprocess JSON-RPC 2.0 over stdin/stdout inside Debian/Linux proot or Android host shell)
 *
 * Discovered tools from enabled connectors are automatically exposed to the LLM both as direct
 * native tools (`mcp__<server>__<tool>`) and through `call_mcp_tool`.
 */
object McpClientHub {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val compactGson = GsonBuilder().disableHtmlEscaping().create()
    private val rpcIdCounter = AtomicInteger(100)
    private var seeded = false

    private fun configFile(): File = File(SkillManager.aiRootDir(), "mcp_connectors.json")
    private fun mcpScriptsDir(): File = File(SkillManager.aiRootDir(), "mcp").apply { mkdirs() }

    @Synchronized
    fun ensureSeeded() {
        if (seeded) return
        seeded = true
        try {
            seedBuiltInConnectors()
        } catch (_: Throwable) {
        }
    }

    fun listConnectors(): List<McpConnectorConfig> {
        ensureSeeded()
        val f = configFile()
        val list = mutableListOf<McpConnectorConfig>()
        if (f.isFile) {
            try {
                val root = JsonParser.parseString(f.readText()).asJsonObject
                val arr = root.getAsJsonArray("connectors") ?: JsonArray()
                for (el in arr) {
                    if (el.isJsonObject) {
                        parseConnector(el.asJsonObject)?.let { list.add(it) }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return list
    }

    fun saveConnectors(connectors: List<McpConnectorConfig>) {
        try {
            val root = JsonObject().apply {
                add("connectors", JsonArray().apply {
                    for (c in connectors) {
                        add(serializeConnector(c))
                    }
                })
            }
            configFile().writeText(gson.toJson(root))
        } catch (_: Throwable) {
        }
    }

    fun addOrUpdateConnector(config: McpConnectorConfig): McpConnectorConfig {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(config.id.ifBlank { config.name })
        val normalized = config.copy(id = cleanId)
        val current = listConnectors().toMutableList()
        val idx = current.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = normalized
        } else {
            current.add(normalized)
        }
        saveConnectors(current)
        return normalized
    }

    fun removeConnector(id: String): Boolean {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(id)
        val current = listConnectors().toMutableList()
        val removed = current.removeAll { it.id.equals(cleanId, ignoreCase = true) }
        if (removed) saveConnectors(current)
        return removed
    }

    fun setConnectorEnabled(id: String, enabled: Boolean): Boolean {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(id)
        val current = listConnectors().toMutableList()
        val idx = current.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
        if (idx < 0) return false
        current[idx] = current[idx].copy(enabled = enabled)
        saveConnectors(current)
        return true
    }

    /**
     * Connects to the MCP server, runs `initialize` + `tools/list`, caches the discovered tools,
     * and returns a status summary.
     */
    fun syncConnectorTools(id: String): String {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(id)
        val current = listConnectors().toMutableList()
        val idx = current.indexOfFirst { it.id.equals(cleanId, ignoreCase = true) }
        if (idx < 0) return "Error: Konektor MCP '$id' tidak ditemukan."

        val conn = current[idx]
        return try {
            val tools = fetchToolsFromServer(conn)
            val updated = conn.copy(
                cachedTools = tools,
                lastStatus = "OK (${tools.size} tools)"
            )
            current[idx] = updated
            saveConnectors(current)
            buildString {
                appendLine("Berhasil sinkronisasi konektor MCP '${conn.name}' (${conn.id}): ${tools.size} tool ditemukan.")
                for (t in tools) {
                    appendLine("- ${t.name} (qualified: ${t.qualifiedName}): ${t.description}")
                }
            }.trim()
        } catch (t: Throwable) {
            val errMsg = "Error: ${t.javaClass.simpleName}: ${t.message}"
            current[idx] = conn.copy(lastStatus = errMsg.take(120))
            saveConnectors(current)
            "Gagal sinkronisasi MCP '${conn.name}' (${conn.id}): $errMsg"
        }
    }

    /**
     * Calls a tool on an external MCP server by `serverId` and `toolName`, or resolves a qualified
     * `mcp__<server>__<tool>` name automatically.
     */
    fun callRemoteTool(serverId: String, toolName: String, arguments: JsonObject): String {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(serverId)
        val conn = listConnectors().firstOrNull {
            it.id.equals(cleanId, ignoreCase = true) ||
                it.id.replace('-', '_').equals(serverId.replace('-', '_'), ignoreCase = true)
        } ?: return "Error: Konektor MCP '$serverId' tidak ditemukan."

        return try {
            val params = JsonObject().apply {
                addProperty("name", toolName)
                add("arguments", arguments)
            }
            val result = when (conn.transport.lowercase()) {
                "http" -> executeHttpRpc(conn, "tools/call", params)
                "stdio" -> executeStdioRpcSession(conn, "tools/call", params)
                else -> throw IllegalArgumentException("Transport '${conn.transport}' tidak didukung.")
            }
            formatMcpToolCallResult(conn.id, toolName, result)
        } catch (t: Throwable) {
            "Error memanggil MCP [${conn.id} -> $toolName]: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun tryResolveQualifiedTool(qualifiedName: String): Pair<String, String>? {
        if (!qualifiedName.startsWith("mcp__")) return null
        val rest = qualifiedName.removePrefix("mcp__")
        val sep = rest.indexOf("__")
        if (sep <= 0) return null
        val serverPart = rest.substring(0, sep)
        val toolPart = rest.substring(sep + 2)
        val conn = listConnectors().firstOrNull {
            it.id.replace('-', '_').equals(serverPart, ignoreCase = true)
        } ?: return serverPart to toolPart
        val matchedTool = conn.cachedTools.firstOrNull {
            it.name.replace('-', '_').equals(toolPart, ignoreCase = true)
        }
        return conn.id to (matchedTool?.name ?: toolPart)
    }

    fun getDynamicToolDefinitions(): List<ToolDefinition> {
        val out = mutableListOf<ToolDefinition>()
        for (conn in listConnectors()) {
            if (!conn.enabled) continue
            for (rt in conn.cachedTools) {
                out.add(
                    ToolDefinition(
                        name = rt.qualifiedName,
                        description = "[MCP: ${conn.name}] ${rt.description}",
                        parameters = rt.parameters,
                        isWriteTool = false
                    )
                )
            }
        }
        return out
    }

    fun buildSystemPromptSection(): String {
        val enabled = listConnectors().filter { it.enabled }
        if (enabled.isEmpty()) return ""
        return buildString {
            appendLine("=== KONEKTOR MCP AKTIF (${enabled.size}) ===")
            for (c in enabled) {
                val toolNames = c.cachedTools.joinToString(", ") { it.name }.ifEmpty { "(panggil manage_mcp_connectors action='sync' untuk memuat daftar tool)" }
                appendLine("- `${c.id}` (${c.name} | transport=${c.transport} | status=${c.lastStatus}): $toolNames")
            }
            appendLine("Anda dapat memanggil tool MCP secara langsung menggunakan nama `mcp__<server>__<tool>` atau melalui `call_mcp_tool(server_id=\"...\", tool_name=\"...\", arguments_json=\"...\")`.")
        }.trim()
    }

    private fun fetchToolsFromServer(conn: McpConnectorConfig): List<McpRemoteTool> {
        val result = when (conn.transport.lowercase()) {
            "http" -> {
                // Attempt initialize first (ignore error if stateless server only needs tools/list)
                try {
                    executeHttpRpc(conn, "initialize", buildInitializeParams())
                } catch (_: Throwable) {
                }
                executeHttpRpc(conn, "tools/list", JsonObject())
            }
            "stdio" -> executeStdioRpcSession(conn, "tools/list", JsonObject())
            else -> throw IllegalArgumentException("Transport '${conn.transport}' tidak dikenal (gunakan 'http' atau 'stdio').")
        }
        val toolsArr = result.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("tools")
            ?: return emptyList()

        val tools = mutableListOf<McpRemoteTool>()
        for (el in toolsArr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val name = obj.get("name")?.asString?.trim().orEmpty()
            if (name.isEmpty()) continue
            val desc = obj.get("description")?.asString?.trim().orEmpty()
            val schema = obj.get("inputSchema")?.takeIf { it.isJsonObject }?.asJsonObject
            val params = parseInputSchema(schema)
            tools.add(McpRemoteTool(conn.id, name, desc, params))
        }
        return tools
    }

    private fun parseInputSchema(schema: JsonObject?): List<ToolParameterProperty> {
        if (schema == null) return emptyList()
        val props = schema.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val requiredSet = schema.get("required")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            ?.toSet() ?: emptySet()

        val result = mutableListOf<ToolParameterProperty>()
        for ((k, v) in props.entrySet()) {
            if (!v.isJsonObject) continue
            val pObj = v.asJsonObject
            val type = pObj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "string"
            val desc = pObj.get("description")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            val enums = pObj.get("enum")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            result.add(
                ToolParameterProperty(
                    name = k,
                    type = type,
                    description = desc,
                    required = k in requiredSet,
                    enumValues = enums
                )
            )
        }
        return result
    }

    private fun buildInitializeParams(): JsonObject = JsonObject().apply {
        addProperty("protocolVersion", "2024-11-05")
        add("capabilities", JsonObject())
        add("clientInfo", JsonObject().apply {
            addProperty("name", "sketchware-pro-ai-agent")
            addProperty("version", "2.0.0")
        })
    }

    private fun executeHttpRpc(conn: McpConnectorConfig, method: String, params: JsonObject): JsonElement {
        if (conn.url.isBlank()) throw IllegalArgumentException("URL konektor HTTP kosong.")
        val reqId = rpcIdCounter.incrementAndGet()
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", reqId)
            addProperty("method", method)
            add("params", params)
        }
        val bodyBytes = compactGson.toJson(payload).toByteArray(StandardCharsets.UTF_8)
        val http = (URL(conn.url.trim()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/event-stream")
            setRequestProperty("User-Agent", "SketchwarePro-MCP-Client/2.0")
            conn.headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            http.outputStream.use { it.write(bodyBytes) }
            val code = http.responseCode
            val stream = if (code >= 400) http.errorStream else http.inputStream
            val rawText = stream?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
            if (code >= 400) {
                throw IllegalStateException("HTTP $code dari ${conn.url}: ${rawText.take(300)}")
            }
            return extractJsonRpcResult(rawText)
        } finally {
            http.disconnect()
        }
    }

    /**
     * Runs a stdio MCP server process (either inside Linux/Debian via proot or on Android host shell),
     * sends `initialize`, `notifications/initialized`, and the target [method] request, and parses
     * the JSON-RPC response line.
     */
    private fun executeStdioRpcSession(conn: McpConnectorConfig, method: String, params: JsonObject): JsonElement {
        if (conn.command.isBlank()) throw IllegalArgumentException("Command untuk konektor stdio kosong.")

        val initReq = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", 1)
            addProperty("method", "initialize")
            add("params", buildInitializeParams())
        }
        val initializedNotif = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", "notifications/initialized")
            add("params", JsonObject())
        }
        val targetId = 2
        val targetReq = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", targetId)
            addProperty("method", method)
            add("params", params)
        }

        val pb = buildStdioProcessBuilder(conn)
        val process = pb.start()
        try {
            val writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
            writer.write(compactGson.toJson(initReq) + "\n")
            writer.write(compactGson.toJson(initializedNotif) + "\n")
            writer.write(compactGson.toJson(targetReq) + "\n")
            writer.flush()
            try {
                writer.close()
            } catch (_: Throwable) {
            }

            val stdoutLines = mutableListOf<String>()
            val stderrBuf = StringBuilder()

            val outThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            synchronized(stdoutLines) { stdoutLines.add(line) }
                        }
                    }
                } catch (_: Throwable) {
                }
            }.apply { isDaemon = true; start() }

            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { r ->
                        while (true) {
                            val line = r.readLine() ?: break
                            synchronized(stderrBuf) { stderrBuf.appendLine(line) }
                        }
                    }
                } catch (_: Throwable) {
                }
            }.apply { isDaemon = true; start() }

            process.waitFor(45, TimeUnit.SECONDS)
            outThread.join(1500)
            errThread.join(500)

            val snapshot = synchronized(stdoutLines) { stdoutLines.toList() }
            for (line in snapshot.reversed()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("{")) continue
                try {
                    val obj = JsonParser.parseString(trimmed).asJsonObject
                    val idEl = obj.get("id")
                    if (idEl != null && !idEl.isJsonNull && idEl.asInt == targetId) {
                        if (obj.has("error") && !obj.get("error").isJsonNull) {
                            throw IllegalStateException("MCP Error: ${obj.get("error")}")
                        }
                        return obj.get("result") ?: JsonObject()
                    }
                } catch (e: IllegalStateException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
            val errStr = synchronized(stderrBuf) { stderrBuf.toString().trim() }
            throw IllegalStateException(
                "Tidak menerima respons JSON-RPC id=$targetId dari proses stdio. " +
                    (if (errStr.isNotEmpty()) "stderr: ${errStr.take(300)}" else "stdout: ${snapshot.takeLast(3).joinToString(" | ")}")
            )
        } finally {
            try {
                process.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildStdioProcessBuilder(conn: McpConnectorConfig): ProcessBuilder {
        val envExports = conn.env.entries.joinToString("") { (k, v) ->
            "export $k='${v.replace("'", "'\\''")}'; "
        }
        val useLinux = conn.inLinux && LinuxSubsystem.isReady()
        return if (useLinux) {
            val distro = conn.linuxDistro.ifBlank { LinuxSubsystem.defaultDistro() ?: "debian" }
            val guestScript = LinuxSubsystem.GUEST_ENV_PREFIX + envExports + conn.command
            val prootArgs = LinuxSubsystem.buildProotCommand(
                distro = distro,
                cwd = SkillManager.aiRootDir(),
                guestArgs = listOf("/bin/sh", "-c", guestScript)
            )
            val quoted = prootArgs.joinToString(" ") { arg ->
                if (arg.isNotEmpty() && arg.all { it.isLetterOrDigit() || it in "/._-+=:@," }) arg
                else "'" + arg.replace("'", "'\\''") + "'"
            }
            ProcessBuilder("/system/bin/sh", "-c", LinuxSubsystem.prootEnvPrefix() + "exec " + quoted).apply {
                directory(LinuxSubsystem.baseDir())
            }
        } else {
            ProcessBuilder("/system/bin/sh", "-c", envExports + conn.command).apply {
                directory(SkillManager.aiRootDir())
            }
        }
    }

    private fun extractJsonRpcResult(rawText: String): JsonElement {
        val trimmed = rawText.trim()
        // Support SSE (text/event-stream) where JSON-RPC payloads are prefixed with "data: "
        val candidates = mutableListOf<String>()
        if (trimmed.startsWith("{")) {
            candidates.add(trimmed)
        }
        for (line in trimmed.lines()) {
            val l = line.trim()
            if (l.startsWith("data:")) {
                val dataJson = l.removePrefix("data:").trim()
                if (dataJson.startsWith("{")) candidates.add(dataJson)
            } else if (l.startsWith("{")) {
                candidates.add(l)
            }
        }
        for (c in candidates.reversed()) {
            try {
                val obj = JsonParser.parseString(c).asJsonObject
                if (obj.has("error") && !obj.get("error").isJsonNull) {
                    throw IllegalStateException("MCP RPC Error: ${obj.get("error")}")
                }
                if (obj.has("result")) {
                    return obj.get("result")
                }
            } catch (e: IllegalStateException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        throw IllegalStateException("Respons bukan JSON-RPC MCP yang valid: ${trimmed.take(250)}")
    }

    private fun formatMcpToolCallResult(serverId: String, toolName: String, result: JsonElement): String {
        if (!result.isJsonObject) return result.toString()
        val obj = result.asJsonObject
        val isError = obj.get("isError")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val contentArr = obj.get("content")?.takeIf { it.isJsonArray }?.asJsonArray
        return buildString {
            appendLine("=== MCP [$serverId :: $toolName] ${if (isError) "ERROR" else "OK"} ===")
            if (contentArr != null && contentArr.size() > 0) {
                for (item in contentArr) {
                    if (item.isJsonObject) {
                        val iObj = item.asJsonObject
                        val text = iObj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                        if (text != null) {
                            appendLine(text)
                        } else {
                            appendLine(iObj.toString())
                        }
                    } else {
                        appendLine(item.toString())
                    }
                }
            } else {
                appendLine(gson.toJson(obj))
            }
        }.trim()
    }

    private fun parseConnector(obj: JsonObject): McpConnectorConfig? {
        val id = obj.get("id")?.asString?.trim().orEmpty()
        if (id.isEmpty()) return null
        val name = obj.get("name")?.asString ?: id
        val transport = obj.get("transport")?.asString ?: "http"
        val url = obj.get("url")?.asString.orEmpty()
        val command = obj.get("command")?.asString.orEmpty()
        val inLinux = obj.get("inLinux")?.asBoolean ?: true
        val linuxDistro = obj.get("linuxDistro")?.asString ?: "debian"
        val enabled = obj.get("enabled")?.asBoolean ?: true
        val source = obj.get("source")?.asString ?: "user"
        val lastStatus = obj.get("lastStatus")?.asString ?: "Belum disinkronkan"

        val headers = mutableMapOf<String, String>()
        obj.get("headers")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (k, v) ->
            if (v.isJsonPrimitive) headers[k] = v.asString
        }
        val env = mutableMapOf<String, String>()
        obj.get("env")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (k, v) ->
            if (v.isJsonPrimitive) env[k] = v.asString
        }

        val cachedTools = mutableListOf<McpRemoteTool>()
        obj.get("cachedTools")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tEl ->
            if (tEl.isJsonObject) {
                val tObj = tEl.asJsonObject
                val tName = tObj.get("name")?.asString.orEmpty()
                val tDesc = tObj.get("description")?.asString.orEmpty()
                val params = mutableListOf<ToolParameterProperty>()
                tObj.get("parameters")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { pEl ->
                    if (pEl.isJsonObject) {
                        val pObj = pEl.asJsonObject
                        params.add(
                            ToolParameterProperty(
                                name = pObj.get("name")?.asString.orEmpty(),
                                type = pObj.get("type")?.asString ?: "string",
                                description = pObj.get("description")?.asString.orEmpty(),
                                required = pObj.get("required")?.asBoolean ?: false
                            )
                        )
                    }
                }
                if (tName.isNotEmpty()) {
                    cachedTools.add(McpRemoteTool(id, tName, tDesc, params))
                }
            }
        }

        return McpConnectorConfig(
            id = id,
            name = name,
            transport = transport,
            url = url,
            command = command,
            inLinux = inLinux,
            linuxDistro = linuxDistro,
            headers = headers,
            env = env,
            enabled = enabled,
            source = source,
            cachedTools = cachedTools,
            lastStatus = lastStatus
        )
    }

    private fun serializeConnector(c: McpConnectorConfig): JsonObject = JsonObject().apply {
        addProperty("id", c.id)
        addProperty("name", c.name)
        addProperty("transport", c.transport)
        addProperty("url", c.url)
        addProperty("command", c.command)
        addProperty("inLinux", c.inLinux)
        addProperty("linuxDistro", c.linuxDistro)
        addProperty("enabled", c.enabled)
        addProperty("source", c.source)
        addProperty("lastStatus", c.lastStatus)
        add("headers", JsonObject().apply { c.headers.forEach { (k, v) -> addProperty(k, v) } })
        add("env", JsonObject().apply { c.env.forEach { (k, v) -> addProperty(k, v) } })
        add("cachedTools", JsonArray().apply {
            for (t in c.cachedTools) {
                add(JsonObject().apply {
                    addProperty("name", t.name)
                    addProperty("description", t.description)
                    add("parameters", JsonArray().apply {
                        for (p in t.parameters) {
                            add(JsonObject().apply {
                                addProperty("name", p.name)
                                addProperty("type", p.type)
                                addProperty("description", p.description)
                                addProperty("required", p.required)
                            })
                        }
                    })
                })
            }
        })
    }

    private fun seedBuiltInConnectors() {
        // 1. Write the local Debian Python MCP Server script so stdio MCP works out of the box!
        val pyScript = File(mcpScriptsDir(), "debian_toolkit_mcp.py")
        if (!pyScript.exists()) {
            pyScript.writeText(
                """
                #!/usr/bin/env python3
                import sys, json, hashlib, sqlite3, os, subprocess

                TOOLS = [
                    {
                        "name": "python_eval",
                        "description": "Execute a Python 3 snippet inside Debian Linux and return stdout/result (great for math, JSON/XML transformations, asset generation, or data analysis).",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "code": {"type": "string", "description": "Python 3 code to run"}
                            },
                            "required": ["code"]
                        }
                    },
                    {
                        "name": "sqlite_inspect",
                        "description": "Execute an SQL query against a local SQLite database file and return rows as JSON.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "db_path": {"type": "string", "description": "Path to SQLite .db file"},
                                "sql": {"type": "string", "description": "SQL query to execute"}
                            },
                            "required": ["db_path", "sql"]
                        }
                    },
                    {
                        "name": "file_checksum",
                        "description": "Calculate SHA-256 and MD5 checksums + byte size of a file.",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "path": {"type": "string", "description": "Absolute path to file"}
                            },
                            "required": ["path"]
                        }
                    }
                ]

                def handle_call(name, args):
                    if name == "python_eval":
                        code = args.get("code", "")
                        p = subprocess.run(["python3", "-c", code], capture_output=True, text=True, timeout=30)
                        out = (p.stdout or "") + (("\n[stderr]\n" + p.stderr) if p.stderr else "")
                        return out.strip() or "(no output, exit=" + str(p.returncode) + ")"
                    elif name == "sqlite_inspect":
                        conn = sqlite3.connect(args["db_path"])
                        conn.row_factory = sqlite3.Row
                        cur = conn.cursor()
                        cur.execute(args["sql"])
                        if args["sql"].strip().upper().startswith("SELECT") or args["sql"].strip().upper().startswith("PRAGMA"):
                            rows = [dict(r) for r in cur.fetchall()]
                            conn.close()
                            return json.dumps(rows, indent=2)
                        else:
                            conn.commit()
                            cnt = cur.rowcount
                            conn.close()
                            return f"OK (affected rows: {cnt})"
                    elif name == "file_checksum":
                        p = args["path"]
                        h256 = hashlib.sha256()
                        hmd5 = hashlib.md5()
                        with open(p, "rb") as f:
                            while True:
                                b = f.read(65536)
                                if not b: break
                                h256.update(b)
                                hmd5.update(b)
                        return json.dumps({"path": p, "size": os.path.getsize(p), "sha256": h256.hexdigest(), "md5": hmd5.hexdigest()}, indent=2)
                    raise ValueError(f"Unknown tool: {name}")

                for line in sys.stdin:
                    line = line.strip()
                    if not line: continue
                    try:
                        req = json.loads(line)
                    except Exception:
                        continue
                    req_id = req.get("id")
                    method = req.get("method", "")
                    if req_id is None:
                        continue
                    if method == "initialize":
                        res = {"protocolVersion": "2024-11-05", "capabilities": {"tools": {}}, "serverInfo": {"name": "debian-toolkit-mcp", "version": "1.0.0"}}
                        print(json.dumps({"jsonrpc": "2.0", "id": req_id, "result": res}), flush=True)
                    elif method == "tools/list":
                        print(json.dumps({"jsonrpc": "2.0", "id": req_id, "result": {"tools": TOOLS}}), flush=True)
                    elif method == "tools/call":
                        params = req.get("params", {})
                        try:
                            text = handle_call(params.get("name"), params.get("arguments", {}))
                            print(json.dumps({"jsonrpc": "2.0", "id": req_id, "result": {"content": [{"type": "text", "text": text}], "isError": False}}), flush=True)
                        except Exception as e:
                            print(json.dumps({"jsonrpc": "2.0", "id": req_id, "result": {"content": [{"type": "text", "text": str(e)}], "isError": True}}), flush=True)
                    else:
                        print(json.dumps({"jsonrpc": "2.0", "id": req_id, "result": {}}), flush=True)
                """.trimIndent() + "\n"
            )
        }

        if (configFile().isFile) return

        val defaultTools = listOf(
            McpRemoteTool(
                serverId = "debian_toolkit",
                name = "python_eval",
                description = "Execute a Python 3 snippet inside Debian Linux and return stdout/result.",
                parameters = listOf(ToolParameterProperty("code", "string", "Python 3 code to run", required = true))
            ),
            McpRemoteTool(
                serverId = "debian_toolkit",
                name = "sqlite_inspect",
                description = "Execute an SQL query against a local SQLite database file and return rows as JSON.",
                parameters = listOf(
                    ToolParameterProperty("db_path", "string", "Path to SQLite .db file", required = true),
                    ToolParameterProperty("sql", "string", "SQL query to execute", required = true)
                )
            ),
            McpRemoteTool(
                serverId = "debian_toolkit",
                name = "file_checksum",
                description = "Calculate SHA-256 and MD5 checksums + byte size of a file.",
                parameters = listOf(ToolParameterProperty("path", "string", "Absolute path to file", required = true))
            )
        )

        val initial = listOf(
            McpConnectorConfig(
                id = "debian_toolkit",
                name = "Debian Python & SQLite MCP (Stdio)",
                transport = "stdio",
                command = "python3 ${pyScript.absolutePath}",
                inLinux = true,
                linuxDistro = "debian",
                enabled = true,
                source = "builtin",
                cachedTools = defaultTools,
                lastStatus = "Siap (3 tools)"
            ),
            McpConnectorConfig(
                id = "context7",
                name = "Context7 Live Docs MCP (HTTP)",
                transport = "http",
                url = "https://mcp.context7.com/mcp",
                enabled = false,
                source = "builtin",
                cachedTools = emptyList(),
                lastStatus = "Opsional (Aktifkan & Sync untuk pakai)"
            )
        )
        saveConnectors(initial)
    }
}
