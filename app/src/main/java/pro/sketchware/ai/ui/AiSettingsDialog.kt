package pro.sketchware.ai.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import pro.sketchware.R
import pro.sketchware.ai.config.AiConfigManager
import pro.sketchware.ai.config.AiProviderType
import pro.sketchware.utility.SketchwareUtil
import pro.sketchware.utility.ThemeUtils
import com.google.android.material.R as MR

object AiSettingsDialog {

    @JvmStatic
    fun show(context: Context, onSaved: (() -> Unit)? = null) {
        val config = AiConfigManager(context)
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

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp12, dp16, dp12)
        }
        scroll.addView(root)

        val providerTypes = AiProviderType.entries
        var selectedProvider = config.providerType

        // 1. Provider Selection
        val providerLabel = TextView(context).apply {
            text = "1. Pilih AI Provider (BYOK)"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cOnSurface)
            setPadding(0, 0, 0, dp4)
        }
        root.addView(providerLabel)

        val providerChipGroup = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        root.addView(providerChipGroup)

        // 2. API Key Input + Status
        val tvKeyStatus = TextView(context).apply {
            textSize = 11.5f
            setPadding(0, dp8, 0, dp4)
        }
        root.addView(tvKeyStatus)

        val tilApiKey = TextInputLayout(context).apply {
            hint = "API Key"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val etApiKey = TextInputEditText(tilApiKey.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        tilApiKey.addView(etApiKey)
        root.addView(
            tilApiKey,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // 3. Model ID Input + Save/Delete Model Presets
        val tilModel = TextInputLayout(context).apply {
            hint = "Model ID (contoh: gemini-2.5-pro, glm-5.3-flash, claude-sonnet-4-20250514)"
        }
        val etModel = TextInputEditText(tilModel.context).apply {
            setSingleLine(true)
        }
        tilModel.addView(etModel)
        root.addView(
            tilModel,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp10
            }
        )

        val modelHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp6, 0, dp4)
        }
        val tvModelPresetLabel = TextView(context).apply {
            text = "Preset & Model Tersimpan (ketuk × untuk hapus model kustom):"
            textSize = 11.5f
            setTextColor(cOnSurfaceVar)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAddModel = TextView(context).apply {
            text = "+ Simpan Model"
            textSize = 11.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cPrimary)
            setPadding(dp8, dp4, dp8, dp4)
            isClickable = true
        }
        modelHeaderRow.addView(tvModelPresetLabel)
        modelHeaderRow.addView(btnAddModel)
        root.addView(modelHeaderRow)

        val modelPresetGroup = ChipGroup(context).apply {
            isSingleSelection = false
        }
        root.addView(
            modelPresetGroup,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // 4. Base URL Endpoint
        val tilBaseUrl = TextInputLayout(context).apply {
            hint = "Base URL Endpoint"
        }
        val etBaseUrl = TextInputEditText(tilBaseUrl.context).apply {
            setSingleLine(true)
        }
        tilBaseUrl.addView(etBaseUrl)
        root.addView(
            tilBaseUrl,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp10
            }
        )

        // 5. Action Buttons: Simpan Key & Model / Hapus API Key
        val keyActionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSaveKeyModel = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Simpan Key & Model"
            textSize = 11.5f
            minHeight = SketchwareUtil.dpToPx(38f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp6
            }
        }
        val btnDeleteKey = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "Hapus API Key"
            textSize = 11.5f
            minHeight = SketchwareUtil.dpToPx(38f)
            setTextColor(cError)
            strokeColor = ColorStateList.valueOf(cError)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp6
            }
        }
        keyActionRow.addView(btnSaveKeyModel)
        keyActionRow.addView(btnDeleteKey)
        root.addView(
            keyActionRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp10
            }
        )

        // 6. Saved API Key & Model Profiles List
        val savedSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp12.toFloat()
                setColor(cSurfaceLow)
                setStroke(maxOf(1, SketchwareUtil.dpToPx(1f)), cOutline)
            }
            setPadding(dp12, dp10, dp12, dp10)
        }
        val savedHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvSavedTitle = TextView(context).apply {
            text = "Daftar API Key & Model Tersimpan"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(cOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClearAllKeys = TextView(context).apply {
            text = "Hapus Semua"
            textSize = 11f
            setTextColor(cError)
            setPadding(dp6, dp4, dp6, dp4)
            isClickable = true
        }
        savedHeaderRow.addView(tvSavedTitle)
        savedHeaderRow.addView(btnClearAllKeys)
        savedSection.addView(savedHeaderRow)

        val savedListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp6, 0, 0)
        }
        savedSection.addView(savedListContainer)
        root.addView(
            savedSection,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp12
            }
        )

        root.addView(
            MaterialDivider(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp12
                bottomMargin = dp8
            }
        )

        // 7. Agent Execution & MCP Settings
        val tilMaxSteps = TextInputLayout(context).apply {
            hint = "Maksimum Langkah Long-Horizon per Run (5 - 100)"
        }
        val etMaxSteps = TextInputEditText(tilMaxSteps.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(config.maxStepsPerRun.toString())
        }
        tilMaxSteps.addView(etMaxSteps)
        root.addView(
            tilMaxSteps,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp4
            }
        )

        val cbAutoBuild = MaterialCheckBox(context).apply {
            text = "Otomatis Compile & Self-Heal setelah Agent menulis kode/blok"
            isChecked = config.autoBuildAndHeal
        }
        root.addView(cbAutoBuild)

        val cbAutoBackup = MaterialCheckBox(context).apply {
            text = "Otomatis buat Snapshot Checkpoint sebelum Agent memodifikasi proyek"
            isChecked = config.autoBackupBeforeRun
        }
        root.addView(cbAutoBackup)

        val lanIp = pro.sketchware.ai.mcp.SketchwareMcpServer.getLanIpAddress()
        val mcpRunning = pro.sketchware.ai.mcp.SketchwareMcpServer.isRunning()
        val mcpTitle = TextView(context).apply {
            text = "🌐 Sketchware MCP & External Agent Bridge\n" +
                "Status: ${if (mcpRunning) "AKTIF" else "NONAKTIF"} | Port: ${config.mcpServerPort}\n" +
                "• ADB URL: http://127.0.0.1:${config.mcpServerPort}/mcp\n" +
                "• Wi-Fi LAN: http://$lanIp:${config.mcpServerPort}/mcp"
            textSize = 12f
            setPadding(dp12, dp10, dp12, dp10)
        }
        root.addView(mcpTitle)

        val cbMcpEnabled = MaterialCheckBox(context).apply {
            text = "Aktifkan Sketchware Live MCP Server"
            isChecked = config.mcpServerEnabled
        }
        root.addView(cbMcpEnabled)

        val tilMcpPort = TextInputLayout(context).apply {
            hint = "Port MCP Server (Default: 8765)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val etMcpPort = TextInputEditText(tilMcpPort.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(config.mcpServerPort.toString())
            setSingleLine(true)
        }
        tilMcpPort.addView(etMcpPort)
        root.addView(
            tilMcpPort,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp8
            }
        )

        val btnOpenExtensions = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "🔌 Kelola Konektor MCP, Plugin & Skill (SKILL.md)"
            textSize = 12f
            setOnClickListener {
                ExtensionsHubDialog.show(context, initialTab = 0, onChanged = onSaved)
            }
        }
        root.addView(
            btnOpenExtensions,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp10
            }
        )

        fun defaultPresetsFor(provider: AiProviderType): List<String> = when (provider) {
            AiProviderType.GEMINI -> listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash")
            AiProviderType.OPENAI -> listOf("gpt-4.1", "gpt-4.1-mini", "o4-mini")
            AiProviderType.OPENROUTER -> listOf(
                "anthropic/claude-sonnet-4",
                "google/gemini-2.5-pro-preview",
                "deepseek/deepseek-chat-v3-0324"
            )
            AiProviderType.CLAUDE -> listOf("claude-sonnet-4-20250514", "claude-3-7-sonnet-latest")
            AiProviderType.CUSTOM -> listOf("glm-5.3-flash", "deepseek-chat", "qwen2.5-coder:32b", "llama-3.3-70b-versatile")
        }

        fun updateKeyStatusLabel(provider: AiProviderType) {
            val savedKey = config.getApiKey(provider)
            if (savedKey.isNotEmpty()) {
                tvKeyStatus.text = "✓ API Key ${provider.name} tersimpan (${AiConfigManager.maskApiKey(savedKey)})"
                tvKeyStatus.setTextColor(cPrimary)
            } else {
                tvKeyStatus.text = "Belum ada API Key tersimpan untuk ${provider.name}"
                tvKeyStatus.setTextColor(cOnSurfaceVar)
            }
        }

        fun renderModelChips(provider: AiProviderType) {
            modelPresetGroup.removeAllViews()
            val builtIn = defaultPresetsFor(provider)
            val customModels = config.getCustomModels(provider)
            val allModels = (customModels + builtIn).distinct()

            for (modelName in allModels) {
                val isCustom = customModels.any { it.equals(modelName, ignoreCase = true) } &&
                    builtIn.none { it.equals(modelName, ignoreCase = true) }
                val chip = Chip(context).apply {
                    text = modelName
                    isCloseIconVisible = isCustom
                    setOnClickListener {
                        etModel.setText(modelName)
                    }
                    if (isCustom) {
                        setOnCloseIconClickListener {
                            config.removeCustomModel(modelName, provider)
                            renderModelChips(provider)
                            SketchwareUtil.toast("Model '$modelName' dihapus dari daftar")
                        }
                    }
                }
                modelPresetGroup.addView(chip)
            }
        }

        fun selectProviderChip(provider: AiProviderType) {
            for (i in 0 until providerChipGroup.childCount) {
                val c = providerChipGroup.getChildAt(i) as? Chip ?: continue
                c.isChecked = (c.text?.toString() == provider.name)
            }
        }

        lateinit var renderSavedProfiles: () -> Unit

        fun populateProviderFields(provider: AiProviderType) {
            selectedProvider = provider
            selectProviderChip(provider)
            etApiKey.setText(config.getApiKey(provider))
            etModel.setText(config.getModelId(provider))
            etBaseUrl.setText(config.getBaseUrl(provider))
            updateKeyStatusLabel(provider)
            renderModelChips(provider)
        }

        renderSavedProfiles = {
            savedListContainer.removeAllViews()
            val profiles = config.getSavedProfiles()
            btnClearAllKeys.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            if (profiles.isEmpty()) {
                savedListContainer.addView(TextView(context).apply {
                    text = "Belum ada profil API Key & Model yang disimpan. Isi API Key & Model di atas lalu ketuk 'Simpan Key & Model'."
                    textSize = 11.5f
                    setTextColor(cOnSurfaceVar)
                })
            } else {
                for (profile in profiles) {
                    val isActive = (config.providerType == profile.provider &&
                        config.getApiKey(profile.provider) == profile.apiKey &&
                        config.getModelId(profile.provider).equals(profile.modelId, ignoreCase = true))

                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply {
                            cornerRadius = dp8.toFloat()
                            setColor(if (isActive) cSurfaceHigh else Color.TRANSPARENT)
                            if (isActive) {
                                setStroke(maxOf(1, SketchwareUtil.dpToPx(1f)), cPrimary)
                            }
                        }
                        setPadding(dp10, dp8, dp8, dp8)
                        isClickable = true
                        setOnClickListener {
                            config.providerType = profile.provider
                            config.setApiKey(profile.apiKey, profile.provider)
                            config.setModelId(profile.modelId, profile.provider)
                            config.setBaseUrl(profile.baseUrl, profile.provider)
                            populateProviderFields(profile.provider)
                            renderSavedProfiles()
                            onSaved?.invoke()
                            SketchwareUtil.toast("Mengaktifkan ${profile.provider.name} · ${profile.modelId}")
                        }
                    }

                    val infoCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    infoCol.addView(TextView(context).apply {
                        text = "${if (isActive) "✓ " else ""}${profile.provider.name} · ${profile.modelId}"
                        textSize = 12.5f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(if (isActive) cPrimary else cOnSurface)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    infoCol.addView(TextView(context).apply {
                        text = "Key: ${AiConfigManager.maskApiKey(profile.apiKey)} · ${profile.baseUrl}"
                        textSize = 11f
                        setTextColor(cOnSurfaceVar)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.MIDDLE
                    })
                    row.addView(infoCol)

                    val btnDelItem = ImageView(context).apply {
                        setImageResource(R.drawable.ic_mtrl_delete)
                        imageTintList = ColorStateList.valueOf(cError)
                        setPadding(dp6, dp6, dp6, dp6)
                        isClickable = true
                        contentDescription = "Hapus"
                        setOnClickListener {
                            config.deleteProfile(profile.id)
                            if (config.getApiKey(profile.provider) == profile.apiKey) {
                                config.clearApiKey(profile.provider)
                                if (selectedProvider == profile.provider) {
                                    etApiKey.setText("")
                                    updateKeyStatusLabel(selectedProvider)
                                }
                            }
                            renderSavedProfiles()
                            onSaved?.invoke()
                            SketchwareUtil.toast("API Key & Model '${profile.modelId}' dihapus")
                        }
                    }
                    row.addView(
                        btnDelItem,
                        LinearLayout.LayoutParams(SketchwareUtil.dpToPx(32f), SketchwareUtil.dpToPx(32f)).apply {
                            marginStart = dp6
                        }
                    )

                    savedListContainer.addView(
                        row,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = dp4
                        }
                    )
                }
            }
        }

        for (p in providerTypes) {
            val chip = Chip(context).apply {
                text = p.name
                isCheckable = true
                isChecked = (p == selectedProvider)
                setOnClickListener {
                    // Save current edits to previous provider before switching
                    config.setApiKey(etApiKey.text?.toString().orEmpty(), selectedProvider)
                    config.setModelId(etModel.text?.toString().orEmpty(), selectedProvider)
                    config.setBaseUrl(etBaseUrl.text?.toString().orEmpty(), selectedProvider)
                    populateProviderFields(p)
                    renderSavedProfiles()
                }
            }
            providerChipGroup.addView(chip)
        }

        btnAddModel.setOnClickListener {
            val modelText = etModel.text?.toString()?.trim().orEmpty()
            if (modelText.isEmpty()) {
                SketchwareUtil.toastError("Ketik nama Model ID terlebih dahulu")
                return@setOnClickListener
            }
            config.addCustomModel(modelText, selectedProvider)
            config.setModelId(modelText, selectedProvider)
            renderModelChips(selectedProvider)
            SketchwareUtil.toast("Model '$modelText' disimpan ke daftar ${selectedProvider.name}")
        }

        btnSaveKeyModel.setOnClickListener {
            val keyText = etApiKey.text?.toString()?.trim().orEmpty()
            val modelText = etModel.text?.toString()?.trim().orEmpty().ifEmpty { selectedProvider.defaultModel }
            val urlText = etBaseUrl.text?.toString()?.trim().orEmpty().ifEmpty { selectedProvider.defaultBaseUrl }
            if (keyText.isEmpty() && selectedProvider != AiProviderType.CUSTOM) {
                SketchwareUtil.toastError("Masukkan API Key terlebih dahulu")
                return@setOnClickListener
            }
            config.providerType = selectedProvider
            config.setApiKey(keyText, selectedProvider)
            config.setModelId(modelText, selectedProvider)
            config.setBaseUrl(urlText, selectedProvider)
            config.saveProfile(selectedProvider, keyText, modelText, urlText)
            updateKeyStatusLabel(selectedProvider)
            renderModelChips(selectedProvider)
            renderSavedProfiles()
            onSaved?.invoke()
            SketchwareUtil.toast("API Key & Model ($modelText) disimpan!")
        }

        btnDeleteKey.setOnClickListener {
            val currentKey = etApiKey.text?.toString()?.trim().orEmpty()
                .ifEmpty { config.getApiKey(selectedProvider) }
            config.clearApiKey(selectedProvider)
            // Also remove matching saved profile for this provider + key if present
            if (currentKey.isNotEmpty()) {
                config.getSavedProfiles()
                    .filter { it.provider == selectedProvider && it.apiKey == currentKey }
                    .forEach { config.deleteProfile(it.id) }
            }
            etApiKey.setText("")
            updateKeyStatusLabel(selectedProvider)
            renderSavedProfiles()
            onSaved?.invoke()
            SketchwareUtil.toast("API Key untuk ${selectedProvider.name} berhasil dihapus")
        }

        btnClearAllKeys.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Hapus Semua API Key?")
                .setMessage("Semua API Key dan daftar profil model yang tersimpan di perangkat ini akan dihapus.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus Semua") { _, _ ->
                    config.clearAllApiKeys()
                    etApiKey.setText("")
                    updateKeyStatusLabel(selectedProvider)
                    renderSavedProfiles()
                    onSaved?.invoke()
                    SketchwareUtil.toast("Semua API Key telah dihapus")
                }
                .show()
        }

        // Seed existing active config into saved profiles if not empty so user immediately sees their active key/model
        val initialKey = config.getApiKey(selectedProvider)
        if (initialKey.isNotEmpty() && config.getSavedProfiles().isEmpty()) {
            config.saveProfile(
                selectedProvider,
                initialKey,
                config.getModelId(selectedProvider),
                config.getBaseUrl(selectedProvider)
            )
        }

        populateProviderFields(selectedProvider)
        renderSavedProfiles()

        MaterialAlertDialogBuilder(context)
            .setTitle("Pengaturan AI Agent Studio")
            .setIcon(R.drawable.ic_mtrl_settings)
            .setView(scroll)
            .setPositiveButton("Simpan & Tutup") { d, _ ->
                val keyText = etApiKey.text?.toString()?.trim().orEmpty()
                val modelText = etModel.text?.toString()?.trim().orEmpty().ifEmpty { selectedProvider.defaultModel }
                val urlText = etBaseUrl.text?.toString()?.trim().orEmpty().ifEmpty { selectedProvider.defaultBaseUrl }
                config.providerType = selectedProvider
                config.setApiKey(keyText, selectedProvider)
                config.setModelId(modelText, selectedProvider)
                config.setBaseUrl(urlText, selectedProvider)
                if (keyText.isNotEmpty() || selectedProvider == AiProviderType.CUSTOM) {
                    config.saveProfile(selectedProvider, keyText, modelText, urlText)
                }
                val maxSteps = etMaxSteps.text?.toString()?.toIntOrNull() ?: 30
                config.maxStepsPerRun = maxSteps
                config.autoBuildAndHeal = cbAutoBuild.isChecked
                config.autoBackupBeforeRun = cbAutoBackup.isChecked
                config.mcpServerEnabled = cbMcpEnabled.isChecked
                config.mcpServerPort = etMcpPort.text?.toString()?.toIntOrNull() ?: 8765
                pro.sketchware.ai.mcp.SketchwareMcpServer.ensureStarted(context)
                SketchwareUtil.toast("Konfigurasi AI Agent & MCP Server disimpan!")
                onSaved?.invoke()
                d.dismiss()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }
}
