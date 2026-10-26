package pro.sketchware.ai.extensions

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import pro.sketchware.ai.shell.ArchiveExtractor
import pro.sketchware.ai.tools.WebTools
import java.io.File

data class PluginSlashCommand(
    val command: String,
    val title: String,
    val subtitle: String,
    val prompt: String
)

data class AgentPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val enabled: Boolean,
    val dirPath: String,
    val slashCommands: List<PluginSlashCommand> = emptyList(),
    val mcpConnectorIds: List<String> = emptyList(),
    val skillIds: List<String> = emptyList(),
    val systemPromptAppend: String = ""
)

/**
 * Manages AI Agent Plugins under `.sketchware/ai/plugins/<plugin-id>/plugin.json`.
 *
 * A plugin is a self-contained bundle (matching the Claude Code / Antigravity plugin concept)
 * that can package together:
 * - Skills (`skills/<skill-id>/SKILL.md`)
 * - MCP Connectors (`mcpConnectors`)
 * - Slash Commands (`slashCommands`)
 * - Domain Rules (`systemPromptAppend`)
 *
 * Toggling a plugin's `enabled` state enables/disables all of its bundled skills, MCP connectors,
 * slash commands, and system prompt rules as a single unit.
 */
object PluginManager {

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private var seeded = false

    fun pluginsRootDir(): File = File(SkillManager.aiRootDir(), "plugins").apply { mkdirs() }

    @Synchronized
    fun ensureSeeded() {
        if (seeded) return
        seeded = true
        try {
            seedBuiltInPlugins()
        } catch (_: Throwable) {
        }
    }

    fun listPlugins(): List<AgentPlugin> {
        ensureSeeded()
        val root = pluginsRootDir()
        val result = mutableListOf<AgentPlugin>()
        root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
            val manifest = File(dir, "plugin.json")
            if (manifest.isFile) {
                parsePlugin(dir, manifest)?.let { result.add(it) }
            }
        }
        return result
    }

    fun listEnabledPlugins(): List<AgentPlugin> = listPlugins().filter { it.enabled }

    fun setPluginEnabled(pluginId: String, enabled: Boolean): Boolean {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(pluginId)
        val dir = File(pluginsRootDir(), cleanId)
        val manifest = File(dir, "plugin.json")
        if (!manifest.isFile) return false
        return try {
            val obj = JsonParser.parseString(manifest.readText()).asJsonObject
            obj.addProperty("enabled", enabled)
            manifest.writeText(gson.toJson(obj))
            // Also toggle any MCP connectors owned by this plugin
            val plugin = parsePlugin(dir, manifest)
            plugin?.mcpConnectorIds?.forEach { mcpId ->
                McpClientHub.setConnectorEnabled(mcpId, enabled)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun createOrUpdatePlugin(
        id: String,
        name: String,
        version: String = "1.0.0",
        description: String,
        author: String = "Sketchware AI Studio",
        enabled: Boolean = true,
        slashCommands: List<PluginSlashCommand> = emptyList(),
        mcpConnectors: List<McpConnectorConfig> = emptyList(),
        skills: List<Triple<String, String, String>> = emptyList(), // (skillId, title, markdown)
        systemPromptAppend: String = ""
    ): AgentPlugin {
        ensureSeeded()
        val cleanId = SkillManager.sanitizeId(id.ifBlank { name })
        val dir = File(pluginsRootDir(), cleanId).apply { mkdirs() }

        // Write bundled skills
        val skillsDir = File(dir, "skills")
        val skillIds = mutableListOf<String>()
        for ((sId, sName, sBody) in skills) {
            val cleanSkillId = SkillManager.sanitizeId(sId)
            skillIds.add(cleanSkillId)
            val sFolder = File(skillsDir, cleanSkillId).apply { mkdirs() }
            val mdFile = File(sFolder, "SKILL.md")
            val content = if (sBody.trimStart().startsWith("---")) {
                sBody
            } else {
                "---\nname: $sName\ndescription: Skill dari plugin $name\n---\n\n$sBody\n"
            }
            mdFile.writeText(content)
        }

        // Register bundled MCP connectors
        val mcpIds = mutableListOf<String>()
        for (mcp in mcpConnectors) {
            val saved = McpClientHub.addOrUpdateConnector(
                mcp.copy(source = "plugin:$cleanId", enabled = enabled)
            )
            mcpIds.add(saved.id)
        }

        val manifestObj = JsonObject().apply {
            addProperty("id", cleanId)
            addProperty("name", name)
            addProperty("version", version)
            addProperty("description", description)
            addProperty("author", author)
            addProperty("enabled", enabled)
            addProperty("systemPromptAppend", systemPromptAppend)
            add("mcpConnectorIds", JsonArray().apply { mcpIds.forEach { add(it) } })
            add("slashCommands", JsonArray().apply {
                for (sc in slashCommands) {
                    add(JsonObject().apply {
                        addProperty("command", if (sc.command.startsWith("/")) sc.command else "/${sc.command}")
                        addProperty("title", sc.title)
                        addProperty("subtitle", sc.subtitle)
                        addProperty("prompt", sc.prompt)
                    })
                }
            })
        }
        val manifestFile = File(dir, "plugin.json")
        manifestFile.writeText(gson.toJson(manifestObj))
        return parsePlugin(dir, manifestFile)!!
    }

    fun installPluginFromArchiveOrUrl(source: String): String {
        ensureSeeded()
        val trimmed = source.trim()
        return try {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val res = WebTools.request(trimmed)
                if (res.code >= 400) {
                    return "Error: Gagal mengunduh plugin dari $trimmed (HTTP ${res.code})"
                }
                val obj = JsonParser.parseString(res.body).asJsonObject
                val id = SkillManager.sanitizeId(obj.get("id")?.asString ?: obj.get("name")?.asString ?: "remote-plugin")
                val dir = File(pluginsRootDir(), id).apply { mkdirs() }
                if (!obj.has("enabled")) obj.addProperty("enabled", true)
                val manifest = File(dir, "plugin.json")
                manifest.writeText(gson.toJson(obj))
                val parsed = parsePlugin(dir, manifest)
                "Plugin '${parsed?.name ?: id}' berhasil dipasang dari URL ke ${dir.absolutePath}"
            } else {
                val file = File(trimmed)
                if (!file.exists()) return "Error: Berkas/folder plugin tidak ditemukan: $trimmed"
                if (file.isDirectory) {
                    val manifest = File(file, "plugin.json")
                    if (!manifest.isFile) return "Error: Folder tidak memiliki berkas plugin.json"
                    val id = SkillManager.sanitizeId(file.name)
                    val target = File(pluginsRootDir(), id)
                    file.copyRecursively(target, overwrite = true)
                    "Plugin '$id' berhasil dipasang ke ${target.absolutePath}"
                } else {
                    val temp = File(SkillManager.aiRootDir(), "tmp_plugin_${System.currentTimeMillis()}").apply { mkdirs() }
                    try {
                        ArchiveExtractor.extract(file, temp)
                        val manifest = temp.walkTopDown().firstOrNull { it.isFile && it.name == "plugin.json" }
                            ?: return "Error: Arsip ${file.name} tidak berisi plugin.json"
                        val rootDir = manifest.parentFile ?: temp
                        val obj = JsonParser.parseString(manifest.readText()).asJsonObject
                        val id = SkillManager.sanitizeId(obj.get("id")?.asString ?: rootDir.name)
                        val target = File(pluginsRootDir(), id)
                        rootDir.copyRecursively(target, overwrite = true)
                        "Plugin '$id' berhasil diekstrak dan dipasang ke ${target.absolutePath}"
                    } finally {
                        temp.deleteRecursively()
                    }
                }
            }
        } catch (t: Throwable) {
            "Error memasang plugin: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun deletePlugin(pluginId: String): Boolean {
        val cleanId = SkillManager.sanitizeId(pluginId)
        val dir = File(pluginsRootDir(), cleanId)
        if (!dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    fun getActivePluginSlashCommands(): List<PluginSlashCommand> {
        return listEnabledPlugins().flatMap { it.slashCommands }
    }

    fun buildSystemPromptSection(): String {
        val enabled = listEnabledPlugins()
        if (enabled.isEmpty()) return ""
        return buildString {
            appendLine("=== PLUGINS AKTIF (${enabled.size}) ===")
            for (p in enabled) {
                appendLine("- **${p.name}** (`${p.id}` v${p.version}): ${p.description}")
                if (p.systemPromptAppend.isNotBlank()) {
                    appendLine("  Aturan Plugin: ${p.systemPromptAppend}")
                }
            }
        }.trim()
    }

    private fun parsePlugin(dir: File, manifest: File): AgentPlugin? {
        return try {
            val obj = JsonParser.parseString(manifest.readText()).asJsonObject
            val id = SkillManager.sanitizeId(obj.get("id")?.asString ?: dir.name)
            val name = obj.get("name")?.asString ?: id
            val version = obj.get("version")?.asString ?: "1.0.0"
            val desc = obj.get("description")?.asString ?: ""
            val author = obj.get("author")?.asString ?: "Sketchware AI"
            val enabled = obj.get("enabled")?.asBoolean ?: true
            val promptAppend = obj.get("systemPromptAppend")?.asString.orEmpty()

            val mcpIds = obj.get("mcpConnectorIds")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()

            val slashList = mutableListOf<PluginSlashCommand>()
            obj.get("slashCommands")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
                if (el.isJsonObject) {
                    val sObj = el.asJsonObject
                    val cmd = sObj.get("command")?.asString.orEmpty()
                    val title = sObj.get("title")?.asString.orEmpty()
                    val sub = sObj.get("subtitle")?.asString.orEmpty()
                    val prm = sObj.get("prompt")?.asString.orEmpty()
                    if (cmd.isNotEmpty() && prm.isNotEmpty()) {
                        slashList.add(PluginSlashCommand(cmd, title.ifEmpty { cmd }, sub, prm))
                    }
                }
            }

            val skillIds = File(dir, "skills").takeIf { it.isDirectory }
                ?.listFiles()?.filter { it.isDirectory && File(it, "SKILL.md").isFile }
                ?.map { SkillManager.sanitizeId(it.name) }?.sorted() ?: emptyList()

            AgentPlugin(
                id = id,
                name = name,
                version = version,
                description = desc,
                author = author,
                enabled = enabled,
                dirPath = dir.absolutePath,
                slashCommands = slashList,
                mcpConnectorIds = mcpIds,
                skillIds = skillIds,
                systemPromptAppend = promptAppend
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun seedBuiltInPlugins() {
        val marker = File(pluginsRootDir(), ".seeded_v1")
        if (marker.isFile) return

        // 1. Firebase & Cloud Backend Plugin
        createOrUpdatePlugin(
            id = "firebase-kit",
            name = "Firebase & Cloud Backend Kit",
            version = "1.2.0",
            description = "Bundel skill & shortcut untuk mengintegrasikan Firebase Auth, Realtime Database, Firestore REST/SDK, dan Cloud Storage di Sketchware Pro.",
            enabled = true,
            slashCommands = listOf(
                PluginSlashCommand(
                    command = "/firebase-setup",
                    title = "Setup Firebase & Auth/Database",
                    subtitle = "[Plugin: Firebase Kit] Konfigurasi Firebase library, komponen & blok logika",
                    prompt = "Gunakan skill firebase-integration untuk memeriksa dan menyiapkan konfigurasi Firebase (Auth / Database / REST) di proyek ini beserta komponen dan logika bloknya."
                )
            ),
            skills = listOf(
                Triple(
                    "firebase-integration",
                    "Firebase Integration for Sketchware Pro",
                    """
                    # Firebase Integration Guide (Sketchware Pro)

                    1. Aktifkan library Firebase proyek menggunakan `configure_library(library_name="firebase", enabled=true)`.
                    2. Untuk autentikasi atau database tanpa ketergantungan Play Services berat, Anda juga dapat menggunakan **Firebase REST API** melalui komponen `RequestNetwork` ke `https://<project-id>-default-rtdb.firebaseio.com/<node>.json`.
                    3. Pastikan permission `android.permission.INTERNET` dan `android.permission.ACCESS_NETWORK_STATE` ditambahkan melalui `add_permissions`.
                    """.trimIndent()
                )
            ),
            systemPromptAppend = "Saat pengguna meminta fitur login cloud atau database realtime, tawarkan opsi Firebase SDK bawaan Sketchware atau Firebase REST API yang ringan."
        )

        // 2. GitHub & Git Workflow Plugin
        createOrUpdatePlugin(
            id = "github-toolkit",
            name = "GitHub & Git Versioning Toolkit",
            version = "1.1.0",
            description = "Integrasi Git CLI di Debian Linux untuk inisialisasi repo proyek, commit, clone library open-source dari GitHub, dan rilis.",
            enabled = true,
            slashCommands = listOf(
                PluginSlashCommand(
                    command = "/git-status",
                    title = "Cek Git & Riwayat Perubahan Proyek",
                    subtitle = "[Plugin: GitHub Toolkit] Jalankan git status/log di workspace proyek via Debian",
                    prompt = "Gunakan run_shell di Debian untuk memeriksa status repositori git pada direktori proyek ini (atau inisialisasi git jika diminta) dan tampilkan ringkasannya."
                )
            ),
            skills = listOf(
                Triple(
                    "github-repo-workflow",
                    "GitHub & Git CLI Workflow",
                    """
                    # GitHub & Git CLI Workflow (Debian Linux)

                    Gunakan `run_shell` (distro `debian`) untuk mengelola Git atau mengunduh source code dari GitHub:
                    - Clone repo referensi: `git clone --depth 1 https://github.com/<owner>/<repo>.git /tmp/<repo>`
                    - Cari contoh implementasi class Java di GitHub menggunakan `web_search` + `web_fetch` pada URL `raw.githubusercontent.com`.
                    """.trimIndent()
                )
            )
        )

        // 3. SQLite & Offline Database Pro Plugin
        createOrUpdatePlugin(
            id = "sqlite-offline-pro",
            name = "SQLite & Offline Storage Pro",
            version = "1.0.0",
            description = "Skill arsitektur database lokal SQLite & SharedPreferences JSON dengan fitur ekspor/impor CSV/JSON dan inspeksi tabel via MCP.",
            enabled = true,
            slashCommands = listOf(
                PluginSlashCommand(
                    command = "/buat-database",
                    title = "Bangun Database Lokal (SQLite / JSON)",
                    subtitle = "[Plugin: SQLite Pro] Buat sistem penyimpanan lokal CRUD lengkap di proyek",
                    prompt = "Rancang dan implementasikan penyimpanan data lokal CRUD yang persisten dan tahan banting di proyek ini, lengkap dengan UI input, daftar data, edit, dan hapus."
                )
            ),
            skills = listOf(
                Triple(
                    "sqlite-crud-helper",
                    "SQLite & JSON CRUD Storage Helper",
                    """
                    # SQLite & JSON CRUD Storage Helper

                    Untuk penyimpanan data lokal terstruktur di Sketchware Pro:
                    1. **Pendekatan Cepat & Visual**: Gunakan komponen `File` (`SharedPreferences`) + `ListMap` + blok `fileSetData` / `fileGetData` dengan serialisasi JSON (`Gson`).
                    2. **Pendekatan Database Relasional**: Buat helper class Java kustom melalui `write_custom_java_file(file_name="DatabaseHelper.java", ...)` yang meng-extend `android.database.sqlite.SQLiteOpenHelper`, lalu panggil method CRUD-nya dari event `initializeLogic` dan `onClick`.
                    """.trimIndent()
                )
            )
        )

        try {
            marker.writeText("ok")
        } catch (_: Throwable) {
        }
    }
}
