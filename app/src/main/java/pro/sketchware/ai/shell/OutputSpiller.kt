package pro.sketchware.ai.shell

import pro.sketchware.ai.agent.AgentMemoryStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * When a tool output is too large to inline, this writes the full text to a private file and returns
 * a truncated preview plus the file path, so the agent can read the rest on demand.
 */
object OutputSpiller {

    private const val PREVIEW_CHARS = 8_000

    fun maybeSpill(scId: String, label: String, text: String): String {
        if (text.length <= PREVIEW_CHARS) return text
        return try {
            val dir = File(AgentMemoryStore.getAgentDir(scId), "shell-output").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safe = label.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val file = File(dir, "$safe-$stamp.log")
            file.writeText(text)
            text.take(PREVIEW_CHARS) +
                "\n\n... (${text.length} chars total, dipotong. Output lengkap: ${file.absolutePath})"
        } catch (_: Throwable) {
            text.take(PREVIEW_CHARS) + "\n... (${text.length} chars total)"
        }
    }
}
