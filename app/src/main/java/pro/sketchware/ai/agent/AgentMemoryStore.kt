package pro.sketchware.ai.agent

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import pro.sketchware.SketchApplication
import pro.sketchware.utility.FileUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists Long-Horizon Agent state (`plan.json`, `memory.md`, `chat.json`, `journal.jsonl`)
 * inside the app's private files directory (`files/agent/<sc_id>/`) so chat transcripts, tool
 * outputs and working memory are not world-readable on shared external storage.
 *
 * Older builds wrote to `.sketchware/agent/<sc_id>/` on external storage; that data is migrated
 * on first access and the world-readable copy is removed.
 */
object AgentMemoryStore {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val compactGson: Gson = GsonBuilder().disableHtmlEscaping().create()

    @Volatile
    private var appContext: Context? = null

    private val migratedIds = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Returns the private agent directory for [scId], creating it if needed and migrating any
     * legacy external-storage contents exactly once per session.
     */
    @JvmStatic
    fun getAgentDir(scId: String): File {
        val safeId = scId.ifBlank { "global" }
        val ctx = appContext ?: SketchApplication.getContext()
        val dir = if (ctx != null) {
            File(ctx.filesDir, "agent/$safeId")
        } else {
            // Last-resort fallback; should not happen because SketchApplication initializes us.
            File(Environment.getExternalStorageDirectory(), ".sketchware/agent/$safeId")
        }
        if (!dir.exists()) dir.mkdirs()
        migrateLegacyExternalDir(safeId, dir)
        return dir
    }

    private fun migrateLegacyExternalDir(safeId: String, newDir: File) {
        if (!migratedIds.add(safeId)) return
        try {
            val legacy = File(Environment.getExternalStorageDirectory(), ".sketchware/agent/$safeId")
            if (!legacy.exists() || legacy.canonicalPath == newDir.canonicalPath) return
            legacy.listFiles()?.forEach { src ->
                val dst = File(newDir, src.name)
                if (!dst.exists()) {
                    if (src.isDirectory) {
                        src.copyRecursively(dst, overwrite = false)
                    } else {
                        src.copyTo(dst, overwrite = false)
                    }
                }
            }
            // Remove the world-readable copy now that private storage holds the data.
            legacy.deleteRecursively()
        } catch (_: Throwable) {
        }
    }

    fun loadPlan(scId: String): AgentPlan {
        val file = File(getAgentDir(scId), "plan.json")
        if (!file.exists()) return AgentPlan()
        return try {
            gson.fromJson(FileUtil.readFile(file.absolutePath), AgentPlan::class.java) ?: AgentPlan()
        } catch (_: Exception) {
            AgentPlan()
        }
    }

    fun savePlan(scId: String, plan: AgentPlan) {
        val file = File(getAgentDir(scId), "plan.json")
        FileUtil.writeFile(file.absolutePath, gson.toJson(plan))
    }

    fun loadMemoryNotes(scId: String): String {
        val file = File(getAgentDir(scId), "memory.md")
        if (!file.exists()) return ""
        return FileUtil.readFile(file.absolutePath)
    }

    fun saveMemoryNotes(scId: String, notes: String, append: Boolean = false) {
        val file = File(getAgentDir(scId), "memory.md")
        val updated = if (append && file.exists()) {
            val current = FileUtil.readFile(file.absolutePath).trim()
            if (current.isEmpty()) notes.trim() else "$current\n\n${notes.trim()}"
        } else {
            notes.trim()
        }
        FileUtil.writeFile(file.absolutePath, updated)
    }

    fun loadChatEntries(scId: String): MutableList<AgentChatEntry> {
        val file = File(getAgentDir(scId), "chat.json")
        if (!file.exists()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<AgentChatEntry>>() {}.type
            gson.fromJson<MutableList<AgentChatEntry>>(FileUtil.readFile(file.absolutePath), type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun appendChatEntry(scId: String, entry: AgentChatEntry) {
        val list = loadChatEntries(scId)
        list.add(entry)
        val trimmed = if (list.size > 150) list.takeLast(150) else list
        val file = File(getAgentDir(scId), "chat.json")
        FileUtil.writeFile(file.absolutePath, gson.toJson(trimmed))

        // Append to journal.jsonl
        try {
            val journalFile = File(getAgentDir(scId), "journal.jsonl")
            journalFile.appendText(compactGson.toJson(entry) + "\n")
        } catch (_: Exception) {
        }
    }

    fun clearSession(scId: String) {
        val dir = getAgentDir(scId)
        File(dir, "chat.json").delete()
        File(dir, "plan.json").delete()
    }
}
