package pro.sketchware.ai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pro.sketchware.R
import pro.sketchware.ai.config.AgentLanguage
import pro.sketchware.ai.config.AiConfigManager
import pro.sketchware.ai.project.ProjectGateway
import pro.sketchware.utility.SketchwareUtil
import pro.sketchware.utility.ThemeUtils
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import com.google.android.material.R as MR

data class AttachedContextItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val displayName: String,
    val absolutePath: String,
    val badge: String,
    val promptBlock: String
) {
    enum class Kind {
        FILE,
        FOLDER,
        APK,
        IMAGE
    }

    fun iconRes(): Int = when (kind) {
        Kind.FILE -> R.drawable.ic_mtrl_file
        Kind.FOLDER -> R.drawable.ic_mtrl_folder
        Kind.APK -> R.drawable.ic_mtrl_apk_document
        Kind.IMAGE -> R.drawable.ic_mtrl_image
    }
}

object ContextAttachmentPicker {

    enum class PickerMode {
        ANY_FILE,
        FOLDER,
        APK_ONLY,
        IMAGE_ONLY
    }

    @JvmStatic
    fun showFileOrFolderPicker(
        context: Context,
        scId: String,
        mode: PickerMode,
        onAttached: (AttachedContextItem) -> Unit
    ) {
        val isId = AiConfigManager(context).agentLanguage.resolved() == AgentLanguage.ID
        val dp4 = SketchwareUtil.dpToPx(4f)
        val dp6 = SketchwareUtil.dpToPx(6f)
        val dp8 = SketchwareUtil.dpToPx(8f)
        val dp10 = SketchwareUtil.dpToPx(10f)
        val dp12 = SketchwareUtil.dpToPx(12f)
        val dp16 = SketchwareUtil.dpToPx(16f)

        val cSurfaceLow = ThemeUtils.getColor(context, MR.attr.colorSurfaceContainerLow)
        val cSurfaceHigh = ThemeUtils.getColor(context, MR.attr.colorSurfaceContainerHigh)
        val cOnSurface = ThemeUtils.getColor(context, MR.attr.colorOnSurface)
        val cOnSurfaceVar = ThemeUtils.getColor(context, MR.attr.colorOnSurfaceVariant)
        val cOutline = ThemeUtils.getColor(context, MR.attr.colorOutlineVariant)
        val cPrimary = ThemeUtils.getColor(context, R.attr.colorPrimary)

        val sdcard = Environment.getExternalStorageDirectory()
        val downloadDir = File(sdcard, "Download").takeIf { it.exists() } ?: sdcard
        val picturesDir = File(sdcard, "Pictures").takeIf { it.exists() } ?: sdcard
        val projectDataDir = File(sdcard, ".sketchware/data/$scId").apply { mkdirs() }
        val projectMyscDir = File(sdcard, ".sketchware/mysc/list/$scId").apply { mkdirs() }
        val localLibsDir = File(sdcard, ".sketchware/libs/local_libs").apply { mkdirs() }

        var currentDir: File = when (mode) {
            PickerMode.APK_ONLY -> downloadDir
            PickerMode.IMAGE_ONLY -> picturesDir
            PickerMode.FOLDER -> sdcard
            PickerMode.ANY_FILE -> downloadDir
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp10, dp16, dp8)
        }

        // 1. Quick Directory Shortcuts
        val shortcutGroup = ChipGroup(context).apply {
            isSingleSelection = false
        }
        val shortcutScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(shortcutGroup)
        }
        root.addView(shortcutScroll)

        // 2. Current Path Banner + Up Button
        val pathRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp8.toFloat()
                setColor(cSurfaceLow)
                setStroke(maxOf(1, SketchwareUtil.dpToPx(1f)), cOutline)
            }
            setPadding(dp10, dp6, dp10, dp6)
        }
        val btnUp = TextView(context).apply {
            text = if (isId) "⬆ Naik" else "⬆ Up"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cPrimary)
            setPadding(dp6, dp4, dp8, dp4)
            isClickable = true
        }
        val tvCurrentPath = TextView(context).apply {
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(cOnSurface)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        pathRow.addView(btnUp)
        pathRow.addView(tvCurrentPath)
        root.addView(
            pathRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp6
            }
        )

        // Optional: Select Current Folder button when in FOLDER mode
        val btnPickCurrentFolder = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            visibility = if (mode == PickerMode.FOLDER) View.VISIBLE else View.GONE
            textSize = 12f
        }
        root.addView(
            btnPickCurrentFolder,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp6
            }
        )

        // Optional: Checkbox for Image mode to also import directly into project @drawable
        val cbAutoImportDrawable = MaterialCheckBox(context).apply {
            text = if (isId) {
                "Sekaligus impor gambar ke Drawable proyek (@drawable/...)"
            } else {
                "Also import image into project Drawables (@drawable/...)"
            }
            isChecked = false
            textSize = 12f
            visibility = if (mode == PickerMode.IMAGE_ONLY) View.VISIBLE else View.GONE
        }
        root.addView(cbAutoImportDrawable)

        // Optional: Checkbox for APK mode to instruct REA Full Clone into current project
        val cbFullCloneApk = MaterialCheckBox(context).apply {
            text = if (isId) {
                "🧬 Aktifkan Mode REA Full Clone (Decompile XML, DEX & Clone Penuh ke Proyek Ini)"
            } else {
                "🧬 Enable REA Full Clone Mode (Decompile XML, DEX & Full Clone into Project)"
            }
            isChecked = true
            textSize = 12f
            visibility = if (mode == PickerMode.APK_ONLY) View.VISIBLE else View.GONE
        }
        root.addView(cbFullCloneApk)

        // 3. Scrollable File & Folder List
        val listScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.44f).toInt()
            ).apply {
                topMargin = dp6
            }
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        listScroll.addView(listContainer)
        root.addView(listScroll)

        // 4. Manual Path Input (Optional direct path entry)
        val tilManualPath = TextInputLayout(context).apply {
            hint = if (isId) "Atau ketik / tempel path lengkap..." else "Or type / paste full path..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val etManualPath = TextInputEditText(tilManualPath.context).apply {
            setSingleLine(true)
            textSize = 12f
        }
        tilManualPath.addView(etManualPath)
        root.addView(
            tilManualPath,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp8
            }
        )

        val dialogTitle = when (mode) {
            PickerMode.ANY_FILE -> if (isId) "📄 Tambahkan File ke AI Agent" else "📄 Add File to AI Agent"
            PickerMode.FOLDER -> if (isId) "📁 Tambahkan Folder ke AI Agent" else "📁 Add Folder to AI Agent"
            PickerMode.APK_ONLY -> if (isId) "📦 Tambahkan File APK ke AI Agent" else "📦 Add APK File to AI Agent"
            PickerMode.IMAGE_ONLY -> if (isId) "🖼️ Tambahkan Gambar ke AI Agent" else "🖼️ Add Image to AI Agent"
        }

        var alertDialog: androidx.appcompat.app.AlertDialog? = null

        fun handlePickedTarget(target: File) {
            if (!target.exists()) {
                SketchwareUtil.toastError(if (isId) "File/folder tidak ditemukan" else "File/folder not found")
                return
            }
            val item = if (target.isDirectory) {
                buildFolderContextItem(target)
            } else {
                val ext = target.extension.lowercase(Locale.ROOT)
                when {
                    ext == "apk" || mode == PickerMode.APK_ONLY -> buildApkContextItem(
                        context = context,
                        scId = scId,
                        apkFile = target,
                        fullCloneDirective = cbFullCloneApk.isChecked
                    )
                    isImageExtension(ext) || mode == PickerMode.IMAGE_ONLY -> buildImageContextItem(
                        context = context,
                        scId = scId,
                        file = target,
                        importToDrawable = cbAutoImportDrawable.isChecked
                    )
                    else -> buildFileContextItem(context, scId, target)
                }
            }
            onAttached(item)
            alertDialog?.dismiss()
        }

        fun renderDirectory(dir: File) {
            currentDir = dir
            tvCurrentPath.text = dir.absolutePath
            etManualPath.setText(dir.absolutePath)
            if (mode == PickerMode.FOLDER) {
                btnPickCurrentFolder.text = if (isId) {
                    "✓ Gunakan Folder Ini (${dir.name.ifEmpty { "/" }})"
                } else {
                    "✓ Attach This Folder (${dir.name.ifEmpty { "/" }})"
                }
                btnPickCurrentFolder.setOnClickListener {
                    handlePickedTarget(dir)
                }
            }

            listContainer.removeAllViews()

            // For APK_ONLY or IMAGE_ONLY, show a Quick Discovered section at the top if we are at the default dir
            if (mode == PickerMode.APK_ONLY) {
                val discoveredApks = discoverRecentFiles(sdcard, setOf("apk"), limit = 12)
                if (discoveredApks.isNotEmpty()) {
                    listContainer.addView(sectionHeader(context, if (isId) "⚡ File APK Terdeteksi di Perangkat" else "⚡ Detected APK Files on Device", cPrimary))
                    for (apk in discoveredApks) {
                        listContainer.addView(
                            fileRow(
                                context = context,
                                file = apk,
                                subtitle = "${formatBytes(apk.length())} · ${apk.parentFile?.name.orEmpty()}",
                                iconRes = R.drawable.ic_mtrl_apk_document,
                                iconTint = cPrimary,
                                cOnSurface = cOnSurface,
                                cOnSurfaceVar = cOnSurfaceVar,
                                cSurfaceHigh = cSurfaceHigh
                            ) {
                                handlePickedTarget(apk)
                            }
                        )
                    }
                    listContainer.addView(sectionHeader(context, if (isId) "📂 Jelajahi Folder (${dir.name})" else "📂 Browse Folder (${dir.name})", cOnSurfaceVar))
                }
            } else if (mode == PickerMode.IMAGE_ONLY) {
                val discoveredImgs = discoverRecentFiles(sdcard, IMAGE_EXTENSIONS, limit = 15)
                if (discoveredImgs.isNotEmpty()) {
                    listContainer.addView(sectionHeader(context, if (isId) "⚡ Gambar & Screenshot Terbaru" else "⚡ Recent Images & Screenshots", cPrimary))
                    for (img in discoveredImgs) {
                        listContainer.addView(
                            fileRow(
                                context = context,
                                file = img,
                                subtitle = "${formatBytes(img.length())} · ${img.parentFile?.name.orEmpty()}",
                                iconRes = R.drawable.ic_mtrl_image,
                                iconTint = cPrimary,
                                cOnSurface = cOnSurface,
                                cOnSurfaceVar = cOnSurfaceVar,
                                cSurfaceHigh = cSurfaceHigh
                            ) {
                                handlePickedTarget(img)
                            }
                        )
                    }
                    listContainer.addView(sectionHeader(context, if (isId) "📂 Jelajahi Folder (${dir.name})" else "📂 Browse Folder (${dir.name})", cOnSurfaceVar))
                }
            }

            val children = dir.listFiles()?.toList().orEmpty().sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
            )

            val filtered = children.filter { f ->
                if (f.isDirectory) return@filter true
                val ext = f.extension.lowercase(Locale.ROOT)
                when (mode) {
                    PickerMode.FOLDER -> false
                    PickerMode.APK_ONLY -> ext == "apk"
                    PickerMode.IMAGE_ONLY -> isImageExtension(ext)
                    PickerMode.ANY_FILE -> true
                }
            }

            if (filtered.isEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = if (isId) "(Tidak ada item yang cocok di folder ini)" else "(No matching items in this folder)"
                    textSize = 12f
                    setTextColor(cOnSurfaceVar)
                    setPadding(dp8, dp12, dp8, dp12)
                })
                return
            }

            for (child in filtered) {
                val isDir = child.isDirectory
                val ext = child.extension.lowercase(Locale.ROOT)
                val iconRes = when {
                    isDir -> R.drawable.ic_mtrl_folder
                    ext == "apk" -> R.drawable.ic_mtrl_apk_document
                    isImageExtension(ext) -> R.drawable.ic_mtrl_image
                    ext in setOf("java", "kt", "xml", "json", "gradle", "py", "sh", "js", "ts") -> R.drawable.ic_mtrl_code
                    else -> R.drawable.ic_mtrl_file
                }
                val sub = if (isDir) {
                    val count = child.list()?.size ?: 0
                    if (isId) "Folder · $count item" else "Folder · $count items"
                } else {
                    formatBytes(child.length())
                }
                listContainer.addView(
                    fileRow(
                        context = context,
                        file = child,
                        subtitle = sub,
                        iconRes = iconRes,
                        iconTint = if (isDir) cPrimary else cOnSurface,
                        cOnSurface = cOnSurface,
                        cOnSurfaceVar = cOnSurfaceVar,
                        cSurfaceHigh = cSurfaceHigh
                    ) {
                        if (isDir) {
                            renderDirectory(child)
                        } else {
                            handlePickedTarget(child)
                        }
                    }
                )
            }
        }

        val shortcuts = listOf(
            "📥 Download" to downloadDir,
            "🖼️ Pictures" to picturesDir,
            "📱 /sdcard" to sdcard,
            "📂 @project" to projectDataDir,
            "🛠️ @mysc" to projectMyscDir,
            "📚 @libs" to localLibsDir
        )
        for ((label, targetDir) in shortcuts) {
            val chip = Chip(context).apply {
                text = label
                textSize = 11.5f
                setOnClickListener {
                    renderDirectory(targetDir)
                }
            }
            shortcutGroup.addView(chip)
        }

        btnUp.setOnClickListener {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                renderDirectory(parent)
            }
        }

        renderDirectory(currentDir)

        alertDialog = MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setView(root)
            .setNegativeButton(if (isId) "Batal" else "Cancel", null)
            .setPositiveButton(if (isId) "Gunakan Path" else "Attach Path") { _, _ ->
                val rawPath = etManualPath.text?.toString()?.trim().orEmpty()
                if (rawPath.isNotEmpty()) {
                    handlePickedTarget(File(rawPath))
                }
            }
            .create()

        alertDialog.show()
    }

    private fun sectionHeader(context: Context, title: String, color: Int): View {
        val dp6 = SketchwareUtil.dpToPx(6f)
        return TextView(context).apply {
            text = title
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
            setPadding(dp6, dp6, dp6, dp6)
        }
    }

    private fun fileRow(
        context: Context,
        file: File,
        subtitle: String,
        iconRes: Int,
        iconTint: Int,
        cOnSurface: Int,
        cOnSurfaceVar: Int,
        cSurfaceHigh: Int,
        onClick: () -> Unit
    ): View {
        val dp4 = SketchwareUtil.dpToPx(4f)
        val dp8 = SketchwareUtil.dpToPx(8f)
        val dp10 = SketchwareUtil.dpToPx(10f)
        val dp20 = SketchwareUtil.dpToPx(20f)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp10, dp8, dp10, dp8)
            background = GradientDrawable().apply {
                cornerRadius = dp8.toFloat()
                setColor(Color.TRANSPARENT)
            }
            isClickable = true
            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(iconTint)
                layoutParams = LinearLayout.LayoutParams(dp20, dp20)
            })

            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp10
                }
            }
            col.addView(TextView(context).apply {
                text = file.name
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(cOnSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            })
            col.addView(TextView(context).apply {
                text = subtitle
                textSize = 11f
                setTextColor(cOnSurfaceVar)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(col)
        }
    }

    private fun discoverRecentFiles(sdcard: File, extensions: Set<String>, limit: Int): List<File> {
        val roots = listOf(
            File(sdcard, "Download"),
            File(sdcard, "Pictures"),
            File(sdcard, "Pictures/Screenshots"),
            File(sdcard, "DCIM/Screenshots"),
            File(sdcard, "DCIM/Camera"),
            File(sdcard, ".sketchware/mysc/list")
        )
        val found = mutableListOf<File>()
        for (r in roots) {
            if (!r.exists() || !r.canRead()) continue
            collectMatchingFiles(r, extensions, maxDepth = 3, currentDepth = 0, out = found)
        }
        return found.distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .take(limit)
    }

    private fun collectMatchingFiles(
        dir: File,
        extensions: Set<String>,
        maxDepth: Int,
        currentDepth: Int,
        out: MutableList<File>
    ) {
        if (currentDepth > maxDepth || out.size >= 40) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isFile && f.extension.lowercase(Locale.ROOT) in extensions) {
                out.add(f)
            } else if (f.isDirectory && currentDepth < maxDepth && !f.name.startsWith(".")) {
                collectMatchingFiles(f, extensions, maxDepth, currentDepth + 1, out)
            }
        }
    }

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "svg")
    private val TEXT_EXTENSIONS = setOf(
        "java", "kt", "xml", "json", "txt", "md", "gradle", "properties",
        "py", "sh", "js", "ts", "html", "css", "yaml", "yml", "csv", "sql", "pro"
    )

    private fun isImageExtension(ext: String): Boolean = ext.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    private fun stageFileInProjectContext(scId: String, file: File): File {
        return try {
            val sdcard = Environment.getExternalStorageDirectory()
            val stageDir = File(sdcard, ".sketchware/data/$scId/files/context_attachments").apply { mkdirs() }
            val dest = File(stageDir, file.name)
            if (file.absolutePath != dest.absolutePath && file.length() <= 35L * 1024L * 1024L) {
                file.copyTo(dest, overwrite = true)
                dest
            } else {
                file
            }
        } catch (_: Exception) {
            file
        }
    }

    fun buildFileContextItem(context: Context, scId: String, file: File): AttachedContextItem {
        val staged = stageFileInProjectContext(scId, file)
        val ext = file.extension.lowercase(Locale.ROOT)
        val sizeStr = formatBytes(file.length())
        val snippet = if (ext in TEXT_EXTENSIONS && file.length() <= 256 * 1024) {
            try {
                val content = file.readText()
                if (content.length > 10_000) {
                    content.take(10_000) + "\n... [Truncated, full file available at ${staged.absolutePath}]"
                } else {
                    content
                }
            } catch (_: Exception) {
                "(Binary or unreadable file)"
            }
        } else {
            "(Binary file — inspect or copy via manage_sketchware_files / manage_local_libraries / run_shell)"
        }

        val block = buildString {
            appendLine("### [Attached File: ${file.name}]")
            appendLine("- **Original Path**: `${file.absolutePath}`")
            appendLine("- **Project Workspace Path**: `${staged.absolutePath}`")
            appendLine("- **Size**: $sizeStr | **Extension**: `.${ext.ifEmpty { "bin" }}`")
            if (ext in TEXT_EXTENSIONS) {
                appendLine("```$ext")
                appendLine(snippet)
                appendLine("```")
            } else {
                appendLine("- **Note**: $snippet")
            }
        }
        return AttachedContextItem(
            kind = AttachedContextItem.Kind.FILE,
            displayName = file.name,
            absolutePath = staged.absolutePath,
            badge = sizeStr,
            promptBlock = block
        )
    }

    fun buildFolderContextItem(folder: File): AttachedContextItem {
        val allFiles = mutableListOf<File>()
        fun walk(d: File, depth: Int) {
            if (depth > 3 || allFiles.size >= 80) return
            d.listFiles()?.forEach { f ->
                allFiles.add(f)
                if (f.isDirectory && depth < 3) walk(f, depth + 1)
            }
        }
        walk(folder, 0)

        val treeLines = allFiles.take(60).joinToString("\n") { f ->
            val rel = f.absolutePath.removePrefix(folder.absolutePath).trimStart('/', '\\')
            if (f.isDirectory) "  [DIR]  $rel/" else "  [FILE] $rel (${formatBytes(f.length())})"
        }

        val block = buildString {
            appendLine("### [Attached Folder: ${folder.name}]")
            appendLine("- **Folder Path**: `${folder.absolutePath}`")
            appendLine("- **Contents Summary (${allFiles.size} items scanned)**:")
            appendLine("```text")
            appendLine(treeLines.ifEmpty { "  (Empty folder)" })
            appendLine("```")
            appendLine("- You can read, copy, or modify files inside `${folder.absolutePath}` using `manage_sketchware_files` or `run_shell`.")
        }

        return AttachedContextItem(
            kind = AttachedContextItem.Kind.FOLDER,
            displayName = folder.name.ifEmpty { folder.absolutePath },
            absolutePath = folder.absolutePath,
            badge = "${allFiles.size} items",
            promptBlock = block
        )
    }

    fun buildApkContextItem(
        context: Context,
        scId: String,
        apkFile: File,
        fullCloneDirective: Boolean = true
    ): AttachedContextItem {
        val staged = stageFileInProjectContext(scId, apkFile)
        val sizeStr = formatBytes(apkFile.length())
        var dexCount = 0
        val layouts = mutableListOf<String>()
        val drawables = mutableListOf<String>()
        val assets = mutableListOf<String>()
        val nativeLibs = mutableListOf<String>()
        var totalEntries = 0

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    totalEntries++
                    val name = e.name
                    when {
                        name.matches(Regex("classes\\d*\\.dex")) -> dexCount++
                        name.startsWith("res/layout/") && name.endsWith(".xml") -> if (layouts.size < 25) layouts.add(name.substringAfterLast('/'))
                        name.startsWith("res/drawable") && !e.isDirectory -> if (drawables.size < 25) drawables.add(name.substringAfterLast('/'))
                        name.startsWith("assets/") && !e.isDirectory -> if (assets.size < 25) assets.add(name.removePrefix("assets/"))
                        name.startsWith("lib/") && name.endsWith(".so") -> if (nativeLibs.size < 20) nativeLibs.add(name.removePrefix("lib/"))
                    }
                }
            }
        } catch (_: Exception) {
        }

        val dexPreview = try {
            pro.sketchware.ai.project.ApkProjectReconstructor.analyzeApkDex(staged, "", "", 18)
        } catch (_: Throwable) {
            null
        }

        val block = buildString {
            appendLine("### [Attached APK File: ${apkFile.name}]")
            appendLine("- **Original Path**: `${apkFile.absolutePath}`")
            appendLine("- **Workspace Path**: `${staged.absolutePath}`")
            appendLine("- **File Size**: $sizeStr | **Total ZIP Entries**: $totalEntries | **DEX Files**: $dexCount")
            if (layouts.isNotEmpty()) {
                appendLine("- **Detected Layouts (`res/layout/`)**: ${layouts.joinToString(", ")}")
            }
            if (drawables.isNotEmpty()) {
                appendLine("- **Sample Drawables**: ${drawables.take(15).joinToString(", ")}")
            }
            if (assets.isNotEmpty()) {
                appendLine("- **Bundled Assets (`assets/`)**: ${assets.joinToString(", ")}")
            }
            if (nativeLibs.isNotEmpty()) {
                appendLine("- **Native Libraries (`lib/`)**: ${nativeLibs.joinToString(", ")}")
            }
            if (dexPreview != null && dexPreview.appClasses.isNotEmpty()) {
                appendLine("- **Top DEX App Classes (${dexPreview.totalClassesCount} total)**: ${dexPreview.appClasses.take(12).joinToString(", ") { it.className }}")
            }
            if (fullCloneDirective) {
                appendLine("- **🧬 REA FULL CLONE DIRECTIVE (ACTIVE)**:")
                appendLine("  1. Segera panggil `reconstruct_apk_project(action=\"full_clone\", apk_path=\"${staged.absolutePath}\")` untuk mendekode `AndroidManifest.xml` & seluruh binary `res/layout/*.xml`, mengimpor drawables/assets/fonts/permissions, serta membuat layar Activity & CustomView di proyek Sketchware `#$scId`.")
                appendLine("  2. Gunakan `reconstruct_apk_project(action=\"analyze_dex\", apk_path=\"${staged.absolutePath}\")` dan `reconstruct_apk_project(action=\"decode_xml\", apk_path=\"${staged.absolutePath}\", xml_entry=\"...\")` (atau MCP `rea_mcp` / `run_shell`) untuk membedah logika bisnis setiap Activity.")
                appendLine("  3. Implementasikan seluruh logika `write_event_blocks`, `add_component`, dan `write_custom_java_file` di setiap layar hasil clone hingga proyek baru ini terkompilasi bersih (`save_and_verify_compile`)!")
            } else {
                appendLine("- **How to Inspect/Clone**: Call `reconstruct_apk_project(action=\"inspect\"|\"decode_xml\"|\"analyze_dex\"|\"full_clone\", apk_path=\"${staged.absolutePath}\")` or use MCP `rea_mcp` / `run_shell`.")
            }
        }

        return AttachedContextItem(
            kind = AttachedContextItem.Kind.APK,
            displayName = apkFile.name,
            absolutePath = staged.absolutePath,
            badge = if (fullCloneDirective) "🧬 REA CLONE · $sizeStr" else "APK · $sizeStr · ${dexCount}DEX",
            promptBlock = block
        )
    }

    fun buildImageContextItem(
        context: Context,
        scId: String,
        file: File,
        importToDrawable: Boolean
    ): AttachedContextItem {
        val staged = stageFileInProjectContext(scId, file)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val dimStr = if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}×${opts.outHeight}" else file.extension.uppercase(Locale.ROOT)
        val sizeStr = formatBytes(file.length())

        var importedDrawableNote = ""
        if (importToDrawable) {
            try {
                val resName = file.nameWithoutExtension
                    .lowercase(Locale.ROOT)
                    .replace(Regex("[^a-z0-9_]"), "_")
                    .let { if (it.firstOrNull()?.isDigit() == true) "img_$it" else it }
                    .ifEmpty { "img_attached" }
                val msg = ProjectGateway.manageProjectResources(
                    scId = scId,
                    action = "import",
                    resourceType = "image",
                    resourceName = resName,
                    sourcePath = file.absolutePath
                )
                importedDrawableNote = "\n- **Auto-Imported to Project Drawable**: `@drawable/$resName` ($msg)"
            } catch (e: Exception) {
                importedDrawableNote = "\n- **Drawable Import Note**: Failed auto-import (${e.message}), you can call `manage_project_resources(action=\"import\", resource_type=\"image\", source_path=\"${staged.absolutePath}\")`."
            }
        }

        val block = buildString {
            appendLine("### [Attached Image: ${file.name}]")
            appendLine("- **Original Path**: `${file.absolutePath}`")
            appendLine("- **Workspace Path**: `${staged.absolutePath}`")
            appendLine("- **Dimensions**: $dimStr | **Size**: $sizeStr | **MIME**: `${opts.outMimeType ?: "image/*"}`")
            appendLine("- **How to Use in Project**: To use this image inside an `ImageView` or layout in project `#$scId`, call `manage_project_resources(action=\"import\", resource_type=\"image\", source_path=\"${staged.absolutePath}\", resource_name=\"...\")`.$importedDrawableNote")
        }

        return AttachedContextItem(
            kind = AttachedContextItem.Kind.IMAGE,
            displayName = file.name,
            absolutePath = staged.absolutePath,
            badge = "$dimStr · $sizeStr",
            promptBlock = block
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
