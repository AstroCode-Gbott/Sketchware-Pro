package pro.sketchware.ai.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pro.sketchware.R
import pro.sketchware.utility.SketchwareUtil
import java.io.File

/**
 * Represents a tangible deliverable / file artifact in the DeerFlow-style Artifacts & Workspace panel.
 */
data class WorkspaceArtifactItem(
    val id: String,
    val title: String,
    val category: String, // "AI Deliverable", "Compiled APK", "Layout XML", "Java Source", "Resource"
    val file: File,
    val badge: String
)

object WorkspaceArtifactsManager {

    fun artifactsDir(scId: String): File {
        val dir = File(Environment.getExternalStorageDirectory(), ".sketchware/data/$scId/ai_artifacts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Collects all tangible deliverables for project [scId]:
     * 1. Explicit AI deliverables in `.sketchware/data/<sc_id>/ai_artifacts/`
     * 2. Compiled APK outputs in `.sketchware/mysc/<sc_id>/bin/` or `.sketchware/mysc/list/<sc_id>/bin/`
     * 3. Custom Java sources & generated layouts in `.sketchware/mysc/<sc_id>/` or `.sketchware/data/<sc_id>/files/`
     */
    fun collectArtifacts(scId: String): List<WorkspaceArtifactItem> {
        val out = mutableListOf<WorkspaceArtifactItem>()
        val ext = Environment.getExternalStorageDirectory()

        // 1. AI Deliverables directory
        val aiDir = artifactsDir(scId)
        aiDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
            if (f.isFile) {
                out.add(
                    WorkspaceArtifactItem(
                        id = "ai:${f.name}",
                        title = f.name,
                        category = "AI Deliverable",
                        file = f,
                        badge = formatSize(f.length())
                    )
                )
            }
        }

        // 2. Compiled APKs
        val binDirs = listOf(
            File(ext, ".sketchware/mysc/list/$scId/bin"),
            File(ext, ".sketchware/mysc/$scId/bin")
        )
        for (b in binDirs) {
            b.listFiles()?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { apk ->
                    out.add(
                        WorkspaceArtifactItem(
                            id = "apk:${apk.absolutePath}",
                            title = apk.name,
                            category = "Compiled APK",
                            file = apk,
                            badge = "APK · ${formatSize(apk.length())}"
                        )
                    )
                }
        }

        // 3. Generated / Custom Layout XMLs
        val layoutDirs = listOf(
            File(ext, ".sketchware/mysc/list/$scId/app/src/main/res/layout"),
            File(ext, ".sketchware/mysc/$scId/app/src/main/res/layout"),
            File(ext, ".sketchware/data/$scId/files/resource/layout")
        )
        val seenNames = HashSet<String>()
        for (ld in layoutDirs) {
            ld.listFiles()?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.take(20)
                ?.forEach { xml ->
                    if (seenNames.add("xml:${xml.name}")) {
                        out.add(
                            WorkspaceArtifactItem(
                                id = "xml:${xml.absolutePath}",
                                title = xml.name,
                                category = "Layout XML",
                                file = xml,
                                badge = "XML · ${formatSize(xml.length())}"
                            )
                        )
                    }
                }
        }

        // 4. Custom Java / Kotlin Files
        val javaRoots = listOf(
            File(ext, ".sketchware/data/$scId/files/java"),
            File(ext, ".sketchware/mysc/list/$scId/app/src/main/java"),
            File(ext, ".sketchware/mysc/$scId/app/src/main/java")
        )
        for (jr in javaRoots) {
            if (!jr.exists()) continue
            jr.walkTopDown().maxDepth(6)
                .filter { it.isFile && (it.name.endsWith(".java") || it.name.endsWith(".kt")) }
                .take(25)
                .forEach { jf ->
                    if (seenNames.add("code:${jf.name}")) {
                        out.add(
                            WorkspaceArtifactItem(
                                id = "code:${jf.absolutePath}",
                                title = jf.name,
                                category = "Source Code",
                                file = jf,
                                badge = jf.extension.uppercase() + " · " + formatSize(jf.length())
                            )
                        )
                    }
                }
        }

        return out
    }

    fun writeDeliverable(scId: String, fileName: String, content: String): File {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "artifact.md" }
        val target = File(artifactsDir(scId), safeName)
        target.writeText(content)
        return target
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * Shows the DeerFlow-inspired "Artifacts" Bottom Sheet for inspecting, copying, or attaching
     * deliverables produced in the workspace.
     */
    fun showArtifactsSheet(
        activity: Activity,
        scId: String,
        palette: DeerPalette,
        tx: DeerText,
        onAttachToChat: (AttachedContextItem) -> Unit
    ) {
        val sheet = BottomSheetDialog(activity)
        val dp = { v: Int -> SketchwareUtil.dpToPx(v.toFloat()) }
        val scroll = NestedScrollView(activity)
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.popover)
            setPadding(dp(16), dp(18), dp(16), dp(24))
        }
        scroll.addView(col, ViewGroup.LayoutParams(-1, -2))

        val head = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleCol.addView(TextView(activity).apply {
            text = "📦 ${tx.artifacts}"
            textSize = 17f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(palette.foreground)
        })
        titleCol.addView(TextView(activity).apply {
            text = tx.artifactsDesc
            textSize = 12f
            setTextColor(palette.mutedForeground)
        })
        head.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val items = collectArtifacts(scId)
        if (items.isEmpty()) {
            col.addView(TextView(activity).apply {
                text = tx.noArtifacts
                textSize = 13f
                setTextColor(palette.mutedForeground)
                setPadding(dp(4), dp(16), dp(4), dp(16))
            })
        } else {
            val grouped = items.groupBy { it.category }
            for ((cat, list) in grouped) {
                col.addView(TextView(activity).apply {
                    text = "$cat (${list.size})"
                    textSize = 11.5f
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(palette.mutedForeground)
                    setPadding(dp(4), dp(10), dp(4), dp(6))
                })
                for (item in list) {
                    val card = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(palette.card)
                            setStroke(maxOf(1, dp(1)), palette.border)
                        }
                        isClickable = true
                        setOnClickListener {
                            sheet.dismiss()
                            showArtifactPreviewDialog(activity, item, palette, tx, onAttachToChat)
                        }
                    }
                    val infoCol = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    infoCol.addView(TextView(activity).apply {
                        text = item.title
                        textSize = 13.5f
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                        setTextColor(palette.foreground)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.MIDDLE
                    })
                    infoCol.addView(TextView(activity).apply {
                        text = item.file.absolutePath
                        textSize = 11f
                        typeface = Typeface.MONOSPACE
                        setTextColor(palette.mutedForeground)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.START
                    })
                    card.addView(infoCol, LinearLayout.LayoutParams(0, -2, 1f))
                    card.addView(TextView(activity).apply {
                        text = item.badge
                        textSize = 10.5f
                        typeface = Typeface.MONOSPACE
                        setTextColor(palette.foreground)
                        setPadding(dp(8), dp(2), dp(8), dp(2))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat()
                            setColor(palette.secondary)
                        }
                    }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
                    col.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
                }
            }
        }

        sheet.setContentView(scroll)
        sheet.show()
    }

    private fun showArtifactPreviewDialog(
        activity: Activity,
        item: WorkspaceArtifactItem,
        palette: DeerPalette,
        tx: DeerText,
        onAttachToChat: (AttachedContextItem) -> Unit
    ) {
        val dp = { v: Int -> SketchwareUtil.dpToPx(v.toFloat()) }
        val isBinary = item.file.name.endsWith(".apk", ignoreCase = true) ||
            item.file.name.endsWith(".png", ignoreCase = true) ||
            item.file.name.endsWith(".jpg", ignoreCase = true)
        val previewText = if (isBinary) {
            "File biner: ${item.file.name}\nPath: ${item.file.absolutePath}\nUkuran: ${formatSize(item.file.length())}"
        } else {
            try {
                item.file.readText().take(12_000)
            } catch (e: Exception) {
                "Gagal membaca file: ${e.message}"
            }
        }

        val vScroll = NestedScrollView(activity)
        val hScroll = HorizontalScrollView(activity)
        val tv = TextView(activity).apply {
            text = previewText
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        hScroll.addView(tv)
        vScroll.addView(hScroll)

        MaterialAlertDialogBuilder(activity)
            .setTitle("${item.title} (${item.badge})")
            .setView(vScroll)
            .setNeutralButton(tx.copyPath) { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("path", item.file.absolutePath))
                SketchwareUtil.toast("Path disalin")
            }
            .setNegativeButton(tx.copy) { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("artifact", previewText))
                SketchwareUtil.toast("Disalin")
            }
            .setPositiveButton("+ Add Context") { _, _ ->
                val kind = if (item.file.name.endsWith(".apk", true)) {
                    AttachedContextItem.Kind.APK
                } else {
                    AttachedContextItem.Kind.FILE
                }
                onAttachToChat(
                    AttachedContextItem(
                        kind = kind,
                        displayName = item.file.name,
                        absolutePath = item.file.absolutePath,
                        badge = item.badge,
                        promptBlock = "### Attached Artifact: ${item.file.name}\nPath: `${item.file.absolutePath}`\n```\n${previewText.take(6000)}\n```"
                    )
                )
            }
            .show()
    }
}
