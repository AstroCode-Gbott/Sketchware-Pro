package pro.sketchware.ai.observe

import a.a.a.lC
import a.a.a.yB
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import pro.sketchware.ai.agent.AgentChatEntry
import pro.sketchware.ai.agent.AgentMemoryStore
import pro.sketchware.ai.agent.AgentOrchestrator
import pro.sketchware.ai.agent.ChatEntryType
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RuntimeLogHarness {

    private const val ACTION_DEBUG_LOG = "pro.sketchware.ACTION_NEW_DEBUG_LOG"
    private const val MAX_LOG_LINES = 160

    @Volatile
    private var registered = false

    private val logsByScId = ConcurrentHashMap<String, ArrayDeque<String>>()
    private val latestCrashByScId = ConcurrentHashMap<String, String>()

    fun ensureRegistered(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            try {
                val appCtx = context.applicationContext
                val filter = IntentFilter(ACTION_DEBUG_LOG)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent == null || intent.action != ACTION_DEBUG_LOG) return
                        val pkg = intent.getStringExtra("packageName").orEmpty().trim()
                        val logText = intent.getStringExtra("log").orEmpty()
                        val isExplicitCrash = intent.getBooleanExtra("isCrash", false)
                        if (logText.isBlank()) return
                        handleIncomingLog(pkg, logText, isExplicitCrash)
                    }
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appCtx.registerReceiver(receiver, filter)
                }
                registered = true
            } catch (_: Throwable) {
            }
        }
    }

    private fun handleIncomingLog(packageName: String, logText: String, isExplicitCrash: Boolean) {
        val scId = resolveScIdByPackage(packageName) ?: return
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val ring = logsByScId.getOrPut(scId) { ArrayDeque() }
        synchronized(ring) {
            for (line in logText.lines()) {
                if (line.isBlank()) continue
                if (ring.size >= MAX_LOG_LINES) {
                    ring.removeFirst()
                }
                ring.addLast("[$timestamp] $line")
            }
        }

        val looksLikeCrash = isExplicitCrash ||
            logText.contains("FATAL EXCEPTION") ||
            (logText.contains("Exception") && logText.contains("\tat ") && (packageName.isEmpty() || logText.contains(packageName)))

        if (looksLikeCrash) {
            val enrichedCrash = enrichCrashWithSourceContext(scId, packageName, logText)
            latestCrashByScId[scId] = enrichedCrash
            saveCrashToDisk(scId, enrichedCrash)

            val crashEntry = AgentChatEntry(
                id = UUID.randomUUID().toString(),
                type = ChatEntryType.ERROR,
                title = "💥 Runtime Crash Terdeteksi ($packageName)",
                body = enrichedCrash.take(1600) +
                    "\n\nTip: Ketik 'Perbaiki crash' agar AI Agent otomatis memperbaiki baris kode yang menyebabkan Force Close."
            )
            AgentMemoryStore.appendChatEntry(scId, crashEntry)
            AgentOrchestrator.notifyExternalChatEntry(scId, crashEntry)
        }
    }

    fun getLatestUnresolvedCrash(scId: String): String? {
        val inMem = latestCrashByScId[scId]
        if (!inMem.isNullOrBlank()) return inMem
        val f = getCrashFile(scId)
        if (f.exists() && f.isFile) {
            val text = try {
                f.readText()
            } catch (_: Throwable) {
                ""
            }
            if (text.isNotBlank()) {
                latestCrashByScId[scId] = text
                return text
            }
        }
        return null
    }

    fun clearCrashReport(scId: String) {
        latestCrashByScId.remove(scId)
        try {
            val f = getCrashFile(scId)
            if (f.exists()) f.delete()
        } catch (_: Throwable) {
        }
    }

    fun inspectRuntimeLogs(scId: String, clearCrashAfterRead: Boolean = false): String {
        val info = lC.b(scId)
        val pkgName = if (info != null) yB.c(info, "my_sc_pkg_name").orEmpty() else ""

        val sb = StringBuilder()
        sb.appendLine("=== RUNTIME LOGS & CRASH HARNESS (sc_id=$scId, package=$pkgName) ===")

        val crash = getLatestUnresolvedCrash(scId)
        if (!crash.isNullOrBlank()) {
            sb.appendLine("\n[UNRESOLVED RUNTIME CRASH STACK TRACE]")
            sb.appendLine(crash)
            if (clearCrashAfterRead) {
                clearCrashReport(scId)
            }
        } else {
            sb.appendLine("\n[STATUS CRASH]: Tidak ada runtime crash yang tercatat untuk build terbaru.")
        }

        val ring = logsByScId[scId]
        if (ring != null && ring.isNotEmpty()) {
            sb.appendLine("\n[CAPTURED BROADCAST LOGS (${ring.size} lines)]")
            synchronized(ring) {
                ring.forEach { sb.appendLine(it) }
            }
        }

        val sysLogcat = readSystemLogcatSnippet(pkgName)
        if (sysLogcat.isNotBlank()) {
            sb.appendLine("\n[SYSTEM LOGCAT RECENT ERRORS]")
            sb.appendLine(sysLogcat)
        }

        return sb.toString().trim()
    }

    private fun enrichCrashWithSourceContext(scId: String, packageName: String, stackTrace: String): String {
        val sb = StringBuilder()
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        sb.appendLine("Waktu Crash: $timeStr")
        sb.appendLine("Package    : $packageName")
        sb.appendLine("--- Stack Trace ---")
        sb.appendLine(stackTrace.trim())

        // Extract lines like (MainActivity.java:245) to show exact generated Java source context
        val regex = Regex("\\(([A-Za-z0-9_]+\\.java):(\\d+)\\)")
        val matches = regex.findAll(stackTrace).toList().take(2)
        val myscJavaDir = File(
            Environment.getExternalStorageDirectory(),
            ".sketchware/mysc/$scId/app/src/main/java/${packageName.replace('.', '/')}"
        )
        for (m in matches) {
            val fileName = m.groupValues[1]
            val lineNum = m.groupValues[2].toIntOrNull() ?: continue
            val javaFile = File(myscJavaDir, fileName)
            if (javaFile.exists() && javaFile.isFile) {
                try {
                    val lines = javaFile.readLines()
                    val start = (lineNum - 6).coerceAtLeast(1)
                    val end = (lineNum + 6).coerceAtMost(lines.size)
                    sb.appendLine("\n--- Source Context: $fileName (around line $lineNum) ---")
                    for (i in start..end) {
                        val marker = if (i == lineNum) ">> " else "   "
                        sb.appendLine("$marker$i: ${lines[i - 1]}")
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return sb.toString().trim()
    }

    private fun readSystemLogcatSnippet(packageName: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "120", "*:E"))
            val lines = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLines() }
            val filtered = if (packageName.isNotBlank()) {
                lines.filter { it.contains(packageName) || it.contains("AndroidRuntime") }
            } else {
                lines.filter { it.contains("AndroidRuntime") }
            }
            filtered.takeLast(35).joinToString("\n")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun resolveScIdByPackage(packageName: String): String? {
        if (packageName.isBlank()) return null
        val list = lC.a() ?: return null
        return list.mapNotNull { map ->
            val pkg = yB.c(map, "my_sc_pkg_name")
            val id = yB.c(map, "sc_id")
            if (pkg == packageName && !id.isNullOrBlank()) id else null
        }.maxByOrNull { it.toIntOrNull() ?: 0 }
    }

    private fun getCrashFile(scId: String): File {
        return File(AgentMemoryStore.getAgentDir(scId), "latest_crash.txt")
    }

    private fun saveCrashToDisk(scId: String, report: String) {
        try {
            getCrashFile(scId).writeText(report)
        } catch (_: Throwable) {
        }
    }
}
