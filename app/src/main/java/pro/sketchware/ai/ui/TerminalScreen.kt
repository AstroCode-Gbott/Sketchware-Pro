package pro.sketchware.ai.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pro.sketchware.ai.shell.LinuxSubsystem
import pro.sketchware.ai.shell.TerminalSession
import pro.sketchware.ai.shell.TerminalSessionManager
import pro.sketchware.utility.SketchwareUtil

/**
 * Interactive terminal window for the AI Agent's persistent shell sessions. It shares the same
 * sessions as the `terminal_*` tools, so output typed here is also visible to the agent and vice versa.
 */
class TerminalScreen(
    private val activity: Activity,
    private val scId: String
) : Dialog(activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var session: TerminalSession? = null
    private var lastSessionId: String? = null
    private var lastRenderedSnapshot: String = ""
    private val headerLog = StringBuilder()

    private lateinit var outputView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var inputView: EditText
    private lateinit var statusView: TextView

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 300L)
        }
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        window?.setBackgroundDrawable(ColorDrawable(BG))
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Attach to an existing live session from the agent if present; otherwise start Linux (Debian) if ready, else Host.
        val existingLive = TerminalSessionManager.list().lastOrNull { it.isAlive }
        if (existingLive != null) {
            session = existingLive
            lastSessionId = existingLive.id
            appendHeaderLog("[attached] terhubung ke sesi aktif ${existingLive.id} (${existingLive.engine})")
            refresh(force = true)
        } else if (LinuxSubsystem.isReady()) {
            startSession("linux")
        } else {
            startSession("host")
        }
        handler.post(poll)
    }

    override fun dismiss() {
        handler.removeCallbacks(poll)
        super.dismiss()
    }

    private fun buildUi(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(activity).apply {
            text = "Terminal Agent"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)
        header.addView(actionButton("Host") { startSession("host") })
        header.addView(actionButton("Linux") { startSession("linux") })
        header.addView(actionButton("Root") { startSession("root") })
        header.addView(actionButton("Tutup") { dismiss() })
        root.addView(header)

        statusView = TextView(activity).apply {
            setTextColor(0xFF9E9E9E.toInt())
            textSize = 11f
            setPadding(0, dp(6), 0, dp(6))
            text = "memulai..."
        }
        root.addView(statusView)

        scrollView = ScrollView(activity).apply {
            setBackgroundColor(0xFF0D0D0D.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        outputView = TextView(activity).apply {
            setTextColor(0xFFB9F6CA.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        scrollView.addView(outputView)
        root.addView(scrollView)

        val inputRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        inputView = EditText(activity).apply {
            hint = "kirim perintah…"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF757575.toInt())
            textSize = 13f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = fieldBackground()
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, _, _ ->
                sendInput()
                true
            }
        }
        inputRow.addView(inputView)
        val send = actionButton("Kirim") { sendInput() }
        inputRow.addView(send)
        root.addView(inputRow)

        return root
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button {
        return Button(activity).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(0xFF37474F.toInt())
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            setOnClickListener { onClick() }
        }
    }

    private fun fieldBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(6).toFloat()
        setColor(0xFF1B1B1B.toInt())
        setStroke(dp(1), 0xFF424242.toInt())
    }

    private fun startSession(engine: String) {
        val existing = session
        if (existing != null && existing.isAlive && engineMatches(engine)) {
            refresh(force = true)
            return
        }
        // Check if there is already a live session matching this engine in TerminalSessionManager
        val matchingLive = TerminalSessionManager.list().lastOrNull {
            it.isAlive && when (engine) {
                "host" -> it.engine == "host"
                "root" -> it.engine == "root"
                "linux" -> it.engine.startsWith("linux:")
                else -> false
            }
        }
        if (matchingLive != null) {
            session = matchingLive
            lastSessionId = matchingLive.id
            lastRenderedSnapshot = ""
            appendHeaderLog("[${matchingLive.engine}] beralih ke sesi ${matchingLive.id}")
            refresh(force = true)
            return
        }
        try {
            val cwd = defaultWorkspaceDir()
            val started = TerminalSessionManager.start(activity, engine, null, cwd, true)
            session = started
            lastSessionId = started.id
            lastRenderedSnapshot = ""
            appendHeaderLog("[${started.engine}] sesi ${started.id} dimulai (cwd=${cwd.absolutePath})")
            refresh(force = true)
        } catch (t: Throwable) {
            appendHeaderLog("[error] gagal memulai '$engine': ${t.javaClass.simpleName}: ${t.message}")
            refresh(force = true)
        }
    }

    private fun engineMatches(engine: String): Boolean {
        val e = session?.engine ?: return false
        return when (engine) {
            "host" -> e == "host"
            "root" -> e == "root"
            "linux" -> e.startsWith("linux:")
            else -> false
        }
    }

    private fun sendInput() {
        val text = inputView.text?.toString().orEmpty()
        val active = session
        if (active == null || !active.isAlive) {
            appendHeaderLog("[session] tidak ada sesi aktif; tekan Linux/Host/Root.")
            refresh(force = true)
            return
        }
        active.write(text + "\n")
        inputView.setText("")
        if (text.isNotBlank()) {
            // Also echo into session buffer if not running under PTY echo so both UI and agent see it
            if (!active.engine.contains("pty")) {
                active.append("\n$ $text\n")
            }
        }
        refresh(force = true)
    }

    private fun refresh(force: Boolean = false) {
        val active = session
        if (active == null) {
            if (force) {
                outputView.text = headerLog.toString()
            }
            statusView.text = "belum ada sesi | distro default: ${LinuxSubsystem.defaultDistro() ?: "-"}"
            return
        }
        // Use snapshot() instead of readNew() so the UI never steals incremental output from the AI agent's terminal_read tool!
        val rawSnap = active.snapshot()
        if (force || rawSnap != lastRenderedSnapshot) {
            lastRenderedSnapshot = rawSnap
            val cleaned = stripAnsi(rawSnap)
            val combined = if (headerLog.isNotEmpty()) {
                headerLog.toString() + "\n" + cleaned
            } else {
                cleaned
            }
            outputView.text = if (combined.length > MAX_DISPLAY) combined.takeLast(MAX_DISPLAY) else combined
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
        statusView.text = "${active.engine} | ${active.id} | " +
            (if (active.isAlive) "berjalan" else "selesai(exit=${active.exitCode ?: "-"})") +
            " | default: ${LinuxSubsystem.defaultDistro() ?: "-"}"
    }

    private fun appendHeaderLog(line: String) {
        if (headerLog.length > 4000) {
            headerLog.delete(0, headerLog.length - 2000)
        }
        if (headerLog.isNotEmpty()) headerLog.append('\n')
        headerLog.append(line)
    }

    private fun defaultWorkspaceDir(): java.io.File {
        val ext = android.os.Environment.getExternalStorageDirectory()
        val mysc = java.io.File(ext, ".sketchware/mysc/$scId")
        if (mysc.isDirectory) return mysc
        return LinuxSubsystem.baseDir()
    }

    private fun stripAnsi(input: String): String {
        var out = input
        out = out.replace(Regex("\u001B\\][^\u0007]*(\u0007|\u001B\\\\)"), "")
        out = out.replace(Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]"), "")
        out = out.replace("\r\n", "\n")
        out = out.replace("\r", "")
        return out
    }

    private fun dp(value: Int): Int = SketchwareUtil.dpToPx(value.toFloat())

    companion object {
        private const val BG = 0xFF121212.toInt()
        private const val MAX_DISPLAY = 60_000

        @JvmStatic
        fun show(activity: Activity, scId: String) {
            TerminalScreen(activity, scId).show()
        }
    }
}
