package pro.sketchware.ai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import pro.sketchware.ai.extensions.AgentPlugin
import pro.sketchware.ai.extensions.AgentSkill
import pro.sketchware.ai.extensions.McpClientHub
import pro.sketchware.ai.extensions.McpConnectorConfig
import pro.sketchware.ai.extensions.PluginManager
import pro.sketchware.ai.extensions.SkillManager
import pro.sketchware.utility.SketchwareUtil
import pro.sketchware.utility.ThemeUtils
import java.io.File
import java.util.concurrent.Executors
import com.google.android.material.R as MR

/**
 * Interactive UI Hub for managing Outbound MCP Connectors, Plugins, and `SKILL.md` Skills.
 */
object ExtensionsHubDialog {

    private val bgExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun show(
        context: Context,
        initialTab: Int = 0, // 0 = MCP, 1 = Plugins, 2 = Skills
        onUsePrompt: ((String) -> Unit)? = null,
        onChanged: (() -> Unit)? = null
    ) {
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
        val cError = ThemeUtils.getColor(context, R.attr.colorError)

        var activeTab = initialTab.coerceIn(0, 2)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp8, dp16, dp8)
        }

        val subtitle = TextView(context).apply {
            text = "Kelola konektor MCP eksternal (HTTP & Linux Stdio), bundel Plugin, dan panduan SKILL.md untuk AI Agent."
            textSize = 12f
            setTextColor(cOnSurfaceVar)
            setPadding(0, 0, 0, dp8)
        }
        root.addView(subtitle)

        val tabGroup = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        root.addView(tabGroup)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp6, 0, dp6)
        }
        root.addView(actionRow)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.56f).toInt()
            )
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp4, 0, dp12)
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        lateinit var renderContent: () -> Unit

        val tabLabels = listOf("🔌 MCP Connectors", "🧩 Plugins", "📚 Skills (SKILL.md)")
        tabLabels.forEachIndexed { idx, label ->
            val chip = Chip(context).apply {
                text = label
                isCheckable = true
                isChecked = (idx == activeTab)
                setOnClickListener {
                    activeTab = idx
                    renderContent()
                }
            }
            tabGroup.addView(chip)
        }

        fun cardBg(active: Boolean): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp12.toFloat()
            setColor(if (active) cSurfaceHigh else cSurfaceLow)
            setStroke(
                maxOf(1, SketchwareUtil.dpToPx(1f)),
                if (active) cPrimary else cOutline
            )
        }

        fun smallOutlinedBtn(label: String, tint: Int = cPrimary, onClick: () -> Unit): MaterialButton {
            return MaterialButton(context, null, MR.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                minHeight = SketchwareUtil.dpToPx(34f)
                minimumHeight = SketchwareUtil.dpToPx(34f)
                setPadding(dp10, dp4, dp10, dp4)
                setTextColor(tint)
                strokeColor = ColorStateList.valueOf(tint)
                setOnClickListener { onClick() }
            }
        }

        fun showAddMcpDialog() {
            val formScroll = ScrollView(context)
            val form = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp16, dp12, dp16, dp8)
            }
            formScroll.addView(form)

            val tilName = TextInputLayout(context).apply { hint = "Nama Konektor (misal: Context7 / GitHub MCP)" }
            val etName = TextInputEditText(tilName.context).apply { setSingleLine(true) }
            tilName.addView(etName)
            form.addView(tilName)

            val transportGroup = ChipGroup(context).apply {
                isSingleSelection = true
                isSelectionRequired = true
                setPadding(0, dp8, 0, dp4)
            }
            var selectedTransport = "http"
            val tilTarget = TextInputLayout(context).apply {
                hint = "URL Endpoint HTTP MCP (contoh: https://mcp.context7.com/mcp)"
            }
            val etTarget = TextInputEditText(tilTarget.context).apply { setSingleLine(true) }
            tilTarget.addView(etTarget)

            val cbInLinux = MaterialCheckBox(context).apply {
                text = "Jalankan di dalam Linux / Debian (proot)"
                isChecked = true
                visibility = View.GONE
            }

            val chipHttp = Chip(context).apply {
                text = "HTTP / Streamable HTTP"
                isCheckable = true
                isChecked = true
                setOnClickListener {
                    selectedTransport = "http"
                    tilTarget.hint = "URL Endpoint HTTP MCP (contoh: https://mcp.context7.com/mcp)"
                    cbInLinux.visibility = View.GONE
                }
            }
            val chipStdio = Chip(context).apply {
                text = "Stdio (Perintah Shell / Debian)"
                isCheckable = true
                setOnClickListener {
                    selectedTransport = "stdio"
                    tilTarget.hint = "Perintah Stdio (contoh: python3 /path/server.py atau npx -y ...)"
                    cbInLinux.visibility = View.VISIBLE
                }
            }
            transportGroup.addView(chipHttp)
            transportGroup.addView(chipStdio)
            form.addView(transportGroup)
            form.addView(tilTarget)
            form.addView(cbInLinux)

            val tilHeader = TextInputLayout(context).apply {
                hint = "Opsional: Authorization / Bearer Token"
                setPadding(0, dp8, 0, 0)
            }
            val etHeader = TextInputEditText(tilHeader.context).apply { setSingleLine(true) }
            tilHeader.addView(etHeader)
            form.addView(tilHeader)

            MaterialAlertDialogBuilder(context)
                .setTitle("Tambah Konektor MCP")
                .setView(formScroll)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan & Sync") { _, _ ->
                    val name = etName.text?.toString()?.trim().orEmpty()
                    val target = etTarget.text?.toString()?.trim().orEmpty()
                    val token = etHeader.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty() || target.isEmpty()) {
                        SketchwareUtil.toastError("Nama dan URL/Perintah wajib diisi")
                        return@setPositiveButton
                    }
                    val headers = if (token.isNotEmpty()) {
                        val authVal = if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
                        mapOf("Authorization" to authVal)
                    } else emptyMap()
                    val saved = McpClientHub.addOrUpdateConnector(
                        McpConnectorConfig(
                            id = SkillManager.sanitizeId(name),
                            name = name,
                            transport = selectedTransport,
                            url = if (selectedTransport == "http") target else "",
                            command = if (selectedTransport == "stdio") target else "",
                            inLinux = cbInLinux.isChecked,
                            headers = headers,
                            enabled = true
                        )
                    )
                    renderContent()
                    onChanged?.invoke()
                    SketchwareUtil.toast("Menyinkronkan tool dari '${saved.name}'…")
                    bgExecutor.execute {
                        val msg = McpClientHub.syncConnectorTools(saved.id)
                        mainHandler.post {
                            SketchwareUtil.toast(msg.lines().firstOrNull() ?: msg)
                            renderContent()
                            onChanged?.invoke()
                        }
                    }
                }
                .show()
        }

        fun showAddSkillDialog(existing: AgentSkill? = null) {
            val formScroll = ScrollView(context)
            val form = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp16, dp12, dp16, dp8)
            }
            formScroll.addView(form)

            val tilName = TextInputLayout(context).apply { hint = "Nama Skill (atau URL file SKILL.md untuk impor)" }
            val etName = TextInputEditText(tilName.context).apply {
                setSingleLine(true)
                if (existing != null) setText(existing.name)
            }
            tilName.addView(etName)
            form.addView(tilName)

            val tilDesc = TextInputLayout(context).apply {
                hint = "Deskripsi singkat kapan AI Agent harus memakai skill ini"
                setPadding(0, dp8, 0, 0)
            }
            val etDesc = TextInputEditText(tilDesc.context).apply {
                if (existing != null) setText(existing.description)
            }
            tilDesc.addView(etDesc)
            form.addView(tilDesc)

            val tilBody = TextInputLayout(context).apply {
                hint = "Isi Instruksi Markdown (SKILL.md)"
                setPadding(0, dp8, 0, 0)
            }
            val existingBody = if (existing != null && File(existing.skillFilePath).isFile) {
                File(existing.skillFilePath).readText()
            } else ""
            val etBody = TextInputEditText(tilBody.context).apply {
                minLines = 6
                maxLines = 14
                gravity = Gravity.TOP or Gravity.START
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setText(existingBody)
            }
            tilBody.addView(etBody)
            form.addView(tilBody)

            MaterialAlertDialogBuilder(context)
                .setTitle(if (existing == null) "Buat / Impor Skill (SKILL.md)" else "Edit Skill: ${existing.name}")
                .setView(formScroll)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan") { _, _ ->
                    val nameOrUrl = etName.text?.toString()?.trim().orEmpty()
                    val desc = etDesc.text?.toString()?.trim().orEmpty()
                    val body = etBody.text?.toString()?.trim().orEmpty()
                    if (nameOrUrl.startsWith("http://") || nameOrUrl.startsWith("https://")) {
                        SketchwareUtil.toast("Mengunduh skill dari URL…")
                        bgExecutor.execute {
                            val res = SkillManager.installSkillFromUrl(nameOrUrl)
                            mainHandler.post {
                                SketchwareUtil.toast(res)
                                renderContent()
                                onChanged?.invoke()
                            }
                        }
                        return@setPositiveButton
                    }
                    if (nameOrUrl.isEmpty() || body.isEmpty()) {
                        SketchwareUtil.toastError("Nama dan isi Markdown SKILL.md wajib diisi")
                        return@setPositiveButton
                    }
                    val id = existing?.id ?: SkillManager.sanitizeId(nameOrUrl)
                    SkillManager.createOrUpdateSkill(
                        id = id,
                        name = nameOrUrl,
                        description = desc.ifBlank { "Panduan khusus untuk $nameOrUrl" },
                        bodyMarkdown = body
                    )
                    SketchwareUtil.toast("Skill '$nameOrUrl' berhasil disimpan!")
                    renderContent()
                    onChanged?.invoke()
                }
                .show()
        }

        fun showAddPluginDialog() {
            val form = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp16, dp12, dp16, dp8)
            }
            val tilSource = TextInputLayout(context).apply {
                hint = "Path file .zip / folder plugin atau URL plugin.json"
            }
            val etSource = TextInputEditText(tilSource.context).apply { setSingleLine(true) }
            tilSource.addView(etSource)
            form.addView(tilSource)

            MaterialAlertDialogBuilder(context)
                .setTitle("Pasang Plugin dari File / URL")
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Pasang") { _, _ ->
                    val src = etSource.text?.toString()?.trim().orEmpty()
                    if (src.isEmpty()) {
                        SketchwareUtil.toastError("Masukkan path atau URL terlebih dahulu")
                        return@setPositiveButton
                    }
                    bgExecutor.execute {
                        val msg = PluginManager.installPluginFromArchiveOrUrl(src)
                        mainHandler.post {
                            SketchwareUtil.toast(msg)
                            renderContent()
                            onChanged?.invoke()
                        }
                    }
                }
                .show()
        }

        var parentDialog: androidx.appcompat.app.AlertDialog? = null

        renderContent = {
            actionRow.removeAllViews()
            listContainer.removeAllViews()

            when (activeTab) {
                0 -> {
                    // MCP Tab
                    actionRow.addView(smallOutlinedBtn("+ Tambah Konektor MCP") { showAddMcpDialog() })
                    actionRow.addView(smallOutlinedBtn("Sync Semua Aktif") {
                        val enabledList = McpClientHub.listConnectors().filter { it.enabled }
                        if (enabledList.isEmpty()) {
                            SketchwareUtil.toast("Tidak ada konektor MCP yang aktif.")
                        } else {
                            SketchwareUtil.toast("Menyinkronkan ${enabledList.size} konektor MCP…")
                            bgExecutor.execute {
                                enabledList.forEach { McpClientHub.syncConnectorTools(it.id) }
                                mainHandler.post {
                                    SketchwareUtil.toast("Sinkronisasi MCP selesai!")
                                    renderContent()
                                    onChanged?.invoke()
                                }
                            }
                        }
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp8 }
                    })

                    val connectors = McpClientHub.listConnectors()
                    for (conn in connectors) {
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = cardBg(conn.enabled)
                            setPadding(dp12, dp10, dp12, dp10)
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp8 }
                        }
                        val topRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val cb = MaterialCheckBox(context).apply {
                            isChecked = conn.enabled
                            text = "${conn.name} (${conn.transport.uppercase()})"
                            textSize = 13f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(cOnSurface)
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                            setOnCheckedChangeListener { _, checked ->
                                McpClientHub.setConnectorEnabled(conn.id, checked)
                                renderContent()
                                onChanged?.invoke()
                            }
                        }
                        topRow.addView(cb)
                        card.addView(topRow)

                        val targetInfo = if (conn.transport == "http") conn.url else conn.command
                        card.addView(TextView(context).apply {
                            text = "Target: $targetInfo\nStatus: ${conn.lastStatus}"
                            textSize = 11.5f
                            setTextColor(cOnSurfaceVar)
                            setPadding(0, dp4, 0, dp4)
                        })

                        if (conn.cachedTools.isNotEmpty()) {
                            card.addView(TextView(context).apply {
                                text = "Tools (${conn.cachedTools.size}): " + conn.cachedTools.joinToString(", ") { it.name }
                                textSize = 11f
                                typeface = Typeface.MONOSPACE
                                setTextColor(cPrimary)
                                setPadding(0, 0, 0, dp6)
                            })
                        }

                        val btnRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.END
                        }
                        btnRow.addView(smallOutlinedBtn("Sync Tools") {
                            SketchwareUtil.toast("Menghubungi ${conn.name}…")
                            bgExecutor.execute {
                                val res = McpClientHub.syncConnectorTools(conn.id)
                                mainHandler.post {
                                    SketchwareUtil.toast(res.lines().firstOrNull() ?: res)
                                    renderContent()
                                    onChanged?.invoke()
                                }
                            }
                        })
                        if (conn.source == "user") {
                            btnRow.addView(smallOutlinedBtn("Hapus", cError) {
                                McpClientHub.removeConnector(conn.id)
                                renderContent()
                                onChanged?.invoke()
                            }.apply {
                                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp6 }
                            })
                        }
                        card.addView(btnRow)
                        listContainer.addView(card)
                    }
                }

                1 -> {
                    // Plugins Tab
                    actionRow.addView(smallOutlinedBtn("+ Pasang Plugin (File / URL)") { showAddPluginDialog() })
                    val plugins: List<AgentPlugin> = PluginManager.listPlugins()
                    for (p in plugins) {
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = cardBg(p.enabled)
                            setPadding(dp12, dp10, dp12, dp10)
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp8 }
                        }
                        val cb = MaterialCheckBox(context).apply {
                            isChecked = p.enabled
                            text = "${p.name} (v${p.version})"
                            textSize = 13f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(cOnSurface)
                            setOnCheckedChangeListener { _, checked ->
                                PluginManager.setPluginEnabled(p.id, checked)
                                renderContent()
                                onChanged?.invoke()
                            }
                        }
                        card.addView(cb)
                        card.addView(TextView(context).apply {
                            text = p.description
                            textSize = 11.5f
                            setTextColor(cOnSurfaceVar)
                            setPadding(0, dp4, 0, dp4)
                        })
                        val metaParts = mutableListOf<String>()
                        if (p.skillIds.isNotEmpty()) metaParts.add("Skills: ${p.skillIds.joinToString(", ")}")
                        if (p.slashCommands.isNotEmpty()) metaParts.add("Slash: ${p.slashCommands.joinToString(", ") { it.command }}")
                        if (p.mcpConnectorIds.isNotEmpty()) metaParts.add("MCP: ${p.mcpConnectorIds.joinToString(", ")}")
                        if (metaParts.isNotEmpty()) {
                            card.addView(TextView(context).apply {
                                text = metaParts.joinToString("  ·  ")
                                textSize = 11f
                                typeface = Typeface.MONOSPACE
                                setTextColor(cPrimary)
                            })
                        }
                        listContainer.addView(card)
                    }
                }

                2 -> {
                    // Skills Tab
                    actionRow.addView(smallOutlinedBtn("+ Buat / Impor Skill (SKILL.md)") { showAddSkillDialog(null) })
                    val skills = SkillManager.listAllSkills()
                    for (s in skills) {
                        val card = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            background = cardBg(s.enabled)
                            setPadding(dp12, dp10, dp12, dp10)
                            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp8 }
                        }
                        val cb = MaterialCheckBox(context).apply {
                            isChecked = s.enabled
                            text = "${s.name} (${s.id})"
                            textSize = 13f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(cOnSurface)
                            setOnCheckedChangeListener { _, checked ->
                                SkillManager.setSkillEnabled(s.id, checked)
                                renderContent()
                                onChanged?.invoke()
                            }
                        }
                        card.addView(cb)
                        card.addView(TextView(context).apply {
                            text = s.description
                            textSize = 11.5f
                            setTextColor(cOnSurfaceVar)
                            maxLines = 3
                            ellipsize = TextUtils.TruncateAt.END
                            setPadding(0, dp4, 0, dp4)
                        })
                        card.addView(TextView(context).apply {
                            text = "Sumber: ${s.source} · ${s.skillFilePath}"
                            textSize = 10.5f
                            typeface = Typeface.MONOSPACE
                            setTextColor(cOnSurfaceVar)
                            setPadding(0, 0, 0, dp6)
                        })
                        val btnRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.END
                        }
                        btnRow.addView(smallOutlinedBtn("Lihat / Edit") { showAddSkillDialog(s) })
                        if (onUsePrompt != null) {
                            btnRow.addView(smallOutlinedBtn("Pakai di Chat") {
                                onUsePrompt.invoke(
                                    "Gunakan skill '${s.id}' (panggil use_skill(action=\"read\", skill_id=\"${s.id}\")) untuk menyelesaikan tugas berikut: "
                                )
                                parentDialog?.dismiss()
                            }.apply {
                                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp6 }
                            })
                        }
                        if (s.source == "standalone") {
                            btnRow.addView(smallOutlinedBtn("Hapus", cError) {
                                SkillManager.deleteSkill(s.id)
                                renderContent()
                                onChanged?.invoke()
                            }.apply {
                                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp6 }
                            })
                        }
                        card.addView(btnRow)
                        listContainer.addView(card)
                    }
                }
            }
        }

        renderContent()

        parentDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Ekosistem MCP, Plugin & Skill")
            .setView(root)
            .setPositiveButton("Selesai") { d, _ ->
                onChanged?.invoke()
                d.dismiss()
            }
            .show()
    }
}
