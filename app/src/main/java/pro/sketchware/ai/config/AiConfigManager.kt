package pro.sketchware.ai.config

import android.content.Context
import android.content.SharedPreferences
import pro.sketchware.ai.security.SecretVault

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GEMINI(
        displayName = "Google Gemini API",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-pro"
    ),
    OPENAI(
        displayName = "OpenAI API",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4.1"
    ),
    OPENROUTER(
        displayName = "OpenRouter API",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "anthropic/claude-sonnet-4"
    ),
    CLAUDE(
        displayName = "Anthropic Claude API",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-sonnet-4-20250514"
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible (Ollama / Groq / DeepSeek)",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat"
    );

    companion object {
        @JvmStatic
        fun fromName(name: String?): AiProviderType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: GEMINI
        }
    }
}

enum class AgentAutonomyMode(
    val title: String,
    val subtitle: String,
    val allowsWriteTools: Boolean,
    val autoApproveWrites: Boolean
) {
    ASK(
        title = "Ask",
        subtitle = "Tanya jawab & baca proyek (Read-only)",
        allowsWriteTools = false,
        autoApproveWrites = false
    ),
    COPILOT(
        title = "Copilot",
        subtitle = "Bantu edit layar/event yang sedang dibuka",
        allowsWriteTools = true,
        autoApproveWrites = true
    ),
    AGENT(
        title = "Agent",
        subtitle = "Eksekusi multi-step lintas layar, komponen & logika",
        allowsWriteTools = true,
        autoApproveWrites = true
    ),
    AUTOPILOT(
        title = "Autopilot 0→APK",
        subtitle = "Otonom penuh: Rancang, Bangun, Compile APK & Self-Heal",
        allowsWriteTools = true,
        autoApproveWrites = true
    ),
    TUTOR(
        title = "Tutor",
        subtitle = "Jelaskan cara kerja blok & bimbing langkah demi langkah",
        allowsWriteTools = false,
        autoApproveWrites = false
    );

    companion object {
        @JvmStatic
        fun fromName(name: String?): AgentAutonomyMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AGENT
        }
    }
}

enum class AgentLanguage(
    val code: String,
    val displayLabel: String,
    val shortBadge: String,
    val llmLanguageName: String
) {
    AUTO("auto", "🌐 Auto (System Default)", "🌐 Auto", "the user's device/message language"),
    ID("id", "🇮🇩 Bahasa Indonesia", "🇮🇩 ID", "Indonesian (Bahasa Indonesia)"),
    EN("en", "🇺🇸 English", "🇺🇸 EN", "English"),
    ES("es", "🇪🇸 Español", "🇪🇸 ES", "Spanish (Español)"),
    PT("pt", "🇧🇷 Português", "🇧🇷 PT", "Portuguese (Português)"),
    AR("ar", "🇸🇦 العربية", "🇸🇦 AR", "Arabic (العربية)"),
    HI("hi", "🇮🇳 हिन्दी", "🇮🇳 HI", "Hindi (हिन्दी)"),
    RU("ru", "🇷🇺 Русский", "🇷🇺 RU", "Russian (Русский)"),
    ZH("zh", "🇨🇳 中文 (简体)", "🇨🇳 ZH", "Simplified Chinese (简体中文)"),
    JA("ja", "🇯🇵 日本語", "🇯🇵 JA", "Japanese (日本語)"),
    KO("ko", "🇰🇷 한국어", "🇰🇷 KO", "Korean (한국어)"),
    FR("fr", "🇫🇷 Français", "🇫🇷 FR", "French (Français)"),
    DE("de", "🇩🇪 Deutsch", "🇩🇪 DE", "German (Deutsch)"),
    TR("tr", "🇹🇷 Türkçe", "🇹🇷 TR", "Turkish (Türkçe)"),
    VI("vi", "🇻🇳 Tiếng Việt", "🇻🇳 VI", "Vietnamese (Tiếng Việt)"),
    TH("th", "🇹🇭 ไทย", "🇹🇭 TH", "Thai (ภาษาไทย)"),
    MS("ms", "🇲🇾 Bahasa Melayu", "🇲🇾 MS", "Malay (Bahasa Melayu)");

    fun resolved(): AgentLanguage {
        if (this != AUTO) return this
        val sys = java.util.Locale.getDefault().language.lowercase(java.util.Locale.ROOT)
        return when (sys) {
            "in", "id" -> ID
            "es" -> ES
            "pt" -> PT
            "ar" -> AR
            "hi" -> HI
            "ru" -> RU
            "zh" -> ZH
            "ja" -> JA
            "ko" -> KO
            "fr" -> FR
            "de" -> DE
            "tr" -> TR
            "vi" -> VI
            "th" -> TH
            "ms" -> MS
            else -> EN
        }
    }

    fun systemPromptDirective(): String {
        val eff = resolved()
        return if (this == AUTO) {
            "IMPORTANT LANGUAGE RULE: Respond in the same language the user writes in (or in ${eff.llmLanguageName} by default). Keep all explanations clear, natural, and well-structured."
        } else {
            "IMPORTANT LANGUAGE RULE: The user has explicitly selected **${eff.llmLanguageName}** (${eff.displayLabel}) as their preferred language. You MUST write all assistant responses, task plans, and explanations in **${eff.llmLanguageName}**."
        }
    }

    companion object {
        @JvmStatic
        fun fromCode(code: String?): AgentLanguage {
            if (code.isNullOrBlank()) return AUTO
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
            } ?: AUTO
        }
    }
}

data class SavedAiProfile(
    val id: String,
    val label: String,
    val provider: AiProviderType,
    val apiKey: String,
    val modelId: String,
    val baseUrl: String
)

class AiConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        try {
            migrateSecretsToVaultIfNeeded()
        } catch (_: Throwable) {
        }
    }

    /**
     * One-time, idempotent migration that re-encrypts any API keys / saved profiles still stored
     * as plaintext by older builds. Cheap after the first pass because encrypted values are skipped.
     */
    private fun migrateSecretsToVaultIfNeeded() {
        var changed = false
        val editor = prefs.edit()
        for (provider in AiProviderType.entries) {
            val key = "${KEY_API_KEY_PREFIX}${provider.name}"
            val raw = prefs.getString(key, "").orEmpty()
            if (raw.isNotEmpty() && !SecretVault.isEncrypted(raw)) {
                editor.putString(key, SecretVault.encrypt(raw))
                changed = true
            }
        }
        val profiles = prefs.getString(KEY_SAVED_PROFILES, "").orEmpty()
        if (profiles.isNotBlank() && !SecretVault.isEncrypted(profiles)) {
            editor.putString(KEY_SAVED_PROFILES, SecretVault.encrypt(profiles))
            changed = true
        }
        if (changed) editor.apply()
    }

    var providerType: AiProviderType
        get() = AiProviderType.fromName(prefs.getString(KEY_PROVIDER, AiProviderType.GEMINI.name))
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.name).apply()

    fun getApiKey(provider: AiProviderType = providerType): String {
        val raw = prefs.getString("${KEY_API_KEY_PREFIX}${provider.name}", "")?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        if (!SecretVault.isEncrypted(raw)) {
            // Legacy plaintext found (e.g. migration was skipped): re-encrypt and return it.
            setApiKey(raw, provider)
            return raw
        }
        return SecretVault.decrypt(raw)?.trim().orEmpty()
    }

    fun setApiKey(apiKey: String, provider: AiProviderType = providerType) {
        val clean = apiKey.trim()
        prefs.edit()
            .putString("${KEY_API_KEY_PREFIX}${provider.name}", SecretVault.encrypt(clean))
            .apply()
    }

    fun clearApiKey(provider: AiProviderType = providerType) {
        prefs.edit().remove("${KEY_API_KEY_PREFIX}${provider.name}").apply()
    }

    fun clearAllApiKeys() {
        val editor = prefs.edit()
        for (p in AiProviderType.entries) {
            editor.remove("${KEY_API_KEY_PREFIX}${p.name}")
        }
        editor.remove(KEY_SAVED_PROFILES)
        editor.apply()
    }

    fun getBaseUrl(provider: AiProviderType = providerType): String {
        val custom = prefs.getString("${KEY_BASE_URL_PREFIX}${provider.name}", "")?.trim().orEmpty()
        return custom.ifEmpty { provider.defaultBaseUrl }
    }

    fun setBaseUrl(baseUrl: String, provider: AiProviderType = providerType) {
        prefs.edit().putString("${KEY_BASE_URL_PREFIX}${provider.name}", baseUrl.trim()).apply()
    }

    fun getModelId(provider: AiProviderType = providerType): String {
        val custom = prefs.getString("${KEY_MODEL_PREFIX}${provider.name}", "")?.trim().orEmpty()
        return custom.ifEmpty { provider.defaultModel }
    }

    fun setModelId(modelId: String, provider: AiProviderType = providerType) {
        prefs.edit().putString("${KEY_MODEL_PREFIX}${provider.name}", modelId.trim()).apply()
    }

    fun getCustomModels(provider: AiProviderType = providerType): List<String> {
        val raw = prefs.getString("${KEY_CUSTOM_MODELS_PREFIX}${provider.name}", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun addCustomModel(modelId: String, provider: AiProviderType = providerType) {
        val clean = modelId.trim()
        if (clean.isEmpty()) return
        val updated = (getCustomModels(provider) + clean).distinct()
        prefs.edit().putString("${KEY_CUSTOM_MODELS_PREFIX}${provider.name}", updated.joinToString("\n")).apply()
    }

    fun removeCustomModel(modelId: String, provider: AiProviderType = providerType) {
        val clean = modelId.trim()
        val updated = getCustomModels(provider).filterNot { it.equals(clean, ignoreCase = true) }
        prefs.edit().putString("${KEY_CUSTOM_MODELS_PREFIX}${provider.name}", updated.joinToString("\n")).apply()
    }

    fun getSavedProfiles(): List<SavedAiProfile> {
        val stored = prefs.getString(KEY_SAVED_PROFILES, "").orEmpty()
        if (stored.isBlank()) return emptyList()
        val raw = if (SecretVault.isEncrypted(stored)) {
            SecretVault.decrypt(stored) ?: return emptyList()
        } else {
            stored
        }
        return try {
            val arr = com.google.gson.JsonParser.parseString(raw).asJsonArray
            val list = mutableListOf<SavedAiProfile>()
            for (el in arr) {
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: continue
                val provider = AiProviderType.fromName(obj.get("provider")?.asString)
                val apiKey = obj.get("apiKey")?.asString.orEmpty()
                val modelId = obj.get("modelId")?.asString.orEmpty().ifEmpty { provider.defaultModel }
                val baseUrl = obj.get("baseUrl")?.asString.orEmpty().ifEmpty { provider.defaultBaseUrl }
                val label = obj.get("label")?.asString.orEmpty().ifEmpty {
                    "${provider.name} · $modelId (${maskApiKey(apiKey)})"
                }
                list.add(SavedAiProfile(id, label, provider, apiKey, modelId, baseUrl))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveProfile(
        provider: AiProviderType,
        apiKey: String,
        modelId: String,
        baseUrl: String,
        customLabel: String = ""
    ): SavedAiProfile {
        val cleanKey = apiKey.trim()
        val cleanModel = modelId.trim().ifEmpty { provider.defaultModel }
        val cleanUrl = baseUrl.trim().ifEmpty { provider.defaultBaseUrl }
        val existing = getSavedProfiles().toMutableList()
        val dupIndex = existing.indexOfFirst {
            it.provider == provider && it.apiKey == cleanKey && it.modelId.equals(cleanModel, ignoreCase = true)
        }
        val id = if (dupIndex >= 0) existing[dupIndex].id else java.util.UUID.randomUUID().toString()
        val label = customLabel.trim().ifEmpty {
            "${provider.name} · $cleanModel (${maskApiKey(cleanKey)})"
        }
        val profile = SavedAiProfile(id, label, provider, cleanKey, cleanModel, cleanUrl)
        if (dupIndex >= 0) {
            existing[dupIndex] = profile
        } else {
            existing.add(0, profile)
        }
        persistProfiles(existing)
        if (cleanModel.isNotEmpty()) {
            addCustomModel(cleanModel, provider)
        }
        return profile
    }

    fun deleteProfile(id: String) {
        val updated = getSavedProfiles().filterNot { it.id == id }
        persistProfiles(updated)
    }

    private fun persistProfiles(profiles: List<SavedAiProfile>) {
        val arr = com.google.gson.JsonArray()
        for (p in profiles) {
            val obj = com.google.gson.JsonObject().apply {
                addProperty("id", p.id)
                addProperty("label", p.label)
                addProperty("provider", p.provider.name)
                addProperty("apiKey", p.apiKey)
                addProperty("modelId", p.modelId)
                addProperty("baseUrl", p.baseUrl)
            }
            arr.add(obj)
        }
        prefs.edit().putString(KEY_SAVED_PROFILES, SecretVault.encrypt(arr.toString())).apply()
    }

    var agentLanguage: AgentLanguage
        get() = AgentLanguage.fromCode(prefs.getString(KEY_AGENT_LANGUAGE, AgentLanguage.AUTO.code))
        set(value) = prefs.edit().putString(KEY_AGENT_LANGUAGE, value.code).apply()

    var autonomyMode: AgentAutonomyMode
        get() = AgentAutonomyMode.fromName(prefs.getString(KEY_AUTONOMY_MODE, AgentAutonomyMode.AGENT.name))
        set(value) = prefs.edit().putString(KEY_AUTONOMY_MODE, value.name).apply()

    var maxStepsPerRun: Int
        get() = prefs.getInt(KEY_MAX_STEPS, 30).coerceIn(5, 100)
        set(value) = prefs.edit().putInt(KEY_MAX_STEPS, value.coerceIn(5, 100)).apply()

    var autoBuildAndHeal: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BUILD_HEAL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BUILD_HEAL, value).apply()

    var autoBackupBeforeRun: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP, value).apply()

    var mcpServerEnabled: Boolean
        get() = prefs.getBoolean(KEY_MCP_SERVER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MCP_SERVER_ENABLED, value).apply()

    var mcpServerPort: Int
        get() = prefs.getInt(KEY_MCP_SERVER_PORT, 8765).coerceIn(1024, 65535)
        set(value) = prefs.edit().putInt(KEY_MCP_SERVER_PORT, value.coerceIn(1024, 65535)).apply()

    fun isConfigured(): Boolean {
        val key = getApiKey()
        if (key.isNotEmpty()) return true
        // Custom local endpoints (e.g. Ollama on LAN) may not require an API key
        return providerType == AiProviderType.CUSTOM && getBaseUrl().isNotEmpty()
    }

    companion object {
        private const val PREFS_NAME = "sketchware_ai_agent_config"
        private const val KEY_PROVIDER = "provider_type"
        private const val KEY_API_KEY_PREFIX = "api_key_"
        private const val KEY_BASE_URL_PREFIX = "base_url_"
        private const val KEY_MODEL_PREFIX = "model_id_"
        private const val KEY_CUSTOM_MODELS_PREFIX = "custom_models_"
        private const val KEY_SAVED_PROFILES = "saved_ai_profiles_json"
        private const val KEY_AGENT_LANGUAGE = "agent_language"
        private const val KEY_AUTONOMY_MODE = "autonomy_mode"
        private const val KEY_MAX_STEPS = "max_steps_per_run"
        private const val KEY_AUTO_BUILD_HEAL = "auto_build_and_heal"
        private const val KEY_AUTO_BACKUP = "auto_backup_before_run"
        private const val KEY_MCP_SERVER_ENABLED = "mcp_server_enabled"
        private const val KEY_MCP_SERVER_PORT = "mcp_server_port"

        fun maskApiKey(key: String): String {
            val clean = key.trim()
            if (clean.isEmpty()) return "Tanpa Key"
            if (clean.length <= 8) return "••••" + clean.takeLast(2)
            return clean.take(4) + "••••" + clean.takeLast(4)
        }
    }
}
