package pro.sketchware.ai.project

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Environment
import org.xmlpull.v1.XmlPullParser
import pro.sketchware.ai.ui.WorkspaceArtifactsManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipFile

/**
 * REA (Reverse Engineer Anything) Engine for Sketchware Pro AI Agent Studio.
 *
 * Provides native binary APK inspection, binary XML (AXML) decompilation via Android's
 * [AssetManager] + [Resources] + pure-Kotlin AXML fallback, pure-Kotlin DEX (`classes*.dex`)
 * class/method/field/string table analysis, and automated **Full APK-to-Sketchware Project Cloning**.
 */
object ApkProjectReconstructor {

    private val FRAMEWORK_RESOURCE_PREFIXES = listOf(
        "abc_",
        "mtrl_",
        "design_",
        "material_",
        "notification_",
        "select_dialog_",
        "support_simple_spinner_",
        "common_google_signin_",
        "common_full_open_on_phone",
        "googleg_",
        "btn_checkbox_",
        "btn_radio_",
        "test_",
        "ime_",
        "custom_dialog"
    )

    private val NOISE_CLASS_PREFIXES = listOf(
        "androidx.",
        "android.support.",
        "com.google.android.material.",
        "com.google.android.gms.",
        "com.google.firebase.",
        "com.google.common.",
        "com.google.gson.",
        "kotlin.",
        "kotlinx.",
        "org.intellij.",
        "org.jetbrains.",
        "okhttp3.",
        "okio.",
        "retrofit2.",
        "com.bumptech.glide.",
        "com.squareup.",
        "io.reactivex.",
        "org.apache."
    )

    data class DexClassSummary(
        val className: String,
        val superClass: String,
        val sourceFile: String?,
        val methods: List<String>,
        val fields: List<String>
    )

    data class DexAnalysisReport(
        val dexFilesCount: Int,
        val totalClassesCount: Int,
        val totalMethodsCount: Int,
        val totalFieldsCount: Int,
        val appPackagePrefix: String,
        val appClasses: List<DexClassSummary>,
        val discoveredUrls: List<String>,
        val interestingStrings: List<String>
    )

    private fun isFrameworkResourceName(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return FRAMEWORK_RESOURCE_PREFIXES.any { lower.startsWith(it) }
    }

    /**
     * Resolves an APK path that may be absolute or relative to `.sketchware` / project context attachments.
     */
    fun resolveApkFile(scId: String, rawPath: String): File {
        val clean = rawPath.trim()
        val direct = File(clean)
        if (direct.isFile) return direct

        val sdcard = Environment.getExternalStorageDirectory()
        val candidates = listOf(
            File(sdcard, clean.removePrefix("/")),
            File(sdcard, ".sketchware/data/$scId/files/context_attachments/$clean"),
            File(sdcard, ".sketchware/data/$scId/files/$clean"),
            File(sdcard, "Download/$clean")
        )
        return candidates.firstOrNull { it.isFile } ?: direct
    }

    /**
     * Deep inspection of an `.apk` file: Manifest metadata, Activities, Services, Permissions,
     * Layout XML inventory, Drawables/Assets inventory, and DEX (`classes*.dex`) summary.
     */
    fun inspectApk(
        context: Context,
        scId: String,
        rawApkPath: String
    ): String {
        val apkFile = resolveApkFile(scId, rawApkPath)
        if (!apkFile.isFile) {
            return "Error: File APK tidak ditemukan di '${apkFile.absolutePath}'. Pastikan path file APK benar atau lampirkan melalui tombol '+' (Add Context -> Tambahkan File APK)."
        }

        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_PERMISSIONS or
            PackageManager.GET_META_DATA
        @Suppress("DEPRECATION")
        val pkgInfo = try {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (_: Throwable) {
            null
        }

        var appLabel = apkFile.nameWithoutExtension
        val pkgName = pkgInfo?.packageName ?: ""
        val verName = pkgInfo?.versionName ?: "1.0"
        @Suppress("DEPRECATION")
        val verCode = pkgInfo?.versionCode ?: 1
        val minSdk = pkgInfo?.applicationInfo?.minSdkVersion ?: 21
        val targetSdk = pkgInfo?.applicationInfo?.targetSdkVersion ?: 28

        val appInfo = pkgInfo?.applicationInfo
        if (appInfo != null) {
            try {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                val lbl = pm.getApplicationLabel(appInfo)?.toString()
                if (!lbl.isNullOrBlank()) appLabel = lbl
            } catch (_: Throwable) {
            }
        }

        val activities = pkgInfo?.activities?.map { it.name }.orEmpty()
        val services = pkgInfo?.services?.map { it.name }.orEmpty()
        val receivers = pkgInfo?.receivers?.map { it.name }.orEmpty()
        val permissions = pkgInfo?.requestedPermissions?.toList().orEmpty()

        val allLayouts = mutableListOf<String>()
        val appLayouts = mutableListOf<String>()
        val appDrawables = mutableListOf<String>()
        val assets = mutableListOf<String>()
        val fonts = mutableListOf<String>()
        val nativeLibs = mutableListOf<String>()
        var dexCount = 0
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
                        name.startsWith("res/layout") && name.endsWith(".xml") -> {
                            val base = name.substringAfterLast('/')
                            if (base !in allLayouts) allLayouts.add(base)
                            if (!isFrameworkResourceName(base) && base !in appLayouts) {
                                appLayouts.add(base)
                            }
                        }
                        (name.startsWith("res/drawable") || name.startsWith("res/mipmap")) && !e.isDirectory -> {
                            val base = name.substringAfterLast('/')
                            if (!isFrameworkResourceName(base) && base !in appDrawables) {
                                appDrawables.add(base)
                            }
                        }
                        name.startsWith("res/font") && !e.isDirectory -> {
                            val base = name.substringAfterLast('/')
                            if (base !in fonts) fonts.add(base)
                        }
                        name.startsWith("assets/") && !e.isDirectory -> {
                            assets.add(name.removePrefix("assets/"))
                        }
                        name.startsWith("lib/") && name.endsWith(".so") -> {
                            nativeLibs.add(name.removePrefix("lib/"))
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val dexReport = analyzeApkDex(apkFile, pkgName, filterQuery = "", maxClasses = 60)

        return buildString {
            appendLine("=== 🔍 REA APK DEEP INSPECTION REPORT ===")
            appendLine("File APK          : ${apkFile.absolutePath} (${apkFile.length() / 1024} KB)")
            appendLine("App Label         : $appLabel")
            appendLine("Package Name      : ${pkgName.ifEmpty { "(unknown)" }}")
            appendLine("Version           : $verName (code=$verCode) | SDK: min=$minSdk, target=$targetSdk")
            appendLine("ZIP Entries       : $totalEntries | DEX Files: $dexCount")

            appendLine("\n--- 1. ACTIVITIES (${activities.size}) ---")
            if (activities.isEmpty()) {
                appendLine("(Tidak ada Activity terdeteksi dari PackageManager; gunakan action='decode_xml' pada AndroidManifest.xml)")
            } else {
                activities.forEachIndexed { idx, act -> appendLine("${idx + 1}. $act") }
            }

            if (services.isNotEmpty() || receivers.isNotEmpty()) {
                appendLine("\n--- 2. SERVICES (${services.size}) & RECEIVERS (${receivers.size}) ---")
                services.forEach { appendLine("- [Service] $it") }
                receivers.forEach { appendLine("- [Receiver] $it") }
            }

            appendLine("\n--- 3. PERMISSIONS (${permissions.size}) ---")
            if (permissions.isEmpty()) {
                appendLine("(Tidak ada permission khusus)")
            } else {
                appendLine(permissions.joinToString(", "))
            }

            appendLine("\n--- 4. APP LAYOUTS (${appLayouts.size} app layouts / ${allLayouts.size} total) ---")
            if (appLayouts.isEmpty()) {
                appendLine(allLayouts.take(30).joinToString(", "))
            } else {
                appLayouts.forEach { appendLine("- res/layout/$it") }
            }

            appendLine("\n--- 5. DRAWABLES (${appDrawables.size}), FONTS (${fonts.size}), ASSETS (${assets.size}), NATIVE LIBS (${nativeLibs.size}) ---")
            if (appDrawables.isNotEmpty()) {
                appendLine("Drawables : ${appDrawables.take(40).joinToString(", ")}${if (appDrawables.size > 40) " ... (+${appDrawables.size - 40} lainnya)" else ""}")
            }
            if (fonts.isNotEmpty()) {
                appendLine("Fonts     : ${fonts.joinToString(", ")}")
            }
            if (assets.isNotEmpty()) {
                appendLine("Assets    : ${assets.take(35).joinToString(", ")}${if (assets.size > 35) " ... (+${assets.size - 35} lainnya)" else ""}")
            }
            if (nativeLibs.isNotEmpty()) {
                appendLine("JNI .so   : ${nativeLibs.take(20).joinToString(", ")}")
            }

            appendLine("\n--- 6. DEX BYTECODE ANALYSIS (${dexReport.appClasses.size} App Classes / ${dexReport.totalClassesCount} Total Classes, ${dexReport.totalMethodsCount} Methods) ---")
            for (cls in dexReport.appClasses.take(35)) {
                val mPreview = cls.methods.take(8).joinToString(", ")
                val fPreview = cls.fields.take(5).joinToString(", ")
                appendLine("• ${cls.className} (extends ${cls.superClass.substringAfterLast('.')})")
                if (fPreview.isNotEmpty()) appendLine("    Fields  : $fPreview")
                if (mPreview.isNotEmpty()) appendLine("    Methods : $mPreview")
            }
            if (dexReport.discoveredUrls.isNotEmpty()) {
                appendLine("\n--- 7. DISCOVERED URLs & ENDPOINTS IN DEX (${dexReport.discoveredUrls.size}) ---")
                dexReport.discoveredUrls.take(25).forEach { appendLine("- $it") }
            }
            if (dexReport.interestingStrings.isNotEmpty()) {
                appendLine("\n--- 8. INTERESTING CONSTANTS / KEYS IN DEX ---")
                dexReport.interestingStrings.take(30).forEach { appendLine("- $it") }
            }

            appendLine("\n💡 Tip Selanjutnya:")
            appendLine("- Panggil `reconstruct_apk_project(action=\"full_clone\", apk_path=\"${apkFile.absolutePath}\")` untuk meng-clone seluruh layout, drawable, asset, font, permission, dan menghasilkan blueprint lengkap ke proyek Sketchware #$scId!")
            appendLine("- Panggil `reconstruct_apk_project(action=\"decode_xml\", apk_path=\"${apkFile.absolutePath}\", xml_entry=\"res/layout/<nama>.xml\")` untuk membaca dekompilasi XML tertentu.")
            appendLine("- Panggil `reconstruct_apk_project(action=\"analyze_dex\", apk_path=\"${apkFile.absolutePath}\", query=\"<NamaClassAtauKataKunci>\")` untuk melihat detail method/field/string pada class DEX tertentu.")
        }.trim()
    }

    /**
     * Decodes a binary XML entry (`AndroidManifest.xml`, `res/layout/activity_main.xml`, etc.) inside an APK
     * into human-readable XML using Android's native [AssetManager] + [Resources] table resolution,
     * with a pure-Kotlin binary AXML fallback decoder.
     */
    fun decodeApkXmlEntry(
        context: Context,
        apkFile: File,
        xmlEntryPath: String
    ): String {
        val normalizedEntry = when {
            xmlEntryPath.equals("manifest", ignoreCase = true) ||
                xmlEntryPath.equals("AndroidManifest.xml", ignoreCase = true) -> "AndroidManifest.xml"
            xmlEntryPath.startsWith("res/") -> xmlEntryPath
            xmlEntryPath.endsWith(".xml") -> "res/layout/$xmlEntryPath"
            else -> "res/layout/$xmlEntryPath.xml"
        }

        // 1. Try Android native AssetManager + Resources + XmlResourceParser first (resolves @0x7f... IDs via resources.arsc!)
        try {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            val cookie = (addAssetPath.invoke(assetManager, apkFile.absolutePath) as? Int) ?: 0
            if (cookie != 0) {
                @Suppress("DEPRECATION")
                val apkRes = Resources(
                    assetManager,
                    context.resources.displayMetrics,
                    context.resources.configuration
                )
                val parser = assetManager.openXmlResourceParser(cookie, normalizedEntry)
                val xmlText = renderXmlFromResourceParser(parser, apkRes)
                parser.close()
                if (xmlText.isNotBlank()) {
                    return xmlText
                }
            }
        } catch (_: Throwable) {
        }

        // 2. Pure-Kotlin binary AXML fallback parser directly from the ZIP entry bytes
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry(normalizedEntry)
                    ?: zip.entries().asSequence().firstOrNull {
                        it.name.equals(normalizedEntry, ignoreCase = true) ||
                            it.name.endsWith("/" + normalizedEntry.substringAfterLast('/'), ignoreCase = true)
                    }
                    ?: return "Error: Entry XML '$normalizedEntry' tidak ditemukan di dalam ${apkFile.name}."
                val bytes = zip.getInputStream(entry).readBytes()
                if (bytes.isNotEmpty() && (bytes[0] == '<'.code.toByte() || String(bytes, 0, minOf(5, bytes.size)).startsWith("<?xml"))) {
                    String(bytes, Charsets.UTF_8)
                } else {
                    decodeBinaryXmlPureKotlin(bytes)
                }
            }
        } catch (e: Throwable) {
            "Error mendekode XML '$normalizedEntry': ${e.message}"
        }
    }

    private fun renderXmlFromResourceParser(parser: XmlResourceParser, apkRes: Resources): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        var indent = 0
        var rootEmitted = false

        fun indentStr(level: Int): String = "    ".repeat(level.coerceAtLeast(0))

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name ?: "View"
                    sb.append(indentStr(indent)).append("<").append(tagName)
                    if (!rootEmitted) {
                        rootEmitted = true
                        sb.append("\n").append(indentStr(indent + 1))
                            .append("xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                        sb.append("\n").append(indentStr(indent + 1))
                            .append("xmlns:app=\"http://schemas.android.com/apk/res-auto\"")
                    }
                    val attrCount = parser.attributeCount
                    for (i in 0 until attrCount) {
                        val rawNs = parser.getAttributeNamespace(i).orEmpty()
                        val prefix = when {
                            rawNs.contains("schemas.android.com/apk/res/android") -> "android:"
                            rawNs.contains("schemas.android.com/apk/res-auto") -> "app:"
                            rawNs.isNotEmpty() -> "app:"
                            else -> "android:"
                        }
                        val attrName = parser.getAttributeName(i).orEmpty().ifEmpty { "attr_$i" }
                        if (attrName == "xmlns") continue
                        val rawVal = parser.getAttributeValue(i).orEmpty()
                        val resolvedVal = resolveAttributeValue(attrName, rawVal, parser.getAttributeResourceValue(i, 0), apkRes)
                        sb.append("\n").append(indentStr(indent + 1))
                            .append(prefix).append(attrName).append("=\"").append(escapeXmlAttr(resolvedVal)).append("\"")
                    }
                    sb.append(">\n")
                    indent++
                }
                XmlPullParser.END_TAG -> {
                    indent = (indent - 1).coerceAtLeast(0)
                    val tagName = parser.name ?: "View"
                    sb.append(indentStr(indent)).append("</").append(tagName).append(">\n")
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        sb.append(indentStr(indent)).append(escapeXmlAttr(text)).append("\n")
                    }
                }
            }
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun resolveAttributeValue(
        attrName: String,
        rawVal: String,
        resId: Int,
        apkRes: Resources
    ): String {
        if (resId != 0 && (rawVal.startsWith("@") || rawVal.startsWith("?"))) {
            try {
                val typeName = apkRes.getResourceTypeName(resId)
                val entryName = apkRes.getResourceEntryName(resId)
                val pkgName = apkRes.getResourcePackageName(resId)
                when (typeName) {
                    "id" -> return if (attrName == "id") "@+id/$entryName" else "@id/$entryName"
                    "string" -> {
                        return try {
                            apkRes.getString(resId)
                        } catch (_: Throwable) {
                            "@string/$entryName"
                        }
                    }
                    "color" -> {
                        return try {
                            @Suppress("DEPRECATION")
                            val c = apkRes.getColor(resId)
                            String.format("#%08X", c)
                        } catch (_: Throwable) {
                            "@color/$entryName"
                        }
                    }
                    "dimen" -> {
                        return try {
                            val px = apkRes.getDimension(resId)
                            val dp = (px / apkRes.displayMetrics.density).toInt().coerceAtLeast(1)
                            if (attrName.contains("textSize", ignoreCase = true)) "${dp}sp" else "${dp}dp"
                        } catch (_: Throwable) {
                            "@dimen/$entryName"
                        }
                    }
                    "drawable", "mipmap" -> {
                        return if (pkgName == "android") "@android:drawable/$entryName" else "@drawable/$entryName"
                    }
                    "style" -> {
                        return if (pkgName == "android") "@android:style/$entryName" else "@style/$entryName"
                    }
                    "layout" -> return "@layout/$entryName"
                }
            } catch (_: Throwable) {
            }
        }

        // Convert common integer constants for layout_width / layout_height / orientation
        if (attrName == "layout_width" || attrName == "layout_height") {
            return when (rawVal) {
                "-1" -> "match_parent"
                "-2" -> "wrap_content"
                else -> rawVal
            }
        }
        if (attrName == "orientation") {
            return when (rawVal) {
                "0" -> "horizontal"
                "1" -> "vertical"
                else -> rawVal
            }
        }
        if (attrName == "visibility") {
            return when (rawVal) {
                "0" -> "visible"
                "1" -> "invisible"
                "2" -> "gone"
                else -> rawVal
            }
        }
        return rawVal
    }

    /**
     * Pure-Kotlin binary Android XML (`AXML` chunk type `0x0003`) decoder fallback.
     */
    internal fun decodeBinaryXmlPureKotlin(bytes: ByteArray): String {
        if (bytes.size < 8) return ""
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileType = buf.getShort(0).toInt() and 0xFFFF
        if (fileType != 0x0003) {
            return String(bytes, Charsets.UTF_8)
        }

        val headerSize = buf.getShort(2).toInt() and 0xFFFF
        val fileSize = buf.getInt(4).coerceAtMost(bytes.size)
        var pos = headerSize
        var strings = emptyList<String>()
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        var indent = 0
        var rootEmitted = false

        while (pos + 8 <= fileSize) {
            val chunkType = buf.getShort(pos).toInt() and 0xFFFF
            val chunkHeaderSize = buf.getShort(pos + 2).toInt() and 0xFFFF
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize <= 0 || pos + chunkSize > fileSize) break

            when (chunkType) {
                0x0001 -> { // RES_STRING_POOL_TYPE
                    strings = parseAxmlStringPool(buf, pos, chunkHeaderSize, chunkSize)
                }
                0x0102 -> { // RES_XML_START_ELEMENT_TYPE
                    val nameIdx = buf.getInt(pos + 20)
                    val attrStart = buf.getShort(pos + 24).toInt() and 0xFFFF
                    val attrSize = (buf.getShort(pos + 26).toInt() and 0xFFFF).coerceAtLeast(20)
                    val attrCount = buf.getShort(pos + 28).toInt() and 0xFFFF
                    val tagName = strings.getOrNull(nameIdx)?.ifEmpty { "View" } ?: "View"

                    sb.append("    ".repeat(indent)).append("<").append(tagName)
                    if (!rootEmitted) {
                        rootEmitted = true
                        sb.append("\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                        sb.append("\n    xmlns:app=\"http://schemas.android.com/apk/res-auto\"")
                    }
                    var attrPos = pos + 16 + attrStart
                    for (i in 0 until attrCount) {
                        if (attrPos + 20 > pos + chunkSize) break
                        val aNameIdx = buf.getInt(attrPos + 4)
                        val aRawValIdx = buf.getInt(attrPos + 8)
                        val aDataType = buf.get(attrPos + 15).toInt() and 0xFF
                        val aData = buf.getInt(attrPos + 16)
                        val aName = strings.getOrNull(aNameIdx).orEmpty().ifEmpty { "attr_$i" }
                        val aVal = if (aRawValIdx >= 0 && aRawValIdx < strings.size) {
                            strings[aRawValIdx]
                        } else {
                            formatAxmlTypedValue(aName, aDataType, aData, strings)
                        }
                        sb.append("\n").append("    ".repeat(indent + 1))
                            .append("android:").append(aName).append("=\"").append(escapeXmlAttr(aVal)).append("\"")
                        attrPos += attrSize
                    }
                    sb.append(">\n")
                    indent++
                }
                0x0103 -> { // RES_XML_END_ELEMENT_TYPE
                    indent = (indent - 1).coerceAtLeast(0)
                    val nameIdx = buf.getInt(pos + 20)
                    val tagName = strings.getOrNull(nameIdx)?.ifEmpty { "View" } ?: "View"
                    sb.append("    ".repeat(indent)).append("</").append(tagName).append(">\n")
                }
            }
            pos += chunkSize
        }
        return sb.toString().trim()
    }

    private fun parseAxmlStringPool(buf: ByteBuffer, chunkStart: Int, headerSize: Int, chunkSize: Int): List<String> {
        return try {
            val stringCount = buf.getInt(chunkStart + 8)
            val flags = buf.getInt(chunkStart + 16)
            val stringsStart = buf.getInt(chunkStart + 20)
            val isUtf8 = (flags and (1 shl 8)) != 0
            val offsetsBase = chunkStart + headerSize
            val dataBase = chunkStart + stringsStart
            val result = ArrayList<String>(stringCount.coerceAtMost(20000))
            for (i in 0 until stringCount.coerceAtMost(20000)) {
                val strOffset = buf.getInt(offsetsBase + i * 4)
                val p = dataBase + strOffset
                if (p < 0 || p >= chunkStart + chunkSize) {
                    result.add("")
                    continue
                }
                if (isUtf8) {
                    var cursor = p
                    val b1 = buf.get(cursor).toInt() and 0xFF
                    cursor += if ((b1 and 0x80) != 0) 2 else 1
                    val b2 = buf.get(cursor).toInt() and 0xFF
                    val byteLen = if ((b2 and 0x80) != 0) {
                        cursor += 2
                        ((b2 and 0x7F) shl 8) or (buf.get(cursor - 1).toInt() and 0xFF)
                    } else {
                        cursor += 1
                        b2
                    }
                    val safeLen = byteLen.coerceIn(0, (chunkStart + chunkSize - cursor).coerceAtLeast(0))
                    val strBytes = ByteArray(safeLen)
                    for (k in 0 until safeLen) strBytes[k] = buf.get(cursor + k)
                    result.add(String(strBytes, Charsets.UTF_8))
                } else {
                    var cursor = p
                    val u1 = buf.getShort(cursor).toInt() and 0xFFFF
                    val charLen = if ((u1 and 0x8000) != 0) {
                        val u2 = buf.getShort(cursor + 2).toInt() and 0xFFFF
                        cursor += 4
                        ((u1 and 0x7FFF) shl 16) or u2
                    } else {
                        cursor += 2
                        u1
                    }
                    val safeChars = charLen.coerceIn(0, ((chunkStart + chunkSize - cursor) / 2).coerceAtLeast(0))
                    val chars = CharArray(safeChars)
                    for (k in 0 until safeChars) {
                        chars[k] = (buf.getShort(cursor + k * 2).toInt() and 0xFFFF).toChar()
                    }
                    result.add(String(chars))
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun formatAxmlTypedValue(attrName: String, dataType: Int, data: Int, strings: List<String>): String {
        return when (dataType) {
            0x03 -> strings.getOrNull(data).orEmpty()
            0x01 -> String.format("@0x%08X", data)
            0x10, 0x11 -> {
                if (attrName == "layout_width" || attrName == "layout_height") {
                    when (data) {
                        -1 -> "match_parent"
                        -2 -> "wrap_content"
                        else -> "${data}dp"
                    }
                } else if (attrName == "orientation") {
                    if (data == 0) "horizontal" else "vertical"
                } else {
                    data.toString()
                }
            }
            0x12 -> if (data != 0) "true" else "false"
            0x1C, 0x1D, 0x1E, 0x1F -> String.format("#%08X", data)
            0x05 -> {
                val units = arrayOf("px", "dp", "sp", "pt", "in", "mm")
                val unit = units.getOrElse(data and 0x0F) { "dp" }
                val value = (data shr 8).coerceAtLeast(0)
                "$value$unit"
            }
            else -> data.toString()
        }
    }

    /**
     * Pure-Kotlin DEX (`classes*.dex`) bytecode table analyzer.
     * Parses `string_ids`, `type_ids`, `field_ids`, `method_ids`, and `class_defs` from all DEX files inside [apkFile].
     */
    fun analyzeApkDex(
        apkFile: File,
        appPackageHint: String = "",
        filterQuery: String = "",
        maxClasses: Int = 80
    ): DexAnalysisReport {
        var dexCount = 0
        var totalClasses = 0
        var totalMethods = 0
        var totalFields = 0
        val matchedClasses = mutableListOf<DexClassSummary>()
        val urls = linkedSetOf<String>()
        val constants = linkedSetOf<String>()
        val pkgPrefix = appPackageHint.trim()
        val q = filterQuery.trim().lowercase(Locale.US)

        try {
            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                    .sortedBy { it.name }
                    .toList()
                dexCount = dexEntries.size

                for (entry in dexEntries) {
                    val bytes = zip.getInputStream(entry).readBytes()
                    if (bytes.size < 112) continue
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    // Check magic "dex\n"
                    if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() || bytes[2] != 'x'.code.toByte()) continue

                    val stringIdsSize = buf.getInt(56)
                    val stringIdsOff = buf.getInt(60)
                    val typeIdsSize = buf.getInt(64)
                    val typeIdsOff = buf.getInt(68)
                    val fieldIdsSize = buf.getInt(80)
                    val fieldIdsOff = buf.getInt(84)
                    val methodIdsSize = buf.getInt(88)
                    val methodIdsOff = buf.getInt(92)
                    val classDefsSize = buf.getInt(96)
                    val classDefsOff = buf.getInt(100)

                    totalClasses += classDefsSize
                    totalMethods += methodIdsSize
                    totalFields += fieldIdsSize

                    fun readDexString(idx: Int): String {
                        if (idx < 0 || idx >= stringIdsSize) return ""
                        val offPos = stringIdsOff + idx * 4
                        if (offPos < 0 || offPos + 4 > bytes.size) return ""
                        val dataOff = buf.getInt(offPos)
                        if (dataOff <= 0 || dataOff >= bytes.size) return ""
                        var p = dataOff
                        // Skip ULEB128 utf16_size
                        var shift = 0
                        while (p < bytes.size) {
                            val b = bytes[p++].toInt() and 0xFF
                            if ((b and 0x80) == 0 || shift > 28) break
                            shift += 7
                        }
                        val start = p
                        while (p < bytes.size && bytes[p] != 0.toByte() && (p - start) < 512) {
                            p++
                        }
                        return String(bytes, start, p - start, Charsets.UTF_8)
                    }

                    fun descriptorToJavaName(desc: String): String {
                        if (desc.startsWith("L") && desc.endsWith(";")) {
                            return desc.substring(1, desc.length - 1).replace('/', '.')
                        }
                        return desc
                    }

                    fun readTypeName(typeIdx: Int): String {
                        if (typeIdx < 0 || typeIdx >= typeIdsSize) return ""
                        val pos = typeIdsOff + typeIdx * 4
                        if (pos < 0 || pos + 4 > bytes.size) return ""
                        val strIdx = buf.getInt(pos)
                        return descriptorToJavaName(readDexString(strIdx))
                    }

                    // Scan strings for URLs and interesting keys (cap to 25000 strings per dex for speed)
                    val strScanLimit = stringIdsSize.coerceAtMost(25000)
                    for (sIdx in 0 until strScanLimit) {
                        val s = readDexString(sIdx)
                        if (s.length in 6..220) {
                            if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("ws://") || s.startsWith("wss://")) {
                                if (!s.contains("schemas.android.com") && !s.contains("www.w3.org") && !s.contains("apache.org") && !s.contains("google.com/Fp")) {
                                    if (urls.size < 40) urls.add(s)
                                }
                            } else if (
                                s.startsWith("CREATE TABLE", ignoreCase = true) ||
                                s.startsWith("SELECT ", ignoreCase = true) ||
                                s.startsWith("/api/") ||
                                s.startsWith("/v1/") ||
                                s.startsWith("/v2/") ||
                                (q.isNotEmpty() && s.lowercase(Locale.US).contains(q))
                            ) {
                                if (constants.size < 50) constants.add(s)
                            }
                        }
                    }

                    // Group methods & fields by defining class index
                    val methodsByClass = HashMap<Int, MutableList<String>>()
                    val methodScanLimit = methodIdsSize.coerceAtMost(40000)
                    for (mIdx in 0 until methodScanLimit) {
                        val pos = methodIdsOff + mIdx * 8
                        if (pos + 8 > bytes.size) break
                        val classIdx = buf.getShort(pos).toInt() and 0xFFFF
                        val nameIdx = buf.getInt(pos + 4)
                        val mName = readDexString(nameIdx)
                        if (mName != "<init>" && mName != "<clinit>" && !mName.startsWith("access$")) {
                            val list = methodsByClass.getOrPut(classIdx) { mutableListOf() }
                            if (list.size < 25 && mName !in list) list.add(mName)
                        }
                    }

                    val fieldsByClass = HashMap<Int, MutableList<String>>()
                    val fieldScanLimit = fieldIdsSize.coerceAtMost(40000)
                    for (fIdx in 0 until fieldScanLimit) {
                        val pos = fieldIdsOff + fIdx * 8
                        if (pos + 8 > bytes.size) break
                        val classIdx = buf.getShort(pos).toInt() and 0xFFFF
                        val typeIdx = buf.getShort(pos + 2).toInt() and 0xFFFF
                        val nameIdx = buf.getInt(pos + 4)
                        val fName = readDexString(nameIdx)
                        val fType = readTypeName(typeIdx).substringAfterLast('.')
                        val list = fieldsByClass.getOrPut(classIdx) { mutableListOf() }
                        if (list.size < 20) list.add("$fName:$fType")
                    }

                    val classScanLimit = classDefsSize.coerceAtMost(15000)
                    for (cIdx in 0 until classScanLimit) {
                        if (matchedClasses.size >= maxClasses) break
                        val pos = classDefsOff + cIdx * 32
                        if (pos + 32 > bytes.size) break
                        val classTypeIdx = buf.getInt(pos)
                        val superTypeIdx = buf.getInt(pos + 8)
                        val sourceFileIdx = buf.getInt(pos + 16)

                        val className = readTypeName(classTypeIdx)
                        if (className.isEmpty() || className.endsWith(".R") || className.contains(".R$") || className.endsWith(".BuildConfig")) {
                            continue
                        }
                        if (NOISE_CLASS_PREFIXES.any { className.startsWith(it) }) {
                            continue
                        }
                        if (q.isNotEmpty()) {
                            val methods = methodsByClass[classTypeIdx].orEmpty()
                            val fields = fieldsByClass[classTypeIdx].orEmpty()
                            val hit = className.lowercase(Locale.US).contains(q) ||
                                methods.any { it.lowercase(Locale.US).contains(q) } ||
                                fields.any { it.lowercase(Locale.US).contains(q) }
                            if (!hit) continue
                        } else if (pkgPrefix.isNotEmpty() && !className.startsWith(pkgPrefix) && matchedClasses.size >= 25) {
                            // Prioritize classes inside the app's package prefix
                            continue
                        }

                        val superName = if (superTypeIdx != -1) readTypeName(superTypeIdx) else "java.lang.Object"
                        val srcFile = if (sourceFileIdx != -1) readDexString(sourceFileIdx) else null
                        matchedClasses.add(
                            DexClassSummary(
                                className = className,
                                superClass = superName,
                                sourceFile = srcFile,
                                methods = methodsByClass[classTypeIdx].orEmpty(),
                                fields = fieldsByClass[classTypeIdx].orEmpty()
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // Sort so Activities and classes matching appPackageHint come first
        val sortedClasses = matchedClasses.sortedWith(
            compareByDescending<DexClassSummary> {
                pkgPrefix.isNotEmpty() && it.className.startsWith(pkgPrefix)
            }.thenByDescending {
                it.className.endsWith("Activity") || it.superClass.endsWith("Activity")
            }.thenBy { it.className }
        )

        return DexAnalysisReport(
            dexFilesCount = dexCount,
            totalClassesCount = totalClasses,
            totalMethodsCount = totalMethods,
            totalFieldsCount = totalFields,
            appPackagePrefix = pkgPrefix,
            appClasses = sortedClasses.take(maxClasses),
            discoveredUrls = urls.toList(),
            interestingStrings = constants.toList()
        )
    }

    /**
     * Performs a **Full APK-to-Sketchware Project Clone & Reconstruction**:
     * 1. Creates a snapshot checkpoint of the current project (`rea_before_clone`).
     * 2. Extracts & registers app Drawables (`res/drawable`, `res/mipmap`) into Sketchware.
     * 3. Extracts & copies bundled `assets/` and `res/font/`.
     * 4. Syncs Android permissions declared in the APK's manifest.
     * 5. Decompiles binary XML layouts (`res/layout/`), saves raw decoded XMLs into `.sketchware/data/<sc_id>/files/rea_workspace/layouts/`,
     *    converts them into Sketchware-compatible layouts, and creates/populates Activities & CustomViews in Sketchware!
     * 6. Generates a comprehensive **`rea_clone_blueprint.md`** artifact in the **📦 Artifacts** panel so the AI Agent
     *    can immediately continue implementing all `BlockBean` logic and custom Java classes until `save_and_verify_compile` succeeds.
     */
    fun fullCloneApkToProject(
        context: Context,
        scId: String,
        rawApkPath: String,
        updateAppIdentity: Boolean = false,
        maxLayouts: Int = 25,
        maxDrawables: Int = 60,
        onProgress: ((String) -> Unit)? = null
    ): String {
        val apkFile = resolveApkFile(scId, rawApkPath)
        if (!apkFile.isFile) {
            return "Error: File APK tidak ditemukan di '${apkFile.absolutePath}'."
        }

        onProgress?.invoke("Membuat snapshot checkpoint proyek #$scId sebelum rekonstruksi APK...")
        try {
            ProjectGateway.createCheckpoint(scId, "rea_before_apk_clone")
        } catch (_: Throwable) {
        }

        val sdcard = Environment.getExternalStorageDirectory()
        val reaWorkspace = File(sdcard, ".sketchware/data/$scId/files/rea_workspace").apply { mkdirs() }
        val decodedLayoutsDir = File(reaWorkspace, "layouts").apply { mkdirs() }
        val extractedDrawablesDir = File(reaWorkspace, "drawables").apply { mkdirs() }

        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PERMISSIONS
        @Suppress("DEPRECATION")
        val pkgInfo = try {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (_: Throwable) {
            null
        }

        var appLabel = apkFile.nameWithoutExtension
        val pkgName = pkgInfo?.packageName ?: ""
        val verName = pkgInfo?.versionName ?: "1.0"
        val permissions = pkgInfo?.requestedPermissions?.toList().orEmpty()
        val activities = pkgInfo?.activities?.map { it.name }.orEmpty()

        val appInfo = pkgInfo?.applicationInfo
        if (appInfo != null) {
            try {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
                val lbl = pm.getApplicationLabel(appInfo)?.toString()
                if (!lbl.isNullOrBlank()) appLabel = lbl
            } catch (_: Throwable) {
            }
        }

        // 1. Decode AndroidManifest.xml and save to rea_workspace
        onProgress?.invoke("Mendekode AndroidManifest.xml dari ${apkFile.name}...")
        val decodedManifest = decodeApkXmlEntry(context, apkFile, "AndroidManifest.xml")
        File(reaWorkspace, "AndroidManifest.xml").writeText(decodedManifest)

        // 2. Sync Permissions
        val addedPermissions = mutableListOf<String>()
        if (permissions.isNotEmpty()) {
            try {
                ProjectGateway.addPermissions(scId, permissions)
                addedPermissions.addAll(permissions)
            } catch (_: Throwable) {
            }
        }

        // 3. Optionally update App Name
        if (updateAppIdentity && appLabel.isNotBlank()) {
            try {
                ProjectGateway.configureProjectMetadata(
                    context = context,
                    scId = scId,
                    action = "update",
                    appName = appLabel,
                    versionName = verName
                )
            } catch (_: Throwable) {
            }
        }

        // 4. Extract Drawables, Fonts, Assets, and Layout XML entries from the APK ZIP
        val importedDrawables = mutableListOf<String>()
        val importedAssets = mutableListOf<String>()
        val importedFonts = mutableListOf<String>()
        val layoutEntries = mutableListOf<String>()

        onProgress?.invoke("Mengekstrak drawable, asset, dan font dari ${apkFile.name}...")
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries().asSequence().toList()

                // Collect layout entries (prefer default res/layout/ over configuration qualifiers)
                for (e in entries) {
                    if (e.name.startsWith("res/layout/") && e.name.endsWith(".xml")) {
                        val base = e.name.substringAfterLast('/')
                        if (!isFrameworkResourceName(base)) {
                            layoutEntries.add(e.name)
                        }
                    }
                }
                if (layoutEntries.isEmpty()) {
                    for (e in entries) {
                        if (e.name.startsWith("res/layout") && e.name.endsWith(".xml")) {
                            val base = e.name.substringAfterLast('/')
                            if (!isFrameworkResourceName(base) && layoutEntries.none { it.endsWith("/$base") }) {
                                layoutEntries.add(e.name)
                            }
                        }
                    }
                }

                // Extract & import drawables (PNG/JPG)
                val seenDrawableNames = mutableSetOf<String>()
                for (e in entries) {
                    if (importedDrawables.size >= maxDrawables) break
                    if (e.isDirectory) continue
                    val name = e.name
                    if ((name.startsWith("res/drawable") || name.startsWith("res/mipmap")) &&
                        (name.endsWith(".png", true) || name.endsWith(".jpg", true)) &&
                        !name.endsWith(".9.png", true)
                    ) {
                        val fileName = name.substringAfterLast('/')
                        if (isFrameworkResourceName(fileName)) continue
                        val resName = fileName.substringBeforeLast('.')
                            .lowercase(Locale.US)
                            .replace(Regex("[^a-z0-9_]"), "_")
                            .let { if (it.firstOrNull()?.isDigit() == true) "ic_$it" else it }
                        if (resName.length !in 2..45 || !seenDrawableNames.add(resName)) continue

                        val tempImg = File(extractedDrawablesDir, "$resName.png")
                        zip.getInputStream(e).use { input ->
                            tempImg.outputStream().use { out -> input.copyTo(out) }
                        }
                        if (tempImg.length() in 64..(2 * 1024 * 1024)) {
                            try {
                                ProjectGateway.manageProjectResources(
                                    scId = scId,
                                    action = "import",
                                    resourceType = "image",
                                    resourceName = resName,
                                    sourcePath = tempImg.absolutePath
                                )
                                importedDrawables.add(resName)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }

                // Extract & import assets
                for (e in entries) {
                    if (importedAssets.size >= 40) break
                    if (e.isDirectory || !e.name.startsWith("assets/")) continue
                    val rel = e.name.removePrefix("assets/")
                    if (rel.isEmpty() || e.size > 5 * 1024 * 1024) continue
                    val tempAsset = File(reaWorkspace, "assets/$rel")
                    tempAsset.parentFile?.mkdirs()
                    zip.getInputStream(e).use { input ->
                        tempAsset.outputStream().use { out -> input.copyTo(out) }
                    }
                    try {
                        ProjectGateway.manageProjectResources(
                            scId = scId,
                            action = "import",
                            resourceType = "asset",
                            resourceName = rel,
                            sourcePath = tempAsset.absolutePath
                        )
                        importedAssets.add(rel)
                    } catch (_: Throwable) {
                    }
                }

                // Extract & import fonts
                for (e in entries) {
                    if (importedFonts.size >= 10) break
                    if (e.isDirectory) continue
                    if ((e.name.startsWith("res/font/") || e.name.startsWith("assets/fonts/")) &&
                        (e.name.endsWith(".ttf", true) || e.name.endsWith(".otf", true))
                    ) {
                        val fontName = e.name.substringAfterLast('/').substringBeforeLast('.')
                            .lowercase(Locale.US)
                            .replace(Regex("[^a-z0-9_]"), "_")
                            .let { if (it.firstOrNull()?.isDigit() == true) "font_$it" else it }
                        if (fontName.isEmpty()) continue
                        val ext = e.name.substringAfterLast('.', "ttf")
                        val tempFont = File(reaWorkspace, "fonts/$fontName.$ext")
                        tempFont.parentFile?.mkdirs()
                        zip.getInputStream(e).use { input ->
                            tempFont.outputStream().use { out -> input.copyTo(out) }
                        }
                        try {
                            ProjectGateway.manageProjectResources(
                                scId = scId,
                                action = "import",
                                resourceType = "font",
                                resourceName = fontName,
                                sourcePath = tempFont.absolutePath
                            )
                            importedFonts.add(fontName)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 5. Decode and clone layouts into Sketchware screens / custom views
        val clonedScreens = mutableListOf<String>()
        val clonedCustomViews = mutableListOf<String>()
        val layoutMappingNotes = mutableListOf<String>()

        val sortedLayouts = layoutEntries.sortedWith(
            compareByDescending<String> {
                val n = it.substringAfterLast('/')
                n == "activity_main.xml" || n == "main.xml"
            }.thenByDescending {
                it.substringAfterLast('/').startsWith("activity_")
            }.thenBy { it }
        ).take(maxLayouts)

        for ((idx, entryPath) in sortedLayouts.withIndex()) {
            val fileName = entryPath.substringAfterLast('/')
            val baseName = fileName.removeSuffix(".xml").lowercase(Locale.US)
            onProgress?.invoke("Mendekode & merekonstruksi layout (${idx + 1}/${sortedLayouts.size}): $fileName...")

            val rawDecodedXml = decodeApkXmlEntry(context, apkFile, entryPath)
            if (rawDecodedXml.startsWith("Error:")) continue

            // Save original decoded XML into rea_workspace/layouts/
            File(decodedLayoutsDir, fileName).writeText(rawDecodedXml)

            // Determine target screen name in Sketchware
            val isMainCandidate = baseName == "activity_main" || baseName == "main" ||
                (idx == 0 && clonedScreens.isEmpty() && baseName.startsWith("activity_"))
            val isCustomViewCandidate = !isMainCandidate && (
                baseName.startsWith("item_") ||
                    baseName.startsWith("row_") ||
                    baseName.startsWith("cell_") ||
                    baseName.startsWith("dialog_") ||
                    baseName.startsWith("layout_item") ||
                    baseName.startsWith("custom_")
                )

            val targetScreenName = when {
                isMainCandidate -> "main"
                baseName.startsWith("activity_") -> baseName.removePrefix("activity_").ifEmpty { "screen_${idx + 1}" }
                else -> baseName
            }.replace(Regex("[^a-z0-9_]"), "_").trim('_').ifEmpty { "screen_${idx + 1}" }

            val adaptedXml = adaptDecodedXmlForSketchware(rawDecodedXml, importedDrawables.toSet())

            try {
                if (targetScreenName != "main") {
                    ProjectGateway.createScreenOrCustomView(
                        scId = scId,
                        rawName = targetScreenName,
                        isCustomView = isCustomViewCandidate,
                        hasToolbar = false
                    )
                }
                val writeRes = ProjectGateway.writeScreenXml(
                    scId = scId,
                    screenName = targetScreenName,
                    xmlContent = adaptedXml,
                    autoCreateButtonClickEvents = true
                )
                if (isCustomViewCandidate) {
                    clonedCustomViews.add("$targetScreenName.xml (dari $fileName)")
                } else {
                    clonedScreens.add("$targetScreenName.xml (dari $fileName)")
                }
                layoutMappingNotes.add("- `$fileName` ➔ **`$targetScreenName.xml`** (${if (isCustomViewCandidate) "CustomView" else "Activity"}): $writeRes")
            } catch (e: Throwable) {
                layoutMappingNotes.add("- `$fileName` ➔ Disimpan di `rea_workspace/layouts/$fileName` (catatan adaptasi: ${e.message})")
            }
        }

        // 6. Run DEX analysis and save blueprint to Artifacts
        onProgress?.invoke("Menganalisis DEX classes, methods, dan konstanta untuk menyusun REA Blueprint...")
        val dexReport = analyzeApkDex(apkFile, pkgName, filterQuery = "", maxClasses = 70)

        val blueprintContent = buildString {
            appendLine("# 🧬 REA Full APK Clone Blueprint: $appLabel")
            appendLine()
            appendLine("- **Source APK**: `${apkFile.absolutePath}` (${apkFile.length() / 1024} KB)")
            appendLine("- **Original Package**: `${pkgName.ifEmpty { "unknown" }}` | **Version**: `$verName`")
            appendLine("- **Target Sketchware Project**: `#$scId`")
            appendLine("- **Raw Decoded Layouts Dir**: `${decodedLayoutsDir.absolutePath}`")
            appendLine()
            appendLine("## 1. Cloned Screens & Custom Views (${clonedScreens.size} Activities, ${clonedCustomViews.size} CustomViews)")
            if (layoutMappingNotes.isEmpty()) {
                appendLine("- (Tidak ada file XML standar di `res/layout/` — aplikasi mungkin berbasis Compose/Flutter/WebView; periksa DEX & Assets di bawah)")
            } else {
                layoutMappingNotes.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("## 2. Imported Resources")
            appendLine("- **Drawables (${importedDrawables.size})**: ${importedDrawables.joinToString(", ").ifEmpty { "(none)" }}")
            appendLine("- **Fonts (${importedFonts.size})**: ${importedFonts.joinToString(", ").ifEmpty { "(none)" }}")
            appendLine("- **Assets (${importedAssets.size})**: ${importedAssets.joinToString(", ").ifEmpty { "(none)" }}")
            appendLine("- **Permissions (${addedPermissions.size})**: ${addedPermissions.joinToString(", ").ifEmpty { "(none)" }}")
            appendLine()
            appendLine("## 3. Original APK Activities (${activities.size})")
            activities.forEachIndexed { i, act -> appendLine("${i + 1}. `$act`") }
            appendLine()
            appendLine("## 4. Decompiled DEX Classes & Methods (${dexReport.appClasses.size} App Classes)")
            for (cls in dexReport.appClasses.take(45)) {
                appendLine("### `${cls.className}` (extends `${cls.superClass.substringAfterLast('.')}`)")
                if (cls.fields.isNotEmpty()) {
                    appendLine("- **Fields**: `${cls.fields.joinToString("`, `")}`")
                }
                if (cls.methods.isNotEmpty()) {
                    appendLine("- **Methods**: `${cls.methods.joinToString("`, `")}`")
                }
            }
            if (dexReport.discoveredUrls.isNotEmpty()) {
                appendLine()
                appendLine("## 5. Discovered API Endpoints & URLs")
                dexReport.discoveredUrls.forEach { appendLine("- `$it`") }
            }
            if (dexReport.interestingStrings.isNotEmpty()) {
                appendLine()
                appendLine("## 6. Discovered SQL / Routes / Constants")
                dexReport.interestingStrings.forEach { appendLine("- `$it`") }
            }
            appendLine()
            appendLine("## 7. Mandatory Next Steps for AI Agent (Autonomous Completion)")
            appendLine("1. Periksa setiap layar yang telah di-clone (`get_screen_state` / `read_screen_xml`) dan rapikan tata letak serta warna/styling (`GradientDrawable`) di `onCreate_initializeLogic`.")
            appendLine("2. Rekonstruksi penuh logika bisnis setiap Activity (`write_event_blocks` / `manage_custom_java`) berdasarkan daftar Method, Field, dan URL dari analisis DEX di atas.")
            appendLine("3. Hubungkan navigasi antar layar (`Intent`), komponen (`SharedPreferences`, `RequestNetwork`, `Dialog`), serta `ListView`/`RecyclerView` adapter.")
            appendLine("4. Jalankan `save_and_verify_compile(full_apk_build=true)` dan lakukan self-healing hingga **BUILD SUKSES**!")
        }.trim()

        val blueprintFile = File(WorkspaceArtifactsManager.artifactsDir(scId), "rea_clone_blueprint.md")
        blueprintFile.writeText(blueprintContent)
        File(reaWorkspace, "rea_clone_blueprint.md").writeText(blueprintContent)

        try {
            ProjectGateway.saveProjectToDisk(context.applicationContext, scId)
            ProjectGateway.notifyUiChanged(scId, "main.xml")
        } catch (_: Throwable) {
        }

        return buildString {
            appendLine("=== ✅ REA FULL APK CLONE & RECONSTRUCTION SELESAI ===")
            appendLine("Target Proyek     : #$scId")
            appendLine("Sumber APK        : ${apkFile.name} (App: $appLabel, Package: $pkgName)")
            appendLine("Layar Activity    : ${clonedScreens.size} di-clone (${clonedScreens.joinToString(", ").ifEmpty { "main.xml" }})")
            appendLine("Custom Views      : ${clonedCustomViews.size} di-clone (${clonedCustomViews.joinToString(", ").ifEmpty { "tidak ada" }})")
            appendLine("Drawables Diimpor : ${importedDrawables.size} (${importedDrawables.take(20).joinToString(", ")})")
            appendLine("Assets & Fonts    : ${importedAssets.size} assets, ${importedFonts.size} fonts")
            appendLine("Permissions       : ${addedPermissions.size} disinkronkan")
            appendLine("Blueprint Artifact: ${blueprintFile.absolutePath}")
            appendLine()
            appendLine("--- RINGKASAN PEMETAAN LAYOUT ---")
            layoutMappingNotes.forEach { appendLine(it) }
            appendLine()
            appendLine("--- RINGKASAN KELAS DEX UTAMA (${dexReport.appClasses.size} kelas) ---")
            for (cls in dexReport.appClasses.take(20)) {
                appendLine("• ${cls.className} -> methods=[${cls.methods.take(6).joinToString(", ")}] fields=[${cls.fields.take(4).joinToString(", ")}]")
            }
            if (dexReport.discoveredUrls.isNotEmpty()) {
                appendLine("• URLs Terdeteksi: ${dexReport.discoveredUrls.take(10).joinToString(", ")}")
            }
            appendLine()
            appendLine("🚀 INSTRUKSI LANJUTAN UNTUK AI AGENT:")
            appendLine("Seluruh kerangka layar XML, drawable, asset, dan permission dari APK telah masuk ke proyek #$scId.")
            appendLine("Sekarang LANJUTKAN secara otonom (tanpa berhenti) untuk mengisi logika `write_event_blocks`, `manage_custom_java`, dan `add_component` di setiap layar sesuai Blueprint REA, lalu verifikasi dengan `save_and_verify_compile` hingga BUILD SUKSES!")
        }.trim()
    }

    /**
     * Converts arbitrary Android layout XML (which may contain ConstraintLayout, RelativeLayout,
     * CoordinatorLayout, MaterialCardView, AppCompatTextView, etc.) into clean, valid XML that
     * Sketchware Pro's `ViewBeanParser` parses without errors while preserving widget IDs, texts,
     * hints, colors, paddings, and imported drawables.
     */
    internal fun adaptDecodedXmlForSketchware(
        rawXml: String,
        availableDrawables: Set<String>
    ): String {
        val tagReplacements = mapOf(
            "androidx.constraintlayout.widget.ConstraintLayout" to "LinearLayout",
            "android.support.constraint.ConstraintLayout" to "LinearLayout",
            "androidx.coordinatorlayout.widget.CoordinatorLayout" to "LinearLayout",
            "android.support.design.widget.CoordinatorLayout" to "LinearLayout",
            "RelativeLayout" to "LinearLayout",
            "FrameLayout" to "LinearLayout",
            "androidx.appcompat.widget.LinearLayoutCompat" to "LinearLayout",
            "com.google.android.material.card.MaterialCardView" to "LinearLayout",
            "androidx.cardview.widget.CardView" to "LinearLayout",
            "android.support.v7.widget.CardView" to "LinearLayout",
            "com.google.android.material.appbar.AppBarLayout" to "LinearLayout",
            "androidx.appcompat.widget.Toolbar" to "LinearLayout",
            "com.google.android.material.appbar.MaterialToolbar" to "LinearLayout",
            "androidx.core.widget.NestedScrollView" to "ScrollView",
            "android.support.v4.widget.NestedScrollView" to "ScrollView",
            "androidx.appcompat.widget.AppCompatTextView" to "TextView",
            "com.google.android.material.textview.MaterialTextView" to "TextView",
            "androidx.appcompat.widget.AppCompatButton" to "Button",
            "com.google.android.material.button.MaterialButton" to "Button",
            "androidx.appcompat.widget.AppCompatEditText" to "EditText",
            "com.google.android.material.textfield.TextInputEditText" to "EditText",
            "com.google.android.material.textfield.TextInputLayout" to "LinearLayout",
            "androidx.appcompat.widget.AppCompatImageView" to "ImageView",
            "androidx.appcompat.widget.AppCompatImageButton" to "ImageView",
            "ImageButton" to "ImageView",
            "com.google.android.material.imageview.ShapeableImageView" to "ImageView",
            "de.hdodenhof.circleimageview.CircleImageView" to "ImageView",
            "androidx.appcompat.widget.SwitchCompat" to "Switch",
            "com.google.android.material.switchmaterial.SwitchMaterial" to "Switch",
            "com.google.android.material.materialswitch.MaterialSwitch" to "Switch",
            "androidx.appcompat.widget.AppCompatCheckBox" to "CheckBox",
            "com.google.android.material.checkbox.MaterialCheckBox" to "CheckBox",
            "androidx.appcompat.widget.AppCompatSpinner" to "Spinner",
            "androidx.recyclerview.widget.RecyclerView" to "ListView",
            "android.support.v7.widget.RecyclerView" to "ListView",
            "View" to "LinearLayout",
            "Space" to "LinearLayout",
            "include" to "LinearLayout",
            "merge" to "LinearLayout",
            "fragment" to "LinearLayout",
            "androidx.fragment.app.FragmentContainerView" to "LinearLayout"
        )

        val supportedTags = setOf(
            "LinearLayout", "ScrollView", "HorizontalScrollView",
            "TextView", "Button", "EditText", "ImageView",
            "ListView", "Spinner", "CheckBox", "Switch",
            "SeekBar", "ProgressBar", "WebView", "CalendarView", "RadioButton"
        )

        var xml = rawXml
        for ((from, to) in tagReplacements) {
            xml = xml.replace("<$from", "<$to")
            xml = xml.replace("</$from>", "</$to>")
        }

        // Replace any remaining custom/unknown package tags (e.g. <com.example.CustomWidget ...> / </com.example.CustomWidget>)
        xml = Regex("<(/?)([a-zA-Z0-9_]+\\.[a-zA-Z0-9_.]+)").replace(xml) { m ->
            val slash = m.groupValues[1]
            val fqcn = m.groupValues[2]
            val simple = fqcn.substringAfterLast('.')
            val mapped = when {
                simple.contains("Text", ignoreCase = true) -> "TextView"
                simple.contains("Button", ignoreCase = true) -> "Button"
                simple.contains("Edit", ignoreCase = true) -> "EditText"
                simple.contains("Image", ignoreCase = true) || simple.contains("Icon", ignoreCase = true) -> "ImageView"
                simple.contains("List", ignoreCase = true) || simple.contains("Recycler", ignoreCase = true) -> "ListView"
                simple.contains("Scroll", ignoreCase = true) -> "ScrollView"
                simple.contains("Progress", ignoreCase = true) -> "ProgressBar"
                simple.contains("Web", ignoreCase = true) -> "WebView"
                simple in supportedTags -> simple
                else -> "LinearLayout"
            }
            "<$slash$mapped"
        }

        // Clean up attributes that reference missing @style/, @color/, @dimen/, or unimported @drawable/
        var autoIdCounter = 1
        val cleanedLines = mutableListOf<String>()
        for (line in xml.lines()) {
            val trimmed = line.trim()
            val suffix = when {
                trimmed.endsWith("/>") -> " />"
                trimmed.endsWith(">") && !trimmed.startsWith("<") -> ">"
                else -> ""
            }
            // Drop attributes that ViewBeanParser cannot resolve
            if (trimmed.startsWith("android:theme=") ||
                trimmed.startsWith("style=") ||
                trimmed.startsWith("android:style=") ||
                trimmed.startsWith("app:layout_constraint") ||
                trimmed.startsWith("android:layout_align") ||
                trimmed.startsWith("android:layout_below") ||
                trimmed.startsWith("android:layout_above") ||
                trimmed.startsWith("android:layout_toEndOf") ||
                trimmed.startsWith("android:layout_toStartOf") ||
                trimmed.startsWith("android:layout_toRightOf") ||
                trimmed.startsWith("android:layout_toLeftOf") ||
                trimmed.startsWith("app:")
            ) {
                if (suffix.isNotEmpty()) cleanedLines.add("    $suffix")
                continue
            }

            var modifiedLine = line
            // Replace unresolved @color/... or ?attr/... with safe hex colors
            if (modifiedLine.contains("=\"@color/") || modifiedLine.contains("=\"?") || modifiedLine.contains("=\"@0x")) {
                modifiedLine = when {
                    modifiedLine.contains("textColor") -> "        android:textColor=\"#1E293B\"$suffix"
                    modifiedLine.contains("background") -> "        android:background=\"#FFFFFF\"$suffix"
                    else -> {
                        if (suffix.isNotEmpty()) cleanedLines.add("    $suffix")
                        continue
                    }
                }
            }
            // Replace @dimen/... with 12dp / 14sp
            if (modifiedLine.contains("=\"@dimen/")) {
                modifiedLine = if (modifiedLine.contains("textSize")) {
                    "        android:textSize=\"14sp\"$suffix"
                } else {
                    modifiedLine.replace(Regex("\"@dimen/[^\"]+\""), "\"12dp\"")
                }
            }
            // Check @drawable/ references against importedDrawables
            val drawMatch = Regex("\"@(drawable|mipmap)/([a-zA-Z0-9_]+)\"").find(modifiedLine)
            if (drawMatch != null) {
                val dName = drawMatch.groupValues[2].lowercase(Locale.US)
                if (dName !in availableDrawables) {
                    if (modifiedLine.contains("android:src=")) {
                        modifiedLine = "        android:src=\"@drawable/default_image\"$suffix"
                    } else if (modifiedLine.contains("android:background=")) {
                        if (suffix.isNotEmpty()) cleanedLines.add("    $suffix")
                        continue
                    }
                } else {
                    modifiedLine = modifiedLine.replace("@mipmap/$dName", "@drawable/$dName")
                }
            }

            // Ensure every opening widget tag has an android:id if missing
            val openTagMatch = Regex("^(\\s*)<([A-Za-z0-9_]+)$").find(modifiedLine)
            if (openTagMatch != null && autoIdCounter <= 200) {
                val tag = openTagMatch.groupValues[2]
                val prefix = when (tag) {
                    "LinearLayout" -> "linear"
                    "TextView" -> "textview"
                    "Button" -> "button"
                    "EditText" -> "edittext"
                    "ImageView" -> "imageview"
                    "ListView" -> "listview"
                    "ScrollView" -> "vscroll"
                    "HorizontalScrollView" -> "hscroll"
                    "ProgressBar" -> "progressbar"
                    "CheckBox" -> "checkbox"
                    "Switch" -> "switch"
                    "WebView" -> "webview"
                    else -> "view"
                }
                cleanedLines.add(modifiedLine)
                // We only inject a fallback id if the element doesn't declare one in its attributes;
                // handled in post-pass below.
                autoIdCounter++
                continue
            }

            cleanedLines.add(modifiedLine)
        }

        return ensureValidIdsInXml(cleanedLines.joinToString("\n"))
    }

    private fun ensureValidIdsInXml(xml: String): String {
        var counter = 1
        val usedIds = mutableSetOf<String>()
        return Regex("<([A-Za-z0-9_]+)([^>]*?)>").replace(xml) { match ->
            val tag = match.groupValues[1]
            val rawAttrs = match.groupValues[2]
            val isSelfClosing = rawAttrs.trimEnd().endsWith("/")
            var attrs = if (isSelfClosing) rawAttrs.trimEnd().removeSuffix("/") else rawAttrs
            val idRegex = Regex("android:id=\"@\\+?id/([a-zA-Z0-9_]+)\"")
            val existing = idRegex.find(attrs)
            if (existing != null) {
                val rawId = existing.groupValues[1].lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_")
                val uniqueId = if (usedIds.add(rawId)) rawId else "${rawId}_${counter++}"
                usedIds.add(uniqueId)
                attrs = attrs.replaceRange(existing.range, "android:id=\"@+id/$uniqueId\"")
            } else if (!attrs.contains("xmlns:android")) {
                val prefix = when (tag) {
                    "LinearLayout" -> "linear"
                    "TextView" -> "textview"
                    "Button" -> "button"
                    "EditText" -> "edittext"
                    "ImageView" -> "imageview"
                    "ListView" -> "listview"
                    "ScrollView" -> "vscroll"
                    else -> "view"
                }
                var candidate = "${prefix}${counter++}"
                while (!usedIds.add(candidate)) {
                    candidate = "${prefix}${counter++}"
                }
                attrs = "\n        android:id=\"@+id/$candidate\"$attrs"
            }
            if (tag == "LinearLayout" && !attrs.contains("android:orientation=")) {
                attrs += "\n        android:orientation=\"vertical\""
            }
            if (!attrs.contains("android:layout_width=")) {
                attrs += "\n        android:layout_width=\"match_parent\""
            }
            if (!attrs.contains("android:layout_height=")) {
                attrs += "\n        android:layout_height=\"wrap_content\""
            }
            if (isSelfClosing) "<$tag$attrs />" else "<$tag$attrs>"
        }
    }

    private fun escapeXmlAttr(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
