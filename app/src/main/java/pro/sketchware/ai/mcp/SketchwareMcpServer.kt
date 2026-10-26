package pro.sketchware.ai.mcp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import pro.sketchware.ai.agent.AgentOrchestrator
import pro.sketchware.ai.config.AiConfigManager
import pro.sketchware.ai.observe.RuntimeLogHarness
import pro.sketchware.ai.project.ProjectGateway
import pro.sketchware.ai.tools.SketchwareToolRegistry
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gelombang 2: Sketchware Pro Live MCP (Model Context Protocol) & External Agent Bridge Server.
 *
 * Allows external AI Agents (Antigravity, Claude Code, Cursor, Gemini CLI, OpenAI Agents, or custom scripts)
 * to inspect, create, design, code, compile, and self-heal Sketchware Pro projects directly on the phone
 * over ADB (`adb forward tcp:8765 tcp:8765` -> `http://127.0.0.1:8765/mcp`) or Wi-Fi LAN (`http://<ip>:8765/mcp`)
 * WITHOUT requiring an on-device LLM API key.
 */
object SketchwareMcpServer {

    private const val TAG = "SketchwareMcpServer"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val running = AtomicBoolean(false)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    var boundPort: Int = 8765
        private set

    @Volatile
    private var appContext: Context? = null

    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val workerPool = Executors.newCachedThreadPool()

    @JvmStatic
    fun isRunning(): Boolean = running.get() && serverSocket?.isClosed == false

    @JvmStatic
    fun ensureStarted(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        RuntimeLogHarness.ensureRegistered(appCtx)
        val config = AiConfigManager(appCtx)
        if (!config.mcpServerEnabled) {
            stop()
            return
        }
        val desiredPort = config.mcpServerPort
        if (isRunning() && boundPort == desiredPort) {
            return
        }
        start(appCtx, desiredPort)
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context, port: Int = 8765): Boolean {
        stop()
        val appCtx = context.applicationContext
        appContext = appCtx
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
            serverSocket = socket
            boundPort = port
            running.set(true)
            Log.i(TAG, "Sketchware MCP Server started on 0.0.0.0:$port")

            acceptExecutor.execute {
                while (running.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        workerPool.execute { handleClient(client) }
                    } catch (e: Throwable) {
                        if (running.get()) {
                            Log.w(TAG, "Accept error: ${e.message}")
                        }
                    }
                }
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to bind Sketchware MCP Server on port $port", e)
            running.set(false)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun stop() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
    }

    @JvmStatic
    fun getLanIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress
                        if (!ip.isNullOrBlank()) return ip
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Throwable) {
            "127.0.0.1"
        }
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 180_000 // Allow up to 3 minutes for full APK builds
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()

            val request = readHttpRequest(input) ?: return
            if (request.method.equals("OPTIONS", ignoreCase = true)) {
                writeHttpResponse(output, 204, "application/json", "")
                return
            }

            try {
                routeHttpRequest(request, output)
            } catch (e: Throwable) {
                val errJson = JsonObject().apply {
                    addProperty("ok", false)
                    addProperty("error", "${e.javaClass.simpleName}: ${e.message}")
                }
                writeHttpResponse(output, 500, "application/json; charset=utf-8", gson.toJson(errJson))
            }
        }
    }

    private fun routeHttpRequest(req: HttpRequest, output: OutputStream) {
        val ctx = appContext ?: throw IllegalStateException("ApplicationContext belum siap.")
        val cleanPath = req.path.substringBefore('?').trimEnd('/').ifEmpty { "/" }

        when {
            req.method == "GET" && (cleanPath == "/" || cleanPath == "/status" || cleanPath == "/health") -> {
                val statusObj = JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("server", "Sketchware Pro AI Agent & MCP Server (Gelombang 2)")
                    addProperty("version", "2.0.0")
                    addProperty("port", boundPort)
                    addProperty("lan_ip", getLanIpAddress())
                    addProperty("adb_endpoint", "http://127.0.0.1:$boundPort/mcp")
                    addProperty("rest_call_endpoint", "http://127.0.0.1:$boundPort/call")
                    addProperty("active_sc_id", ProjectGateway.resolveDefaultScId() ?: "")
                    add("projects_summary", JsonParser.parseString(ProjectGateway.listAllProjectsJson()))
                }
                writeHttpResponse(output, 200, "application/json; charset=utf-8", gson.toJson(statusObj))
            }

            req.method == "GET" && cleanPath == "/projects" -> {
                writeHttpResponse(output, 200, "application/json; charset=utf-8", ProjectGateway.listAllProjectsJson())
            }

            req.method == "GET" && cleanPath == "/tools" -> {
                val toolsObj = JsonObject().apply {
                    addProperty("ok", true)
                    add("tools", buildMcpToolsArray())
                }
                writeHttpResponse(output, 200, "application/json; charset=utf-8", gson.toJson(toolsObj))
            }

            req.method == "POST" && cleanPath == "/call" -> {
                val bodyObj = if (req.body.isBlank()) JsonObject() else JsonParser.parseString(req.body).asJsonObject
                val toolName = bodyObj.get("name")?.asString
                    ?: bodyObj.get("tool")?.asString
                    ?: throw IllegalArgumentException("Field 'name' atau 'tool' wajib diisi.")
                val argsObj = when {
                    bodyObj.has("arguments") && bodyObj.get("arguments").isJsonObject ->
                        bodyObj.getAsJsonObject("arguments")
                    bodyObj.has("args") && bodyObj.get("args").isJsonObject ->
                        bodyObj.getAsJsonObject("args")
                    else -> bodyObj.deepCopy().apply {
                        remove("name")
                        remove("tool")
                    }
                }
                if (bodyObj.has("sc_id") && !argsObj.has("sc_id")) {
                    argsObj.addProperty("sc_id", bodyObj.get("sc_id").asString)
                }
                val agentName = bodyObj.get("agent")?.asString ?: "External AI Agent"
                val execResult = executeUnifiedTool(ctx, toolName, argsObj, agentName)
                val resp = JsonObject().apply {
                    addProperty("ok", !execResult.isError)
                    addProperty("tool", toolName)
                    addProperty("sc_id", execResult.scId)
                    addProperty("summary", execResult.summary)
                    addProperty("output", execResult.output)
                }
                writeHttpResponse(output, 200, "application/json; charset=utf-8", gson.toJson(resp))
            }

            req.method == "POST" && (cleanPath == "/mcp" || cleanPath == "/") -> {
                handleMcpJsonRpc(ctx, req.body, output)
            }

            else -> {
                val notFound = JsonObject().apply {
                    addProperty("ok", false)
                    addProperty("error", "Endpoint tidak ditemukan: ${req.method} $cleanPath")
                }
                writeHttpResponse(output, 404, "application/json; charset=utf-8", gson.toJson(notFound))
            }
        }
    }

    private fun handleMcpJsonRpc(ctx: Context, rawBody: String, output: OutputStream) {
        val parsed = JsonParser.parseString(rawBody)
        if (!parsed.isJsonObject) {
            writeHttpResponse(output, 400, "application/json; charset=utf-8", """{"error":"Invalid JSON-RPC payload"}""")
            return
        }
        val reqObj = parsed.asJsonObject
        val idElement: JsonElement? = reqObj.get("id")
        val method = reqObj.get("method")?.asString.orEmpty()
        val params = if (reqObj.has("params") && reqObj.get("params").isJsonObject) {
            reqObj.getAsJsonObject("params")
        } else {
            JsonObject()
        }

        // JSON-RPC notifications have no "id"
        if (idElement == null || method.startsWith("notifications/")) {
            writeHttpResponse(output, 202, "application/json; charset=utf-8", "")
            return
        }

        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", idElement)
        }

        try {
            val resultElement: JsonElement = when (method) {
                "initialize" -> JsonObject().apply {
                    addProperty("protocolVersion", "2024-11-05")
                    add("capabilities", JsonObject().apply {
                        add("tools", JsonObject().apply { addProperty("listChanged", true) })
                        add("resources", JsonObject().apply { addProperty("listChanged", true) })
                    })
                    add("serverInfo", JsonObject().apply {
                        addProperty("name", "sketchware-pro-mcp")
                        addProperty("version", "2.0.0")
                    })
                }

                "ping" -> JsonObject()

                "tools/list" -> JsonObject().apply {
                    add("tools", buildMcpToolsArray())
                }

                "tools/call" -> {
                    val toolName = params.get("name")?.asString
                        ?: throw IllegalArgumentException("Parameter 'name' wajib diisi pada tools/call.")
                    val args = if (params.has("arguments") && params.get("arguments").isJsonObject) {
                        params.getAsJsonObject("arguments")
                    } else {
                        JsonObject()
                    }
                    val exec = executeUnifiedTool(ctx, toolName, args, "MCP Agent")
                    JsonObject().apply {
                        add("content", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", exec.output)
                            })
                        })
                        addProperty("isError", exec.isError)
                    }
                }

                "resources/list" -> JsonObject().apply {
                    add("resources", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("uri", "sketchware://projects")
                            addProperty("name", "Daftar Semua Proyek Sketchware di HP")
                            addProperty("mimeType", "application/json")
                        })
                        val activeScId = ProjectGateway.resolveDefaultScId()
                        if (!activeScId.isNullOrBlank()) {
                            add(JsonObject().apply {
                                addProperty("uri", "sketchware://project/$activeScId/overview")
                                addProperty("name", "Overview Proyek Aktif ($activeScId)")
                                addProperty("mimeType", "application/json")
                            })
                        }
                    })
                }

                "resources/read" -> {
                    val uri = params.get("uri")?.asString.orEmpty()
                    val textContent = when {
                        uri == "sketchware://projects" -> ProjectGateway.listAllProjectsJson()
                        uri.startsWith("sketchware://project/") -> {
                            val scId = uri.removePrefix("sketchware://project/").substringBefore("/")
                            ProjectGateway.getProjectSummary(scId)
                        }
                        else -> throw IllegalArgumentException("URI resource tidak dikenal: $uri")
                    }
                    JsonObject().apply {
                        add("contents", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("uri", uri)
                                addProperty("mimeType", "application/json")
                                addProperty("text", textContent)
                            })
                        })
                    }
                }

                else -> throw IllegalArgumentException("Method MCP tidak didukung: $method")
            }
            response.add("result", resultElement)
        } catch (e: Throwable) {
            response.add("error", JsonObject().apply {
                addProperty("code", -32603)
                addProperty("message", "${e.javaClass.simpleName}: ${e.message}")
            })
        }

        writeHttpResponse(output, 200, "application/json; charset=utf-8", gson.toJson(response))
    }

    private data class UnifiedExecResult(
        val scId: String,
        val summary: String,
        val output: String,
        val isError: Boolean
    )

    private fun executeUnifiedTool(
        ctx: Context,
        toolName: String,
        args: JsonObject,
        agentName: String
    ): UnifiedExecResult {
        return try {
            when (toolName) {
                "list_all_projects" -> {
                    val out = ProjectGateway.listAllProjectsJson()
                    UnifiedExecResult(
                        scId = ProjectGateway.resolveDefaultScId().orEmpty(),
                        summary = "Membaca daftar seluruh proyek di HP",
                        output = out,
                        isError = false
                    )
                }

                "create_project_from_zero" -> {
                    val appName = args.get("app_name")?.asString?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: throw IllegalArgumentException("Parameter 'app_name' wajib diisi.")
                    val pkgName = args.get("package_name")?.asString?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "com.ai.${appName.lowercase().replace(Regex("[^a-z0-9]"), "").ifEmpty { "app" }}"
                    val projName = args.get("project_name")?.asString?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: appName
                    val verName = args.get("version_name")?.asString?.trim()?.ifEmpty { "1.0" } ?: "1.0"
                    val verCode = args.get("version_code")?.asString?.trim()?.ifEmpty { "1" } ?: "1"
                    val openInUi = if (args.has("open_in_ui")) args.get("open_in_ui").asBoolean else true

                    val newScId = ProjectGateway.createProjectFromZero(
                        context = ctx,
                        appName = appName,
                        packageName = pkgName,
                        projectName = projName,
                        versionCode = verCode,
                        versionName = verName,
                        colorPrimaryHex = args.get("color_primary")?.asString ?: "#FF2196F3",
                        colorPrimaryDarkHex = args.get("color_primary_dark")?.asString ?: "#FF1976D2",
                        colorAccentHex = args.get("color_accent")?.asString ?: "#FFFF4081"
                    )
                    if (openInUi) {
                        ProjectGateway.openProjectInUi(ctx, newScId, "main.xml")
                    }
                    val out = gson.toJson(
                        mapOf(
                            "status" to "created",
                            "sc_id" to newScId,
                            "app_name" to appName,
                            "package_name" to pkgName,
                            "project_name" to projName,
                            "opened_in_ui" to openInUi,
                            "overview" to ProjectGateway.getProjectSummary(newScId)
                        )
                    )
                    val summary = "Membuat proyek baru '$appName' (sc_id=$newScId)"
                    AgentOrchestrator.recordExternalAgentAction(newScId, agentName, toolName, summary, out, false)
                    UnifiedExecResult(newScId, summary, out, false)
                }

                "open_project_in_ui" -> {
                    val scId = args.get("sc_id")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ProjectGateway.resolveDefaultScId()
                        ?: throw IllegalArgumentException("Parameter 'sc_id' wajib diisi.")
                    val xmlName = args.get("xml_name")?.asString?.trim()
                    val msg = ProjectGateway.openProjectInUi(ctx, scId, xmlName)
                    AgentOrchestrator.recordExternalAgentAction(scId, agentName, toolName, msg, msg, false)
                    UnifiedExecResult(scId, msg, msg, false)
                }

                "read_agent_session_and_queue" -> {
                    val scId = args.get("sc_id")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ProjectGateway.resolveDefaultScId()
                        ?: throw IllegalArgumentException("Parameter 'sc_id' wajib diisi.")
                    val chat = pro.sketchware.ai.agent.AgentMemoryStore.loadChatEntries(scId)
                    val plan = pro.sketchware.ai.agent.AgentMemoryStore.loadPlan(scId)
                    val memory = pro.sketchware.ai.agent.AgentMemoryStore.loadMemoryNotes(scId)
                    val out = gson.toJson(
                        mapOf(
                            "sc_id" to scId,
                            "plan" to plan,
                            "memory_notes" to memory,
                            "recent_chat_entries" to chat.takeLast(20)
                        )
                    )
                    UnifiedExecResult(scId, "Membaca sesi & antrean instruksi proyek #$scId", out, false)
                }

                "post_agent_message_to_phone" -> {
                    val scId = args.get("sc_id")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ProjectGateway.resolveDefaultScId()
                        ?: throw IllegalArgumentException("Parameter 'sc_id' wajib diisi.")
                    val title = args.get("title")?.asString?.trim()?.ifEmpty { "🌐 $agentName" } ?: "🌐 $agentName"
                    val message = args.get("message")?.asString?.trim().orEmpty()
                    val entry = pro.sketchware.ai.agent.AgentChatEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        type = pro.sketchware.ai.agent.ChatEntryType.ASSISTANT,
                        title = title,
                        body = message
                    )
                    pro.sketchware.ai.agent.AgentMemoryStore.appendChatEntry(scId, entry)
                    AgentOrchestrator.recordExternalAgentAction(scId, agentName, "message", title, message, false)
                    UnifiedExecResult(scId, "Mengirim pesan ke AI Agent Studio di HP", "Pesan terkirim ke layar HP (#$scId)", false)
                }

                else -> {
                    val scId = args.get("sc_id")?.asString?.trim()?.takeIf { it.isNotEmpty() }
                        ?: ProjectGateway.resolveDefaultScId()
                        ?: throw IllegalArgumentException(
                            "Tidak ada proyek aktif. Berikan parameter 'sc_id' atau panggil 'create_project_from_zero' / 'list_all_projects' terlebih dahulu."
                        )
                    val out = SketchwareToolRegistry.executeTool(ctx, scId, toolName, args)
                    val isErr = out.startsWith("Error:") || out.startsWith("COMPILE ERROR")
                    val summary = "Eksekusi $toolName pada proyek #$scId"
                    AgentOrchestrator.recordExternalAgentAction(
                        scId = scId,
                        agentName = agentName,
                        toolName = toolName,
                        summary = summary,
                        output = out,
                        isError = isErr
                    )
                    UnifiedExecResult(scId, summary, out, isErr)
                }
            }
        } catch (e: Throwable) {
            val fallbackScId = args.get("sc_id")?.asString ?: ProjectGateway.resolveDefaultScId().orEmpty()
            val errText = "ERROR executing $toolName: ${e.javaClass.simpleName}: ${e.message}"
            if (fallbackScId.isNotBlank()) {
                AgentOrchestrator.recordExternalAgentAction(
                    scId = fallbackScId,
                    agentName = agentName,
                    toolName = toolName,
                    summary = "Gagal menjalankan $toolName",
                    output = errText,
                    isError = true
                )
            }
            UnifiedExecResult(fallbackScId, "Error pada $toolName", errText, true)
        }
    }

    private fun buildMcpToolsArray(): JsonArray {
        val array = JsonArray()

        // 1. Global Tool: list_all_projects
        array.add(JsonObject().apply {
            addProperty("name", "list_all_projects")
            addProperty(
                "description",
                "Menampilkan daftar seluruh proyek Sketchware Pro yang ada di HP beserta sc_id, nama aplikasi, package name, dan sc_id yang sedang aktif."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject())
            })
        })

        // 2. Global Tool: create_project_from_zero
        array.add(JsonObject().apply {
            addProperty("name", "create_project_from_zero")
            addProperty(
                "description",
                "Membuat proyek aplikasi Android baru dari 0 di Sketchware Pro dan langsung membukanya di layar HP."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("app_name", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Nama aplikasi Android (misal 'Smart Kasir AI')")
                    })
                    add("package_name", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Package name Android (misal 'com.ai.smartkasir')")
                    })
                    add("project_name", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Nama workspace proyek (opsional)")
                    })
                    add("color_primary", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Warna utama Hex (misal '#FF2196F3')")
                    })
                    add("color_primary_dark", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Warna status bar Hex (misal '#FF1976D2')")
                    })
                    add("color_accent", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Warna aksen Hex (misal '#FFFF4081')")
                    })
                    add("open_in_ui", JsonObject().apply {
                        addProperty("type", "boolean")
                        addProperty("description", "Buka langsung proyek di layar editor HP (default true)")
                    })
                })
                add("required", JsonArray().apply { add("app_name") })
            })
        })

        // 3. Global Tool: open_project_in_ui
        array.add(JsonObject().apply {
            addProperty("name", "open_project_in_ui")
            addProperty(
                "description",
                "Membuka proyek sc_id tertentu di layar DesignActivity HP atau berpindah ke layar xml_name tertentu secara live."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("sc_id", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "ID proyek Sketchware (misal '601')")
                    })
                    add("xml_name", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Opsional: nama file layout yang ingin ditampilkan (misal 'main.xml')")
                    })
                })
                add("required", JsonArray().apply { add("sc_id") })
            })
        })

        // 4. Global Tool: read_agent_session_and_queue
        array.add(JsonObject().apply {
            addProperty("name", "read_agent_session_and_queue")
            addProperty(
                "description",
                "Membaca riwayat chat, instruksi user di HP, Task Plan, dan Working Memory untuk proyek sc_id."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("sc_id", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "ID proyek Sketchware (opsional jika proyek sudah aktif)")
                    })
                })
            })
        })

        // 5. Global Tool: post_agent_message_to_phone
        array.add(JsonObject().apply {
            addProperty("name", "post_agent_message_to_phone")
            addProperty(
                "description",
                "Mengirim pesan/penjelasan dari AI Agent eksternal langsung ke jendela AI Agent Studio di layar HP."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("sc_id", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "ID proyek Sketchware (opsional jika proyek sudah aktif)")
                    })
                    add("title", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Judul pesan (misal '🌐 Antigravity AI Agent')")
                    })
                    add("message", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "Isi pesan atau laporan progres untuk ditampilkan di HP")
                    })
                })
                add("required", JsonArray().apply { add("message") })
            })
        })

        // 6..23. All 18 Project Tools from SketchwareToolRegistry (with optional sc_id injected)
        for (spec in SketchwareToolRegistry.getToolsForMode(pro.sketchware.ai.config.AgentAutonomyMode.AUTOPILOT)) {
            val propsObj = JsonObject()
            val reqArr = JsonArray()
            propsObj.add("sc_id", JsonObject().apply {
                addProperty("type", "string")
                addProperty(
                    "description",
                    "ID proyek Sketchware (misal '601'). Opsional jika proyek sudah aktif/terbuka di layar HP."
                )
            })
            for (param in spec.parameters) {
                propsObj.add(param.name, JsonObject().apply {
                    addProperty("type", param.type)
                    addProperty("description", param.description)
                    if (!param.enumValues.isNullOrEmpty()) {
                        add("enum", JsonArray().apply { param.enumValues.forEach { add(it) } })
                    }
                })
                if (param.required) {
                    reqArr.add(param.name)
                }
            }
            val inputSchema = JsonObject().apply {
                addProperty("type", "object")
                add("properties", propsObj)
                if (reqArr.size() > 0) {
                    add("required", reqArr)
                }
            }
            array.add(JsonObject().apply {
                addProperty("name", spec.name)
                addProperty("description", spec.description)
                add("inputSchema", inputSchema)
            })
        }

        return array
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String
    )

    private fun readHttpRequest(input: InputStream): HttpRequest? {
        val requestLine = readAsciiLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null
        val method = parts[0].trim().uppercase()
        val path = parts[1].trim()

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val n = input.read(buf, readTotal, contentLength - readTotal)
                if (n <= 0) break
                readTotal += n
            }
            String(buf, 0, readTotal, StandardCharsets.UTF_8)
        } else {
            ""
        }

        return HttpRequest(method, path, headers, body)
    }

    private fun readAsciiLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var sawByte = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sawByte) baos.toString(StandardCharsets.UTF_8.name()) else null
            }
            sawByte = true
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    private fun writeHttpResponse(
        output: OutputStream,
        statusCode: Int,
        contentType: String,
        body: String
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            202 -> "Accepted"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, Mcp-Session-Id\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            output.write(bodyBytes)
        }
        output.flush()
    }
}
