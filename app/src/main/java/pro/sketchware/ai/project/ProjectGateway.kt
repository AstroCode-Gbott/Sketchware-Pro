package pro.sketchware.ai.project

import a.a.a.GB
import a.a.a.Ox
import a.a.a.bC
import a.a.a.cC
import a.a.a.eC
import a.a.a.jC
import a.a.a.lC
import a.a.a.nB
import a.a.a.oB
import a.a.a.wq
import a.a.a.yB
import a.a.a.yq
import android.content.Context
import android.graphics.Color
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.besome.sketch.beans.BlockBean
import com.besome.sketch.beans.ComponentBean
import com.besome.sketch.beans.EventBean
import com.besome.sketch.beans.HistoryViewBean
import com.besome.sketch.beans.ProjectFileBean
import com.besome.sketch.beans.ProjectResourceBean
import com.besome.sketch.beans.ViewBean
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.moreblock.ReturnMoreblockManager
import mod.hey.studios.project.ProjectSettings
import mod.hey.studios.project.ProjectTracker
import mod.hey.studios.util.Helper
import mod.jbk.build.BuiltInLibraries
import mod.pranav.dependency.resolver.DependencyResolver
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.ai.agent.AgentMemoryStore
import pro.sketchware.ai.ir.SketchIrCompiler
import pro.sketchware.managers.inject.InjectRootLayoutManager
import pro.sketchware.tools.ViewBeanParser
import pro.sketchware.utility.FilePathUtil
import pro.sketchware.utility.FileResConfig
import pro.sketchware.utility.FileUtil
import pro.sketchware.utility.relativelayout.CircularDependencyDetector
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

object ProjectGateway {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val lock = Any()

    interface UiBridge {
        fun onProjectDataChanged(scId: String, activeXmlOrJava: String?)
        fun onSwitchToScreen(scId: String, projectFileBean: ProjectFileBean)
    }

    interface ProjectsListBridge {
        fun onProjectCreated(scId: String)
    }

    @Volatile
    var uiBridge: UiBridge? = null

    @Volatile
    var projectsListBridge: ProjectsListBridge? = null

    @Volatile
    var activeScId: String? = null

    fun notifyUiChanged(scId: String, activeXmlOrJava: String? = null) {
        activeScId = scId
        val bridge = uiBridge ?: return
        mainHandler.post {
            try {
                bridge.onProjectDataChanged(scId, activeXmlOrJava)
            } catch (_: Throwable) {
            }
        }
    }

    fun listAllProjectsJson(): String {
        synchronized(lock) {
            val rawList = lC.a() ?: ArrayList()
            val items = rawList.mapNotNull { map ->
                val scId = yB.c(map, "sc_id")
                if (scId.isNullOrBlank()) null
                else mapOf(
                    "sc_id" to scId,
                    "app_name" to yB.c(map, "my_app_name"),
                    "project_name" to yB.c(map, "my_ws_name"),
                    "package_name" to yB.c(map, "my_sc_pkg_name"),
                    "version_name" to yB.c(map, "sc_ver_name"),
                    "version_code" to yB.c(map, "sc_ver_code")
                )
            }.sortedByDescending { it["sc_id"]?.toIntOrNull() ?: 0 }

            return gson.toJson(
                mapOf(
                    "total_projects" to items.size,
                    "active_sc_id" to (activeScId ?: items.firstOrNull()?.get("sc_id")),
                    "projects" to items
                )
            )
        }
    }

    fun resolveDefaultScId(): String? {
        val current = activeScId
        if (!current.isNullOrBlank()) return current
        val rawList = lC.a() ?: return null
        return rawList.mapNotNull { yB.c(it, "sc_id") }
            .maxByOrNull { it.toIntOrNull() ?: 0 }
    }

    fun openProjectInUi(context: Context, scId: String, xmlName: String? = null): String {
        ensureProjectLoaded(scId)
        activeScId = scId
        mainHandler.post {
            try {
                val currentDesignScId = com.besome.sketch.design.DesignActivity.sc_id
                val bridge = uiBridge
                if (bridge != null && currentDesignScId == scId) {
                    if (!xmlName.isNullOrBlank()) {
                        val normalizedXml = if (xmlName.endsWith(".xml")) xmlName else "$xmlName.xml"
                        val fileMgr = jC.b(scId)
                        val targetBean = fileMgr.b()?.firstOrNull { it.fileName == normalizedXml }
                            ?: fileMgr.c()?.firstOrNull { it.fileName == normalizedXml }
                        if (targetBean != null) {
                            bridge.onSwitchToScreen(scId, targetBean)
                        }
                    }
                    bridge.onProjectDataChanged(scId, xmlName)
                } else {
                    val intent = android.content.Intent(
                        context.applicationContext,
                        com.besome.sketch.design.DesignActivity::class.java
                    ).apply {
                        putExtra("sc_id", scId)
                        addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    context.applicationContext.startActivity(intent)
                }
            } catch (_: Throwable) {
            }
        }
        return "Proyek sc_id=$scId dibuka di layar editor Sketchware Pro."
    }

    /**
     * Ensures that `jC.a/b/c/d(scId)` are initialized in memory for the given `scId`.
     */
    fun ensureProjectLoaded(scId: String) {
        synchronized(lock) {
            val fileMgr = jC.b(scId)
            val dataMgr = jC.a(scId)
            if (fileMgr == null || dataMgr == null || fileMgr.b() == null || fileMgr.b().isEmpty()) {
                jC.a(scId, false)
                jC.b(scId, false)
                val resMgr = jC.d(scId, false)
                jC.c(scId, false)
                cC.c(scId)
                bC.d(scId)
                resMgr.f()
                resMgr.g()
                resMgr.e()
            }
        }
    }

    /**
     * Creates a brand-new Sketchware project from 0 and initializes all required files and metadata.
     * Returns the new `sc_id` (e.g. `"605"`).
     */
    fun createProjectFromZero(
        context: Context,
        appName: String,
        packageName: String,
        projectName: String = appName,
        versionCode: String = "1",
        versionName: String = "1.0",
        colorPrimaryHex: String = "#FF2196F3",
        colorPrimaryDarkHex: String = "#FF1976D2",
        colorAccentHex: String = "#FFFF4081"
    ): String {
        synchronized(lock) {
            val appCtx = context.applicationContext
            val scId = lC.b()
            val data = HashMap<String, Any>()
            data["sc_id"] = scId
            data["my_sc_pkg_name"] = packageName.trim()
            data["my_ws_name"] = projectName.trim()
            data["my_app_name"] = appName.trim()
            data["my_sc_reg_dt"] = nB().a("yyyyMMddHHmmss")
            data["custom_icon"] = false
            data["isIconAdaptive"] = false
            data["sc_ver_code"] = versionCode
            data["sc_ver_name"] = versionName
            data["sketchware_ver"] = GB.d(appCtx)

            val colorAccent = parseColorOrDefault(colorAccentHex, Color.parseColor("#FF4081"))
            val colorPrimary = parseColorOrDefault(colorPrimaryHex, Color.parseColor("#2196F3"))
            val colorPrimaryDark = parseColorOrDefault(colorPrimaryDarkHex, Color.parseColor("#1976D2"))
            val colorControlHighlight = parseColorOrDefault("#20008DCD", Color.parseColor("#20008DCD"))
            val colorControlNormal = parseColorOrDefault("#FF57BEEE", Color.parseColor("#57BEEE"))

            data["color_accent"] = colorAccent
            data["color_primary"] = colorPrimary
            data["color_primary_dark"] = colorPrimaryDark
            data["color_control_highlight"] = colorControlHighlight
            data["color_control_normal"] = colorControlNormal

            lC.a(scId, data)
            wq.a(appCtx, scId)
            oB().b(wq.b(scId))

            val projectSettings = ProjectSettings(scId)
            projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE)
            projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)

            ProjectTracker.setScId(scId)
            jC.a(scId, false)
            jC.b(scId, false)
            val resMgr = jC.d(scId, false)
            jC.c(scId, false)
            cC.c(scId)
            bC.d(scId)
            resMgr.f()
            resMgr.g()
            resMgr.e()

            saveProjectToDisk(context, scId)
            activeScId = scId
            val listBridge = projectsListBridge
            if (listBridge != null) {
                mainHandler.post {
                    try {
                        listBridge.onProjectCreated(scId)
                    } catch (_: Throwable) {
                    }
                }
            }
            return scId
        }
    }

    /**
     * Commits all in-memory project state (`jC.a`, `jC.b`, `jC.c`, `jC.d`) to `.sketchware/data/<scId>/`.
     */
    fun saveProjectToDisk(context: Context, scId: String) {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val dataMgr = jC.a(scId)
            val fileMgr = jC.b(scId)
            val libMgr = jC.c(scId)
            val resMgr = jC.d(scId)

            resMgr.a()
            fileMgr.l()
            fileMgr.m()
            fileMgr.j()
            dataMgr.a(fileMgr)
            dataMgr.k()
            dataMgr.j()
            resMgr.x()
            libMgr.l()
            resMgr.h()

            val projectMetadata = lC.b(scId)
            if (projectMetadata != null) {
                projectMetadata["sketchware_ver"] = GB.d(context.applicationContext)
                lC.b(scId, projectMetadata)
            }
            resMgr.f()
            resMgr.g()
            resMgr.e()
        }
    }

    data class CheckpointInfo(
        val id: String,
        val timestamp: String,
        val label: String,
        val filesCount: Int,
        val totalBytes: Long,
        val dir: File
    )

    private val CHECKPOINT_DATA_FILES = listOf(
        "file", "view", "logic", "library", "resource",
        "local_library", "permission", "import", "java", "broadcast", "service"
    )

    /**
     * Creates a lightweight snapshot of the project's data and metadata files under `files/agent/<scId>/checkpoints/`.
     */
    fun createCheckpoint(scId: String, label: String): String {
        synchronized(lock) {
            val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36).ifEmpty { "snapshot" }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val checkpointsRoot = File(AgentMemoryStore.getAgentDir(scId), "checkpoints").apply { mkdirs() }
            val checkpointDir = File(checkpointsRoot, "${timestamp}_$safeLabel").apply { mkdirs() }

            val dataDir = File(wq.b(scId))
            if (dataDir.exists()) {
                for (name in CHECKPOINT_DATA_FILES) {
                    val src = File(dataDir, name)
                    if (src.exists() && src.isFile) {
                        src.copyTo(File(checkpointDir, name), overwrite = true)
                    }
                }
            }
            val projectMetaFile = File(wq.c(scId), "project")
            if (projectMetaFile.exists() && projectMetaFile.isFile) {
                projectMetaFile.copyTo(File(checkpointDir, "project_meta"), overwrite = true)
            }

            // Keep at most 40 checkpoints per project so storage stays bounded
            try {
                checkpointsRoot.listFiles()
                    ?.filter { it.isDirectory }
                    ?.sortedByDescending { it.name }
                    ?.drop(40)
                    ?.forEach { it.deleteRecursively() }
            } catch (_: Throwable) {
            }

            return checkpointDir.absolutePath
        }
    }

    fun listCheckpoints(scId: String): List<CheckpointInfo> {
        synchronized(lock) {
            val checkpointsRoot = File(AgentMemoryStore.getAgentDir(scId), "checkpoints")
            if (!checkpointsRoot.isDirectory) return emptyList()
            return checkpointsRoot.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.map { dir ->
                    val parts = dir.name.split("_", limit = 3)
                    val ts = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else dir.name
                    val lbl = if (parts.size >= 3) parts[2] else dir.name
                    val files = dir.listFiles().orEmpty()
                    CheckpointInfo(
                        id = dir.name,
                        timestamp = ts,
                        label = lbl,
                        filesCount = files.size,
                        totalBytes = files.sumOf { it.length() },
                        dir = dir
                    )
                }
                .orEmpty()
        }
    }

    fun restoreCheckpoint(scId: String, checkpointIdOrQuery: String): String {
        synchronized(lock) {
            val all = listCheckpoints(scId)
            if (all.isEmpty()) {
                return "Error: Belum ada checkpoint untuk proyek sc_id=$scId."
            }
            val q = checkpointIdOrQuery.trim()
            val target = when {
                q.isEmpty() || q.equals("latest", ignoreCase = true) || q.equals("last", ignoreCase = true) -> all.first()
                else -> all.firstOrNull { it.id.equals(q, ignoreCase = true) }
                    ?: all.firstOrNull { it.id.contains(q, ignoreCase = true) || it.label.contains(q, ignoreCase = true) }
            } ?: return "Error: Checkpoint '$checkpointIdOrQuery' tidak ditemukan. Gunakan action='list' untuk melihat daftar ID checkpoint."

            val dataDir = File(wq.b(scId)).apply { mkdirs() }
            var restoredFiles = 0
            for (name in CHECKPOINT_DATA_FILES) {
                val src = File(target.dir, name)
                if (src.isFile) {
                    src.copyTo(File(dataDir, name), overwrite = true)
                    restoredFiles++
                }
            }
            val metaSrc = File(target.dir, "project_meta")
            if (metaSrc.isFile) {
                val metaDest = File(wq.c(scId), "project")
                metaDest.parentFile?.mkdirs()
                metaSrc.copyTo(metaDest, overwrite = true)
                restoredFiles++
            }

            // Reload Sketchware in-memory managers from restored disk state
            jC.a()
            jC.a(scId, false)
            jC.b(scId, false)
            val resMgr = jC.d(scId, false)
            jC.c(scId, false)
            cC.c(scId)
            bC.d(scId)
            resMgr.f()
            resMgr.g()
            resMgr.e()
            notifyUiChanged(scId, null)

            return "Berhasil me-restore proyek #$scId ke checkpoint '${target.id}' ($restoredFiles berkas dipulihkan & state UI disegarkan)."
        }
    }

    fun manageCheckpoints(
        scId: String,
        action: String,
        checkpointId: String = "",
        label: String = "manual"
    ): String {
        return when (action.trim().lowercase(Locale.US)) {
            "list", "ls" -> {
                val list = listCheckpoints(scId)
                if (list.isEmpty()) {
                    "Belum ada checkpoint untuk proyek #$scId."
                } else {
                    buildString {
                        appendLine("=== CHECKPOINTS PROYEK #$scId (${list.size}) ===")
                        for ((idx, cp) in list.withIndex()) {
                            appendLine("${idx + 1}. ${cp.id} | label=${cp.label} | ${cp.filesCount} files (${cp.totalBytes / 1024} KB)")
                        }
                        appendLine("\nGunakan manage_checkpoints(action=\"restore\", checkpoint_id=\"<id atau 'latest'>\") untuk rollback.")
                    }.trim()
                }
            }
            "create", "save", "snapshot" -> {
                val path = createCheckpoint(scId, label.ifBlank { "manual" })
                "Checkpoint berhasil dibuat di: $path"
            }
            "restore", "rollback" -> {
                restoreCheckpoint(scId, checkpointId.ifBlank { "latest" })
            }
            "delete", "remove" -> {
                val list = listCheckpoints(scId)
                val target = list.firstOrNull { it.id.equals(checkpointId.trim(), ignoreCase = true) }
                    ?: return "Error: Checkpoint '$checkpointId' tidak ditemukan."
                target.dir.deleteRecursively()
                "Checkpoint '${target.id}' berhasil dihapus."
            }
            else -> "Error: action '$action' tidak dikenal. Gunakan: list, create, restore, atau delete."
        }
    }

    fun getProjectSummary(scId: String): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val info = lC.b(scId) ?: HashMap()
            val fileMgr = jC.b(scId)
            val dataMgr = jC.a(scId)
            val libMgr = jC.c(scId)
            val frc = FileResConfig(scId)

            val sb = StringBuilder()
            sb.appendLine("=== SKETCHWARE PROJECT SUMMARY (sc_id=$scId) ===")
            sb.appendLine("App Name     : ${yB.c(info, "my_app_name")}")
            sb.appendLine("Project Name : ${yB.c(info, "my_ws_name")}")
            sb.appendLine("Package Name : ${yB.c(info, "my_sc_pkg_name")}")
            sb.appendLine("Version      : ${yB.c(info, "sc_ver_name")} (${yB.c(info, "sc_ver_code")})")

            val libs = listOf(
                "AppCompat/Design" to (libMgr.c()?.useYn == "Y"),
                "Firebase" to (libMgr.b()?.useYn == "Y"),
                "AdMob" to (libMgr.d()?.useYn == "Y"),
                "GoogleMap" to (libMgr.e()?.useYn == "Y")
            )
            sb.appendLine("Libraries    : ${libs.joinToString(", ") { "${it.first}=${if (it.second) "ON" else "OFF"}" }}")

            val perms = frc.permissionList
            sb.appendLine("Permissions  : ${if (perms.isEmpty()) "(none)" else perms.joinToString(", ")}")

            val activities = fileMgr.b().orEmpty()
            sb.appendLine("\n--- ACTIVITIES / SCREENS (${activities.size}) ---")
            for (act in activities) {
                val xmlName = act.xmlName
                val javaName = act.javaName
                val views = dataMgr.d(xmlName).orEmpty()
                val vars = dataMgr.k(javaName).orEmpty()
                val lists = dataMgr.j(javaName).orEmpty()
                val comps = dataMgr.e(javaName).orEmpty()
                val events = dataMgr.g(javaName).orEmpty()
                val moreBlocks = dataMgr.i(javaName).orEmpty()
                sb.appendLine(
                    "- $javaName ($xmlName): ${views.size} views, ${vars.size} vars, ${lists.size} lists, " +
                        "${comps.size} components, ${events.size} events, ${moreBlocks.size} moreBlocks " +
                        "[toolbar=${act.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR)}, " +
                        "fab=${act.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FAB)}, " +
                        "drawer=${act.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)}]"
                )
            }

            val customViews = fileMgr.c().orEmpty()
            if (customViews.isNotEmpty()) {
                sb.appendLine("\n--- CUSTOM VIEWS & DRAWERS (${customViews.size}) ---")
                for (cv in customViews) {
                    val views = dataMgr.d(cv.xmlName).orEmpty()
                    sb.appendLine("- ${cv.xmlName} (fileName=${cv.fileName}, type=${cv.fileType}): ${views.size} views")
                }
            }

            val customJava = frc.javaFile
            if (customJava.isNotEmpty()) {
                sb.appendLine("\n--- CUSTOM JAVA/KOTLIN FILES (${customJava.size}) ---")
                customJava.forEach { sb.appendLine("- $it") }
            }

            return sb.toString().trim()
        }
    }

    /**
     * Resolves a screen name like `"main"`, `"MainActivity"`, `"MainActivity.java"`, or `"main.xml"`
     * to its [ProjectFileBean].
     */
    fun resolveProjectFile(scId: String, screenName: String): ProjectFileBean? {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val fileMgr = jC.b(scId)
            val clean = screenName.trim()
            if (clean.isEmpty()) {
                return fileMgr.b(ProjectFileBean.DEFAULT_XML_NAME)
            }

            for (act in fileMgr.b().orEmpty()) {
                if (act.fileName.equals(clean, ignoreCase = true) ||
                    act.xmlName.equals(clean, ignoreCase = true) ||
                    act.javaName.equals(clean, ignoreCase = true) ||
                    act.activityName.equals(clean, ignoreCase = true)
                ) {
                    return act
                }
            }

            for (cv in fileMgr.c().orEmpty()) {
                if (cv.fileName.equals(clean, ignoreCase = true) ||
                    cv.xmlName.equals(clean, ignoreCase = true)
                ) {
                    return cv
                }
            }

            // Try normalizing e.g. "HomeActivity" -> "home"
            val snake = clean
                .removeSuffix(".java")
                .removeSuffix("Activity")
                .removeSuffix(".xml")
                .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                .lowercase(Locale.US)
            return fileMgr.b("$snake.xml") ?: fileMgr.a(ProjectFileBean.getJavaName(snake))
        }
    }

    /**
     * Creates a new Activity screen or Custom View if it does not already exist.
     */
    fun createScreenOrCustomView(
        scId: String,
        rawName: String,
        isCustomView: Boolean = false,
        hasToolbar: Boolean = true,
        isFullscreen: Boolean = false,
        hasFab: Boolean = false,
        hasDrawer: Boolean = false
    ): ProjectFileBean {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val cleanName = rawName.trim()
                .removeSuffix(".xml")
                .removeSuffix(".java")
                .removeSuffix("Activity")
                .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifEmpty { "second" }

            val existing = resolveProjectFile(scId, cleanName)
            if (existing != null) {
                return existing
            }

            val fileMgr = jC.b(scId)
            val dataMgr = jC.a(scId)
            val libMgr = jC.c(scId)

            val bean = if (isCustomView) {
                ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW, cleanName)
            } else {
                ProjectFileBean(
                    ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY,
                    cleanName,
                    ProjectFileBean.ORIENTATION_PORTRAIT,
                    ProjectFileBean.KEYBOARD_STATE_UNSPECIFIED,
                    hasToolbar,
                    isFullscreen,
                    hasFab,
                    hasDrawer
                )
            }

            if (isCustomView) {
                fileMgr.c().add(bean)
            } else {
                fileMgr.b().add(bean)
                if (hasDrawer) {
                    val drawerBean = ProjectFileBean(ProjectFileBean.PROJECT_FILE_TYPE_DRAWER, bean.drawerName)
                    if (fileMgr.c().none { it.fileName == drawerBean.fileName }) {
                        fileMgr.c().add(drawerBean)
                    }
                }
                if (hasDrawer || hasFab) {
                    libMgr.c().useYn = "Y"
                    libMgr.l()
                }
            }

            if (!dataMgr.c.containsKey(bean.xmlName)) {
                dataMgr.c[bean.xmlName] = ArrayList()
            }
            fileMgr.l()
            fileMgr.m()
            fileMgr.j()
            dataMgr.a(fileMgr)
            dataMgr.k()
            notifyUiChanged(scId, bean.xmlName)
            return bean
        }
    }

    /**
     * Reads the current Android XML layout of any screen or custom view using Sketchware's [Ox] generator.
     */
    fun readScreenXml(context: Context, scId: String, screenName: String): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Screen/CustomView '$screenName' tidak ditemukan di proyek $scId")
            val projectInfo = lC.b(scId)
            val q = yq(context.applicationContext, wq.d(scId), projectInfo)
            val xmlGen = Ox(q.N, pf)
            val dataMgr = jC.a(scId)
            val viewBeans = dataMgr.d(pf.xmlName)
            val viewFab = dataMgr.h(pf.xmlName)
            xmlGen.setExcludeAppCompat(true)
            xmlGen.a(eC.a(viewBeans), viewFab)
            return xmlGen.b()
        }
    }

    /**
     * Parses standard Android XML layout into [ViewBean] objects via [ViewBeanParser],
     * validates circular dependencies, updates undo history (`cC`), and auto-registers `onClick`
     * events for Buttons if requested.
     */
    fun writeScreenXml(
        scId: String,
        screenName: String,
        xmlContent: String,
        autoCreateButtonClickEvents: Boolean = true
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Screen/CustomView '$screenName' tidak ditemukan. Gunakan tool create_screen terlebih dahulu.")
            val xmlName = pf.xmlName
            val sanitizedXml = sanitizeLayoutXml(xmlContent)

            val parser = ViewBeanParser(sanitizedXml)
            parser.setSkipRoot(true)
            val parsedLayout = parser.parse()

            for (viewBean in parsedLayout) {
                val detector = CircularDependencyDetector(parsedLayout, viewBean)
                for (attr in viewBean.parentAttributes.keys) {
                    val targetId = viewBean.parentAttributes[attr]
                    if (!detector.isLegalAttribute(targetId, attr)) {
                        throw IllegalArgumentException("Circular dependency ditemukan pada view '${viewBean.id}' untuk atribut '$attr'")
                    }
                }
            }

            try {
                val rootAttrs = parser.rootAttributes
                val rootLayoutManager = InjectRootLayoutManager(scId)
                rootLayoutManager.set(xmlName, InjectRootLayoutManager.toRoot(rootAttrs))
            } catch (_: Throwable) {
            }

            val dataMgr = jC.a(scId)
            try {
                val historyBean = HistoryViewBean()
                historyBean.actionOverride(parsedLayout, dataMgr.d(xmlName))
                val cc = cC.c(scId)
                if (!cc.c.containsKey(xmlName)) {
                    cc.e(xmlName)
                }
                cc.a(xmlName)
                cc.a(xmlName, historyBean)
            } catch (_: Throwable) {
            }

            dataMgr.c[xmlName] = parsedLayout

            if (autoCreateButtonClickEvents && pf.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                val existingEvents = dataMgr.g(pf.javaName).orEmpty().map { it.eventKey }.toSet()
                for (vb in parsedLayout) {
                    if (vb.type == ViewBean.VIEW_TYPE_WIDGET_BUTTON && !vb.id.isNullOrEmpty()) {
                        val key = "${vb.id}_onClick"
                        if (!existingEvents.contains(key)) {
                            dataMgr.a(pf.javaName, EventBean.EVENT_TYPE_VIEW, vb.type, vb.id, "onClick")
                        }
                    }
                }
            }

            dataMgr.k()
            notifyUiChanged(scId, xmlName)
            return "Berhasil menerapkan XML layout ke $xmlName (${parsedLayout.size} ViewBean ter-parse: ${parsedLayout.joinToString(", ") { "${it.id}(type=${it.type})" }})."
        }
    }

    private fun sanitizeLayoutXml(rawXml: String): String {
        var xml = rawXml.trim()
        if (xml.startsWith("```")) {
            xml = xml.lines()
                .dropWhile { it.trim().startsWith("```") }
                .dropLastWhile { it.trim().startsWith("```") }
                .joinToString("\n")
                .trim()
        }
        // Ensure outer root container exists if the LLM passed a single widget or inner LinearLayout without root
        // Note: ViewBeanParser with setSkipRoot(true) skips the outermost tag (representing the root screen container)
        // and parses all child tags inside it as top-level views (parent="root").
        if (!xml.contains("xmlns:android")) {
            xml = """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:orientation="vertical">
                $xml
                </LinearLayout>
            """.trimIndent()
        }
        return xml
    }

    /**
     * Lists all Views, Variables, Lists, Components, MoreBlocks, and Events on a specific screen.
     */
    fun getScreenStateDetails(scId: String, screenName: String): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Screen '$screenName' tidak ditemukan.")
            val dataMgr = jC.a(scId)
            val xmlName = pf.xmlName
            val javaName = pf.javaName

            val sb = StringBuilder()
            sb.appendLine("=== SCREEN STATE: ${pf.fileName} (xml=$xmlName, java=$javaName) ===")

            val views = dataMgr.d(xmlName).orEmpty()
            sb.appendLine("\n[VIEWS (${views.size})]")
            for (v in views) {
                sb.appendLine(
                    "- id=${v.id}, type=${v.type}, parent=${v.parent}, text=\"${v.text?.text.orEmpty()}\", " +
                        "hint=\"${v.text?.hint.orEmpty()}\", customView=\"${v.customView.orEmpty()}\""
                )
            }

            if (pf.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                val vars = dataMgr.k(javaName).orEmpty()
                sb.appendLine("\n[VARIABLES (${vars.size})]")
                for (pair in vars) {
                    val typeStr = when (pair.first) {
                        0 -> "boolean (0)"
                        1 -> "number/double (1)"
                        2 -> "String (2)"
                        3 -> "Map (3)"
                        else -> "type=${pair.first}"
                    }
                    sb.appendLine("- ${pair.second} : $typeStr")
                }

                val lists = dataMgr.j(javaName).orEmpty()
                sb.appendLine("\n[LISTS (${lists.size})]")
                for (pair in lists) {
                    val typeStr = when (pair.first) {
                        1 -> "ListInt (1)"
                        2 -> "ListString (2)"
                        3 -> "ListMap (3)"
                        else -> "type=${pair.first}"
                    }
                    sb.appendLine("- ${pair.second} : $typeStr")
                }

                val comps = dataMgr.e(javaName).orEmpty()
                sb.appendLine("\n[COMPONENTS (${comps.size})]")
                for (c in comps) {
                    sb.appendLine(
                        "- id=${c.componentId}, type=${c.type} (${ComponentBean.getComponentTypeName(c.type)}), " +
                            "param1=\"${c.param1}\", param2=\"${c.param2}\", param3=\"${c.param3}\""
                    )
                }

                val moreBlocks = dataMgr.i(javaName).orEmpty()
                sb.appendLine("\n[MOREBLOCKS (${moreBlocks.size})]")
                for (mb in moreBlocks) {
                    val blockCount = dataMgr.a(javaName, "${mb.first}_moreBlock")?.size ?: 0
                    sb.appendLine("- name=${mb.first}, spec=\"${mb.second}\" ($blockCount blocks)")
                }

                val events = dataMgr.g(javaName).orEmpty()
                val onCreateBlocks = dataMgr.a(javaName, "onCreate_initializeLogic")?.size ?: 0
                sb.appendLine("\n[EVENTS (${events.size + 1})]")
                sb.appendLine("- onCreate_initializeLogic (targetId=onCreate, eventName=initializeLogic) [$onCreateBlocks blocks]")
                for (ev in events) {
                    val count = dataMgr.a(javaName, ev.eventKey)?.size ?: 0
                    sb.appendLine(
                        "- ${ev.eventKey} (eventType=${ev.eventType}, targetType=${ev.targetType}, " +
                            "targetId=${ev.targetId}, eventName=${ev.eventName}) [$count blocks]"
                    )
                }
            }

            return sb.toString().trim()
        }
    }

    /**
     * Adds or removes a Variable on an Activity:
     * `varType`: `"boolean"` (`0`), `"number"`/`"int"`/`"double"` (`1`), `"string"` (`2`), `"map"` (`3`).
     */
    fun manageVariable(
        scId: String,
        screenName: String,
        action: String,
        varName: String,
        varType: String
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val dataMgr = jC.a(scId)

            val typeInt = when (varType.trim().lowercase(Locale.US)) {
                "0", "bool", "boolean" -> 0
                "1", "int", "number", "double", "num" -> 1
                "2", "str", "string" -> 2
                "3", "map", "hashmap" -> 3
                else -> 2
            }

            if (action.equals("remove", ignoreCase = true) || action.equals("delete", ignoreCase = true)) {
                dataMgr.p(javaName, varName)
                dataMgr.o(javaName, varName)
                dataMgr.k()
                notifyUiChanged(scId, javaName)
                return "Variabel '$varName' dihapus dari $javaName."
            }

            val existing = dataMgr.k(javaName).orEmpty().any { it.second == varName }
            if (!existing) {
                dataMgr.c(javaName, typeInt, varName)
                dataMgr.k()
                notifyUiChanged(scId, javaName)
            }
            return "Variabel '$varName' (type=$typeInt) siap di $javaName."
        }
    }

    /**
     * Adds or removes a List on an Activity:
     * `listType`: `"int"`/`"number"` (`1`), `"string"` (`2`), `"map"`/`"listmap"` (`3`).
     */
    fun manageList(
        scId: String,
        screenName: String,
        action: String,
        listName: String,
        listType: String
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val dataMgr = jC.a(scId)

            val typeInt = when (listType.trim().lowercase(Locale.US)) {
                "1", "int", "number", "double", "listint" -> 1
                "2", "str", "string", "liststr", "liststring" -> 2
                "3", "map", "listmap" -> 3
                else -> 2
            }

            if (action.equals("remove", ignoreCase = true) || action.equals("delete", ignoreCase = true)) {
                dataMgr.o(javaName, listName)
                dataMgr.p(javaName, listName)
                dataMgr.k()
                notifyUiChanged(scId, javaName)
                return "List '$listName' dihapus dari $javaName."
            }

            val existing = dataMgr.j(javaName).orEmpty().any { it.second == listName }
            if (!existing) {
                dataMgr.b(javaName, typeInt, listName)
                dataMgr.k()
                notifyUiChanged(scId, javaName)
            }
            return "List '$listName' (type=$typeInt) siap di $javaName."
        }
    }

    /**
     * Adds a Component (`Intent`, `SharedPreferences`/`File`, `Calendar`, `Vibrator`, `Timer`,
     * `Dialog`, `RequestNetwork`, `FirebaseDB`, `FirebaseAuth`, `MediaPlayer`, `ObjectAnimator`, etc.)
     * to an Activity.
     */
    fun addComponent(
        scId: String,
        screenName: String,
        componentTypeOrName: String,
        componentId: String,
        param1: String = "",
        param2: String = "",
        param3: String = ""
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val dataMgr = jC.a(scId)

            val typeInt = resolveComponentTypeInt(componentTypeOrName)
            val existing = dataMgr.e(javaName).orEmpty().firstOrNull { it.componentId == componentId }
            if (existing != null) {
                existing.type = typeInt
                if (param1.isNotEmpty()) existing.param1 = param1
                if (param2.isNotEmpty()) existing.param2 = param2
                if (param3.isNotEmpty()) existing.param3 = param3
                existing.buildClassInfo()
            } else {
                dataMgr.a(javaName, typeInt, componentId, param1)
                val added = dataMgr.e(javaName).orEmpty().firstOrNull { it.componentId == componentId }
                if (added != null) {
                    if (param2.isNotEmpty()) added.param2 = param2
                    if (param3.isNotEmpty()) added.param3 = param3
                    added.buildClassInfo()
                }
            }

            // Auto-enable permissions if RequestNetwork is added
            if (typeInt == ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK) {
                addPermissions(scId, listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"))
            } else if (typeInt == ComponentBean.COMPONENT_TYPE_VIBRATOR) {
                addPermissions(scId, listOf("android.permission.VIBRATE"))
            }

            dataMgr.k()
            notifyUiChanged(scId, javaName)
            return "Component '$componentId' (type=$typeInt / ${ComponentBean.getComponentTypeName(typeInt)}) berhasil ditambahkan di $javaName."
        }
    }

    private fun resolveComponentTypeInt(raw: String): Int {
        val asInt = raw.trim().toIntOrNull()
        if (asInt != null && asInt > 0) return asInt
        return when (raw.trim().lowercase(Locale.US)) {
            "intent" -> ComponentBean.COMPONENT_TYPE_INTENT
            "file", "sharedpreferences", "sharedpref", "sp" -> ComponentBean.COMPONENT_TYPE_SHAREDPREF
            "calendar" -> ComponentBean.COMPONENT_TYPE_CALENDAR
            "vibrator" -> ComponentBean.COMPONENT_TYPE_VIBRATOR
            "timer", "timertask" -> ComponentBean.COMPONENT_TYPE_TIMERTASK
            "firebase", "firebasedb", "firebase_db" -> ComponentBean.COMPONENT_TYPE_FIREBASE
            "dialog", "alertdialog" -> ComponentBean.COMPONENT_TYPE_DIALOG
            "mediaplayer" -> ComponentBean.COMPONENT_TYPE_MEDIAPLAYER
            "soundpool" -> ComponentBean.COMPONENT_TYPE_SOUNDPOOL
            "objectanimator", "animator" -> ComponentBean.COMPONENT_TYPE_OBJECTANIMATOR
            "gyroscope" -> ComponentBean.COMPONENT_TYPE_GYROSCOPE
            "firebaseauth", "firebase_auth" -> ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH
            "interstitialad" -> ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD
            "firebasestorage", "firebase_storage" -> ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE
            "camera" -> ComponentBean.COMPONENT_TYPE_CAMERA
            "filepicker" -> ComponentBean.COMPONENT_TYPE_FILE_PICKER
            "requestnetwork", "http", "network" -> ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK
            "texttospeech", "tts" -> ComponentBean.COMPONENT_TYPE_TEXT_TO_SPEECH
            "speechtotext", "stt" -> ComponentBean.COMPONENT_TYPE_SPEECH_TO_TEXT
            "bluetooth", "bluetoothconnect" -> ComponentBean.COMPONENT_TYPE_BLUETOOTH_CONNECT
            "location", "locationmanager" -> ComponentBean.COMPONENT_TYPE_LOCATION_MANAGER
            "rewardedvideoad" -> ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD
            "progressdialog" -> ComponentBean.COMPONENT_TYPE_PROGRESS_DIALOG
            "datepickerdialog" -> ComponentBean.COMPONENT_TYPE_DATE_PICKER_DIALOG
            "timepickerdialog" -> ComponentBean.COMPONENT_TYPE_TIME_PICKER_DIALOG
            "notification" -> ComponentBean.COMPONENT_TYPE_NOTIFICATION
            "fragmentadapter" -> ComponentBean.COMPONENT_TYPE_FRAGMENT_ADAPTER
            else -> {
                val byName = ComponentBean.getComponentTypeByTypeName(raw.trim())
                if (byName > 0) byName else ComponentBean.COMPONENT_TYPE_INTENT
            }
        }
    }

    /**
     * Creates a MoreBlock (custom function) on an Activity.
     * Example: `funcName = "calculateTotal"`, `spec = "calculateTotal %d.price %s.label"` (or `returnType = "String"` -> `"calculateTotal|s ..."`).
     */
    fun createMoreBlock(
        scId: String,
        screenName: String,
        funcName: String,
        spec: String = "",
        returnType: String = "void"
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val dataMgr = jC.a(scId)

            val cleanName = funcName.trim().replace(Regex("[^a-zA-Z0-9_]"), "")
            val prefix = when (returnType.trim().lowercase(Locale.US)) {
                "void", "" -> cleanName
                "string", "s" -> "$cleanName|s"
                "number", "double", "d", "int" -> "$cleanName|d"
                "boolean", "bool", "b" -> "$cleanName|b"
                else -> "$cleanName|${returnType.trim()}"
            }
            val finalSpec = if (spec.isBlank()) {
                prefix
            } else if (spec.startsWith(cleanName)) {
                spec.replaceFirst(cleanName, prefix)
            } else {
                "$prefix ${spec.trim()}"
            }

            val existingList = dataMgr.i(javaName)
            val existingIdx = existingList?.indexOfFirst {
                ReturnMoreblockManager.getMbName(it.first) == cleanName
            } ?: -1

            if (existingIdx >= 0 && existingList != null) {
                existingList[existingIdx] = android.util.Pair(prefix, finalSpec)
            } else {
                dataMgr.a(javaName, prefix, finalSpec)
            }

            val eventKey = "${cleanName}_moreBlock"
            if (dataMgr.a(javaName, eventKey) == null) {
                dataMgr.a(javaName, eventKey, ArrayList())
            }
            dataMgr.k()
            notifyUiChanged(scId, javaName)
            return "MoreBlock '$cleanName' (spec='$finalSpec', eventKey='$eventKey') siap di $javaName."
        }
    }

    /**
     * Reads the current logic blocks and generated Java preview for an event or MoreBlock.
     */
    fun readEventLogic(
        scId: String,
        screenName: String,
        targetId: String,
        eventName: String
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val dataMgr = jC.a(scId)
            val eventKey = resolveEventKey(targetId, eventName)
            val blocks = dataMgr.a(javaName, eventKey)
            return SketchIrCompiler.decompileEventBlocks(pf.activityName, blocks, false)
        }
    }

    /**
     * Compiles and writes logic blocks (from Block-Script DSL or JSON block array) into an Activity's
     * event or MoreBlock, automatically registering the [EventBean] if it does not yet exist!
     */
    fun writeEventLogic(
        context: Context,
        scId: String,
        screenName: String,
        targetId: String,
        eventName: String,
        blocksInput: String,
        append: Boolean = false
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val pf = resolveProjectFile(scId, screenName)
                ?: throw IllegalArgumentException("Activity '$screenName' tidak ditemukan.")
            val javaName = pf.javaName
            val xmlName = pf.xmlName
            val dataMgr = jC.a(scId)

            val (normTargetId, normEventName, eventKey) = normalizeEventTarget(targetId, eventName)
            ensureEventBeanRegistered(dataMgr, javaName, xmlName, normTargetId, normEventName)

            val existingBlocks = dataMgr.a(javaName, eventKey).orEmpty()
            val startId = if (append && existingBlocks.isNotEmpty()) {
                (existingBlocks.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 10) + 10
            } else {
                10
            }

            val compiledBlocks = SketchIrCompiler.compile(context, blocksInput, startId)
            val finalBlocks = if (append && existingBlocks.isNotEmpty() && compiledBlocks.isNotEmpty()) {
                val merged = ArrayList<BlockBean>(existingBlocks.map { it.clone() })
                // Find the last top-level statement block in existingBlocks and link its nextBlock
                val referencedInSubOrParam = mutableSetOf<String>()
                for (b in merged) {
                    if (b.subStack1 > 0) referencedInSubOrParam.add(b.subStack1.toString())
                    if (b.subStack2 > 0) referencedInSubOrParam.add(b.subStack2.toString())
                    b.parameters?.forEach { p ->
                        if (p != null && p.startsWith("@")) referencedInSubOrParam.add(p.removePrefix("@"))
                    }
                }
                val byId = merged.associateBy { it.id }
                var curr = merged.firstOrNull { !referencedInSubOrParam.contains(it.id) }
                while (curr != null && curr.nextBlock > 0 && byId.containsKey(curr.nextBlock.toString())) {
                    curr = byId[curr.nextBlock.toString()]
                }
                if (curr != null) {
                    curr.nextBlock = compiledBlocks.first().id.toInt()
                }
                merged.addAll(compiledBlocks)
                merged
            } else {
                compiledBlocks
            }

            dataMgr.a(javaName, eventKey, finalBlocks)

            val javaPreview = SketchIrCompiler.decompileEventBlocks(pf.activityName, finalBlocks, false)
            val harnessNotes = runProactiveEventAutoHealAndLint(scId, javaName, xmlName, eventKey, finalBlocks, javaPreview)

            dataMgr.k()
            notifyUiChanged(scId, javaName)

            return buildString {
                append("Berhasil menyimpan ${finalBlocks.size} BlockBean ke $javaName -> $eventKey.")
                if (harnessNotes.isNotBlank()) {
                    append("\n")
                    append(harnessNotes)
                }
                append("\n\n")
                append(javaPreview)
            }
        }
    }

    private val commonClassImports: Map<String, String> = mapOf(
        "Gson" to "com.google.gson.Gson",
        "TypeToken" to "com.google.gson.reflect.TypeToken",
        "JsonObject" to "com.google.gson.JsonObject",
        "JsonArray" to "com.google.gson.JsonArray",
        "JsonParser" to "com.google.gson.JsonParser",
        "JSONObject" to "org.json.JSONObject",
        "JSONArray" to "org.json.JSONArray",
        "Color" to "android.graphics.Color",
        "GradientDrawable" to "android.graphics.drawable.GradientDrawable",
        "Typeface" to "android.graphics.Typeface",
        "EditText" to "android.widget.EditText",
        "TextView" to "android.widget.TextView",
        "Button" to "android.widget.Button",
        "LinearLayout" to "android.widget.LinearLayout",
        "RelativeLayout" to "android.widget.RelativeLayout",
        "ImageView" to "android.widget.ImageView",
        "CheckBox" to "android.widget.CheckBox",
        "Switch" to "android.widget.Switch",
        "ProgressBar" to "android.widget.ProgressBar",
        "SeekBar" to "android.widget.SeekBar",
        "ScrollView" to "android.widget.ScrollView",
        "Toast" to "android.widget.Toast",
        "AlertDialog" to "android.app.AlertDialog",
        "DialogInterface" to "android.content.DialogInterface",
        "Intent" to "android.content.Intent",
        "Uri" to "android.net.Uri",
        "ClipboardManager" to "android.content.ClipboardManager",
        "ClipData" to "android.content.ClipData",
        "Context" to "android.content.Context",
        "View" to "android.view.View",
        "Gravity" to "android.view.Gravity",
        "Handler" to "android.os.Handler",
        "Looper" to "android.os.Looper",
        "ArrayList" to "java.util.ArrayList",
        "HashMap" to "java.util.HashMap",
        "Map" to "java.util.Map",
        "Collections" to "java.util.Collections",
        "Random" to "java.util.Random",
        "Timer" to "java.util.Timer",
        "TimerTask" to "java.util.TimerTask",
        "SimpleDateFormat" to "java.text.SimpleDateFormat",
        "Date" to "java.util.Date",
        "Locale" to "java.util.Locale",
        "DecimalFormat" to "java.text.DecimalFormat",
        "File" to "java.io.File"
    )

    private fun runProactiveEventAutoHealAndLint(
        scId: String,
        javaName: String,
        xmlName: String,
        eventKey: String,
        blocks: List<BlockBean>,
        javaPreview: String
    ): String {
        val dataMgr = jC.a(scId)
        val fileMgr = jC.b(scId)
        val autoVars = mutableListOf<String>()
        val autoLists = mutableListOf<String>()
        val autoImports = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val existingVars = dataMgr.k(javaName).orEmpty().map { it.second }.toMutableSet()
        val existingLists = dataMgr.j(javaName).orEmpty().map { it.second }.toMutableSet()

        fun ensureVar(name: String?, typeInt: Int, label: String) {
            val clean = name?.trim().orEmpty()
            if (clean.isEmpty() || clean.startsWith("@") || !clean.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) return
            if (existingVars.add(clean)) {
                dataMgr.c(javaName, typeInt, clean)
                autoVars.add("$clean ($label)")
            }
        }

        fun ensureList(name: String?, typeInt: Int, label: String) {
            val clean = name?.trim().orEmpty()
            if (clean.isEmpty() || clean.startsWith("@") || !clean.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) return
            if (existingLists.add(clean)) {
                dataMgr.b(javaName, typeInt, clean)
                autoLists.add("$clean ($label)")
            }
        }

        val knownViewIds = mutableSetOf<String>()
        dataMgr.d(xmlName).orEmpty().mapNotNullTo(knownViewIds) { it.id }
        dataMgr.d("_drawer_$xmlName").orEmpty().mapNotNullTo(knownViewIds) { it.id }
        for (cv in fileMgr.c().orEmpty()) {
            dataMgr.d(cv.xmlName).orEmpty().mapNotNullTo(knownViewIds) { it.id }
        }

        for (b in blocks) {
            val p0 = b.parameters?.getOrNull(0)
            val p1 = b.parameters?.getOrNull(1)
            when (b.opCode) {
                "setVarBoolean" -> ensureVar(p0, 0, "boolean")
                "setVarInt", "increaseInt", "decreaseInt" -> ensureVar(p0, 1, "number")
                "setVarString" -> ensureVar(p0, 2, "String")
                "createNewMap", "mapPutValue", "mapGetValue", "mapContainKey", "mapRemoveKey", "mapClear", "mapSize", "mapIsEmpty", "mapToStr" ->
                    ensureVar(p0, 3, "Map")
                "strToMap" -> ensureVar(p1, 3, "Map")
                "addListInt", "insertListInt" -> ensureList(b.parameters?.lastOrNull(), 1, "ListInt")
                "addListStr", "insertListStr", "strToListStr" -> ensureList(b.parameters?.lastOrNull(), 2, "ListString")
                "addListMap", "insertListMap", "setListMap", "strToListMap" -> ensureList(b.parameters?.lastOrNull(), 3, "ListMap")
                "listMapToStr" -> ensureList(p0, 3, "ListMap")
                "setText", "getText", "setVisible", "setBgColor", "setTextColor", "setChecked", "getChecked", "setEnable", "listSetData", "listRefresh" -> {
                    val viewId = p0?.trim().orEmpty()
                    if (viewId.isNotEmpty() && !viewId.startsWith("@") && knownViewIds.isNotEmpty() && !knownViewIds.contains(viewId)) {
                        warnings.add("View ID '$viewId' pada blok '${b.opCode}' belum ditemukan di $xmlName.")
                    }
                }
            }
        }

        // Scan Java preview for common class names and auto-register type-9 custom imports in Jx
        for ((simpleName, fqcn) in commonClassImports) {
            if (Regex("\\b$simpleName\\b").containsMatchIn(javaPreview)) {
                if (existingVars.add(fqcn)) {
                    dataMgr.c(javaName, 9, fqcn)
                    autoImports.add(fqcn)
                }
            }
        }

        val sb = StringBuilder()
        if (autoVars.isNotEmpty()) {
            sb.appendLine("✨ [Harness Auto-Heal] Variabel otomatis didaftarkan: ${autoVars.joinToString(", ")}")
        }
        if (autoLists.isNotEmpty()) {
            sb.appendLine("✨ [Harness Auto-Heal] List otomatis didaftarkan: ${autoLists.joinToString(", ")}")
        }
        if (autoImports.isNotEmpty()) {
            sb.appendLine("✨ [Harness Auto-Heal] Import otomatis disuntikkan ke $javaName: ${autoImports.joinToString(", ")}")
        }
        if (warnings.isNotEmpty()) {
            sb.appendLine("⚠️ [Harness Pre-Flight Linter]: ${warnings.distinct().joinToString(" | ")}")
        }
        return sb.toString().trim()
    }

    private fun resolveEventKey(targetId: String, eventName: String): String {
        return normalizeEventTarget(targetId, eventName).third
    }

    private fun normalizeEventTarget(targetId: String, eventName: String): Triple<String, String, String> {
        val t = targetId.trim()
        val e = eventName.trim()
        if (t.equals("onCreate", ignoreCase = true) || e.equals("onCreate", ignoreCase = true) || e.equals("initializeLogic", ignoreCase = true)) {
            return Triple("onCreate", "initializeLogic", "onCreate_initializeLogic")
        }
        if (e.equals("moreBlock", ignoreCase = true)) {
            val cleanFn = ReturnMoreblockManager.getMbName(t)
            return Triple(cleanFn, "moreBlock", "${cleanFn}_moreBlock")
        }
        if (t.isEmpty() || t.equals("Activity", ignoreCase = true)) {
            return Triple(e, e, "${e}_$e")
        }
        if (e.startsWith("${t}_")) {
            val stripped = e.removePrefix("${t}_")
            return Triple(t, stripped, "${t}_$stripped")
        }
        return Triple(t, e, "${t}_$e")
    }

    private fun ensureEventBeanRegistered(
        dataMgr: eC,
        javaName: String,
        xmlName: String,
        targetId: String,
        eventName: String
    ) {
        if (targetId == "onCreate" && eventName == "initializeLogic") return
        if (eventName == "moreBlock") return

        val eventKey = "${targetId}_$eventName"
        val existingEvents = dataMgr.g(javaName).orEmpty()
        if (existingEvents.any { it.eventKey == eventKey }) return

        // Check if targetId is a View in xmlName
        val viewBean = dataMgr.c(xmlName, targetId)
        if (viewBean != null) {
            dataMgr.a(javaName, EventBean.EVENT_TYPE_VIEW, viewBean.type, targetId, eventName)
            return
        }

        // Check if targetId is a View in Drawer
        val drawerViewBean = dataMgr.c("_drawer_$xmlName", targetId)
        if (drawerViewBean != null) {
            dataMgr.a(javaName, EventBean.EVENT_TYPE_DRAWER_VIEW, drawerViewBean.type, targetId, eventName)
            return
        }

        // Check if targetId is a Component in javaName
        val compBean = dataMgr.e(javaName).orEmpty().firstOrNull { it.componentId == targetId }
        if (compBean != null) {
            dataMgr.a(javaName, EventBean.EVENT_TYPE_COMPONENT, compBean.type, targetId, eventName)
            return
        }

        // Otherwise register as Activity Lifecycle Event (e.g. onBackPressed, onStart, onResume, onPause, onDestroy)
        dataMgr.a(javaName, EventBean.EVENT_TYPE_ACTIVITY, -1, targetId, eventName)
    }

    fun setLibraryEnabled(
        scId: String,
        libraryName: String,
        enabled: Boolean,
        configData1: String = "",
        configData2: String = "",
        configData3: String = ""
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val libMgr = jC.c(scId)
            val useVal = if (enabled) "Y" else "N"
            val bean = when (libraryName.trim().lowercase(Locale.US)) {
                "appcompat", "design", "material" -> libMgr.c()
                "firebase" -> libMgr.b()
                "admob" -> libMgr.d()
                "googlemap", "map", "maps" -> libMgr.e()
                else -> throw IllegalArgumentException("Library tidak dikenal: $libraryName (gunakan appcompat, firebase, admob, atau googlemap)")
            }
            bean.useYn = useVal
            if (configData1.isNotEmpty()) bean.data = configData1
            if (configData2.isNotEmpty()) bean.reserved1 = configData2
            if (configData3.isNotEmpty()) bean.reserved2 = configData3
            libMgr.l()
            jC.b(scId).a(libMgr)
            notifyUiChanged(scId, null)
            return "Library '$libraryName' diatur ke useYn=$useVal."
        }
    }

    fun addPermissions(scId: String, permissions: List<String>): String {
        synchronized(lock) {
            val fpu = FilePathUtil()
            val path = fpu.getPathPermission(scId)
            val frc = FileResConfig(scId)
            val current = ArrayList<String>(frc.permissionList)
            for (raw in permissions) {
                val p = raw.trim()
                if (p.isEmpty()) continue
                val fullPerm = if (p.contains(".")) p else "android.permission.${p.uppercase(Locale.US)}"
                if (!current.contains(fullPerm)) {
                    current.add(fullPerm)
                }
            }
            FileUtil.writeFile(path, Gson().toJson(current))
            return "Permissions aktif (${current.size}): ${current.joinToString(", ")}"
        }
    }

    fun addCustomImports(scId: String, imports: List<String>): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val fpu = FilePathUtil()
            val path = fpu.getPathImport(scId)
            val frc = FileResConfig(scId)
            val current = ArrayList<String>(frc.importList)
            val dataMgr = jC.a(scId)
            val activities = jC.b(scId).b().orEmpty()

            for (raw in imports) {
                val imp = raw.trim().removePrefix("import ").removeSuffix(";").trim()
                if (imp.isEmpty()) continue
                if (!current.contains(imp)) {
                    current.add(imp)
                }
                // Also register as variableId=9 in each Activity so Jx.java emits the import statement
                for (act in activities) {
                    val javaName = act.javaName
                    val existing = dataMgr.k(javaName).orEmpty().any { it.first == 9 && it.second == imp }
                    if (!existing) {
                        dataMgr.c(javaName, 9, imp)
                    }
                }
            }
            dataMgr.k()
            FileUtil.writeFile(path, Gson().toJson(current))
            return "Custom imports aktif (${current.size}): ${current.joinToString(", ")}"
        }
    }

    fun writeCustomJavaFile(
        scId: String,
        fileName: String,
        sourceCode: String
    ): String {
        synchronized(lock) {
            val fpu = FilePathUtil()
            val javaDir = File(fpu.getPathJava(scId))
            if (!javaDir.exists()) javaDir.mkdirs()
            val cleanName = if (fileName.endsWith(".java") || fileName.endsWith(".kt")) {
                fileName.trim()
            } else {
                "${fileName.trim()}.java"
            }
            val target = File(javaDir, cleanName)
            target.parentFile?.mkdirs()
            FileUtil.writeFile(target.absolutePath, sourceCode)
            return "File custom class disimpan di ${target.absolutePath} (${sourceCode.length} chars)."
        }
    }

    private fun resolveSketchwarePath(scId: String, rawPath: String): File {
        val extRoot = Environment.getExternalStorageDirectory()
        val swRoot = File(extRoot, ".sketchware")
        val trimmed = rawPath.trim()
        val f: File = when {
            trimmed.isEmpty() || trimmed == "." || trimmed == "@sketchware" || trimmed == "@sketchware/" -> swRoot
            trimmed.startsWith("@project/") || trimmed == "@project" ->
                File(File(swRoot, "data/$scId"), trimmed.removePrefix("@project").removePrefix("/"))
            trimmed.startsWith("@files/") || trimmed == "@files" ->
                File(File(swRoot, "data/$scId/files"), trimmed.removePrefix("@files").removePrefix("/"))
            trimmed.startsWith("@mysc/") || trimmed == "@mysc" ->
                File(File(swRoot, "mysc/$scId"), trimmed.removePrefix("@mysc").removePrefix("/"))
            trimmed.startsWith("@libs/") || trimmed == "@libs" ->
                File(File(swRoot, "libs/local_libs"), trimmed.removePrefix("@libs").removePrefix("/"))
            trimmed.startsWith("@system/") || trimmed == "@system" ->
                File(File(swRoot, "system"), trimmed.removePrefix("@system").removePrefix("/"))
            trimmed.startsWith("/") -> File(trimmed)
            trimmed.startsWith(".sketchware/") -> File(extRoot, trimmed)
            trimmed.startsWith("data/") || trimmed.startsWith("libs/") || trimmed.startsWith("mysc/") ||
                trimmed.startsWith("system/") || trimmed.startsWith("resources/") || trimmed.startsWith("agent/") ->
                File(swRoot, trimmed)
            else -> File(File(swRoot, "data/$scId"), trimmed)
        }
        return try {
            f.canonicalFile
        } catch (_: Exception) {
            f.absoluteFile
        }
    }

    /**
     * Full filesystem access for Sketchware & project files (list, read, write, append, delete, mkdir, copy, move).
     * Never forces `.java` extension, allowing the AI Agent to create/edit/delete XML resources, local libraries,
     * JSON configs (`local_library`, `component.json`, `block.json`), Java/Kotlin files, or directories directly.
     */
    fun manageSketchwareFiles(
        scId: String,
        action: String,
        path: String,
        content: String = "",
        targetPath: String = "",
        recursive: Boolean = false
    ): String {
        synchronized(lock) {
            val act = action.trim().lowercase(Locale.US)
            val file = resolveSketchwarePath(scId, path)

            return when (act) {
                "list", "ls", "dir" -> {
                    if (!file.exists()) {
                        return "Path tidak ditemukan: ${file.absolutePath}"
                    }
                    if (file.isFile) {
                        return "[FILE] ${file.absolutePath} (${file.length()} bytes)"
                    }
                    val entries = mutableListOf<String>()
                    if (recursive) {
                        file.walkTopDown().maxDepth(5).take(250).forEach { child ->
                            if (child != file) {
                                val rel = child.relativeTo(file).path.replace('\\', '/')
                                val tag = if (child.isDirectory) "[DIR] " else "[FILE]"
                                val sizeStr = if (child.isFile) " (${child.length()} B)" else ""
                                entries.add("$tag $rel$sizeStr")
                            }
                        }
                    } else {
                        file.listFiles()
                            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name }))
                            ?.take(250)
                            ?.forEach { child ->
                                val tag = if (child.isDirectory) "[DIR] " else "[FILE]"
                                val sizeStr = if (child.isFile) " (${child.length()} B)" else ""
                                entries.add("$tag ${child.name}$sizeStr")
                            }
                    }
                    buildString {
                        appendLine("Direktori: ${file.absolutePath} (${entries.size} item)")
                        if (entries.isEmpty()) {
                            appendLine("(Kosong)")
                        } else {
                            entries.forEach { appendLine("- $it") }
                        }
                    }.trim()
                }

                "read", "cat", "get" -> {
                    if (!file.exists()) {
                        return "Error: File tidak ditemukan di ${file.absolutePath}"
                    }
                    if (file.isDirectory) {
                        return "Error: ${file.absolutePath} adalah direktori. Gunakan action='list'."
                    }
                    val text = FileUtil.readFile(file.absolutePath)
                    val maxLen = 24000
                    val body = if (text.length > maxLen) {
                        text.take(maxLen) + "\n... [Dipangkas, total ${text.length} chars]"
                    } else {
                        text
                    }
                    "=== FILE: ${file.absolutePath} (${file.length()} bytes) ===\n$body"
                }

                "write", "put", "create" -> {
                    file.parentFile?.mkdirs()
                    FileUtil.writeFile(file.absolutePath, content)
                    "Berhasil menulis file ${file.absolutePath} (${file.length()} bytes)."
                }

                "append" -> {
                    file.parentFile?.mkdirs()
                    val existing = if (file.exists() && file.isFile) FileUtil.readFile(file.absolutePath) else ""
                    FileUtil.writeFile(file.absolutePath, existing + content)
                    "Berhasil menambahkan ke file ${file.absolutePath} (ukuran baru: ${file.length()} bytes)."
                }

                "mkdir", "mkdirs" -> {
                    val ok = file.mkdirs() || file.isDirectory
                    if (ok) "Direktori siap: ${file.absolutePath}"
                    else "Gagal membuat direktori: ${file.absolutePath}"
                }

                "delete", "rm", "remove" -> {
                    if (!file.exists()) {
                        return "File/direktori sudah tidak ada: ${file.absolutePath}"
                    }
                    val isDir = file.isDirectory
                    FileUtil.deleteFile(file.absolutePath)
                    val gone = !file.exists()
                    if (gone) {
                        "Berhasil menghapus ${if (isDir) "direktori" else "file"}: ${file.absolutePath}"
                    } else {
                        file.deleteRecursively()
                        "Status hapus ${file.absolutePath}: terhapus=${!file.exists()}"
                    }
                }

                "copy", "cp" -> {
                    if (targetPath.isBlank()) {
                        return "Error: parameter 'target_path' wajib diisi untuk action='copy'."
                    }
                    if (!file.exists()) {
                        return "Error: sumber tidak ditemukan: ${file.absolutePath}"
                    }
                    val dest = resolveSketchwarePath(scId, targetPath)
                    dest.parentFile?.mkdirs()
                    file.copyRecursively(dest, overwrite = true)
                    "Berhasil menyalin ${file.absolutePath} -> ${dest.absolutePath}."
                }

                "move", "mv", "rename" -> {
                    if (targetPath.isBlank()) {
                        return "Error: parameter 'target_path' wajib diisi untuk action='move'."
                    }
                    if (!file.exists()) {
                        return "Error: sumber tidak ditemukan: ${file.absolutePath}"
                    }
                    val dest = resolveSketchwarePath(scId, targetPath)
                    dest.parentFile?.mkdirs()
                    val moved = file.renameTo(dest)
                    if (!moved) {
                        file.copyRecursively(dest, overwrite = true)
                        FileUtil.deleteFile(file.absolutePath)
                    }
                    "Berhasil memindahkan/mengubah nama ${file.absolutePath} -> ${dest.absolutePath}."
                }

                else -> "Error: action '$action' tidak dikenal. Gunakan: list, read, write, append, mkdir, delete, copy, atau move."
            }
        }
    }

    /**
     * Manages local libraries in `.sketchware/data/<sc_id>/local_library` and `.sketchware/libs/local_libs/`.
     * Actions: `list`, `enable`, `disable` (supports `"all_missing"`), `repair`, `install_maven`, `import_jar`.
     */
    fun manageLocalLibraries(
        scId: String,
        action: String,
        libraryNames: String = "",
        mavenCoordinate: String = "",
        skipSubdependencies: Boolean = true,
        sourcePath: String = "",
        packageName: String = ""
    ): String {
        synchronized(lock) {
            val fpu = FilePathUtil()
            val configPath = fpu.getPathLocalLibrary(scId)
            val localLibsRoot = File(Environment.getExternalStorageDirectory(), ".sketchware/libs/local_libs")
            if (!localLibsRoot.exists()) localLibsRoot.mkdirs()

            val currentList: ArrayList<HashMap<String, Any>> = try {
                if (FileUtil.isExistFile(configPath)) {
                    val raw = FileUtil.readFile(configPath).trim()
                    if (raw.isEmpty() || raw == "null") ArrayList()
                    else Gson().fromJson<ArrayList<HashMap<String, Any>>>(raw, Helper.TYPE_MAP_LIST) ?: ArrayList()
                } else {
                    ArrayList()
                }
            } catch (_: Exception) {
                ArrayList()
            }

            val act = action.trim().lowercase(Locale.US)
            val targets = libraryNames.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

            return when (act) {
                "list", "status", "inspect" -> {
                    val installedFolders = localLibsRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
                    buildString {
                        appendLine("=== LOCAL LIBRARIES AKTIF DI PROYEK #$scId (${currentList.size}) ===")
                        if (currentList.isEmpty()) {
                            appendLine("(Tidak ada local library aktif)")
                        } else {
                            for ((idx, item) in currentList.withIndex()) {
                                val name = item["name"]?.toString().orEmpty()
                                val pkg = item["packageName"]?.toString().orEmpty()
                                val jarPath = item["jarPath"]?.toString().orEmpty()
                                val dexPath = item["dexPath"]?.toString().orEmpty()
                                val resPath = item["resPath"]?.toString().orEmpty()
                                val jarOk = jarPath.isNotEmpty() && File(jarPath).isFile
                                val dexOk = dexPath.isNotEmpty() && File(dexPath).isFile
                                val resOk = resPath.isEmpty() || File(resPath).isDirectory
                                appendLine("${idx + 1}. $name (pkg=$pkg) | jar=${if (jarOk) "OK" else "MISSING"} | dex=${if (dexOk) "OK" else "MISSING"} | res=${if (resOk) "OK" else "MISSING"}")
                            }
                        }
                        appendLine("\n=== FOLDER DI .sketchware/libs/local_libs (${installedFolders.size}) ===")
                        for (dir in installedFolders.take(80)) {
                            val hasJar = File(dir, "classes.jar").isFile
                            val hasDex = File(dir, "classes.dex").isFile
                            val hasRes = File(dir, "res").isDirectory
                            appendLine("- ${dir.name} [jar=$hasJar, dex=$hasDex, res=$hasRes]")
                        }
                    }.trim()
                }

                "disable", "remove" -> {
                    val removeMissing = targets.any { it.equals("all_missing", ignoreCase = true) }
                    val targetSet = targets.map { it.lowercase(Locale.US) }.toSet()
                    val removed = mutableListOf<String>()
                    val retained = ArrayList<HashMap<String, Any>>()

                    for (item in currentList) {
                        val name = item["name"]?.toString().orEmpty()
                        val jarPath = item["jarPath"]?.toString().orEmpty()
                        val dexPath = item["dexPath"]?.toString().orEmpty()
                        val isMissingBinaries = (jarPath.isNotEmpty() && !File(jarPath).isFile) &&
                            (dexPath.isNotEmpty() && !File(dexPath).isFile)
                        if (targetSet.contains(name.lowercase(Locale.US)) || (removeMissing && isMissingBinaries)) {
                            removed.add(name)
                        } else {
                            retained.add(item)
                        }
                    }
                    FileUtil.writeFile(configPath, gson.toJson(retained))
                    "Local library dinonaktifkan (${removed.size}): ${removed.joinToString(", ")}. Sisa aktif: ${retained.size}."
                }

                "enable", "add" -> {
                    val added = mutableListOf<String>()
                    for (libName in targets) {
                        if (currentList.any { it["name"]?.toString().equals(libName, ignoreCase = true) }) continue
                        val libDir = File(localLibsRoot, libName)
                        if (!libDir.exists()) libDir.mkdirs()
                        val entry = HashMap<String, Any>()
                        entry["name"] = libName
                        val jarFile = File(libDir, "classes.jar")
                        val dexFile = File(libDir, "classes.dex")
                        val resDir = File(libDir, "res")
                        val assetsDir = File(libDir, "assets")
                        val manifestFile = File(libDir, "AndroidManifest.xml")
                        val pgFile = File(libDir, "proguard.txt")
                        val configFile = File(libDir, "config")
                        entry["jarPath"] = jarFile.absolutePath
                        entry["dexPath"] = dexFile.absolutePath
                        if (resDir.exists()) entry["resPath"] = resDir.absolutePath
                        if (assetsDir.exists()) entry["assetsPath"] = assetsDir.absolutePath
                        if (manifestFile.exists()) entry["manifestPath"] = manifestFile.absolutePath
                        if (pgFile.exists()) entry["pgRulesPath"] = pgFile.absolutePath
                        if (configFile.isFile) {
                            val pkg = FileUtil.readFile(configFile.absolutePath).trim()
                            if (pkg.isNotEmpty()) entry["packageName"] = pkg
                        }
                        currentList.add(entry)
                        added.add(libName)
                    }
                    FileUtil.writeFile(configPath, gson.toJson(currentList))
                    "Local library diaktifkan (${added.size}): ${added.joinToString(", ")}. Total aktif: ${currentList.size}."
                }

                "install_maven", "download_maven", "maven" -> {
                    val coord = mavenCoordinate.ifBlank { targets.firstOrNull().orEmpty() }.trim()
                    val parts = coord.split(":")
                    if (parts.size != 3) {
                        return "Error: Format koordinat Maven harus 'groupId:artifactId:version' (contoh: 'com.google.code.gson:gson:2.10.1' atau 'com.airbnb.android:lottie:6.1.0')."
                    }
                    val (groupId, artifactId, version) = parts
                    BuiltInLibraries.maybeExtractAndroidJar()
                    BuiltInLibraries.maybeExtractCoreLambdaStubsJar()

                    val buildSettings = BuildSettings(scId)
                    val resolver = DependencyResolver(groupId, artifactId, version, skipSubdependencies, buildSettings)
                    // Ensure JitPack and Maven Central are always available
                    for ((repoName, repoUrl) in listOf(
                        "Maven Central" to "https://repo1.maven.org/maven2",
                        "JitPack" to "https://jitpack.io"
                    )) {
                        if (repositories.none { it.getURL().equals(repoUrl, ignoreCase = true) }) {
                            repositories.add(object : Repository {
                                override fun getName(): String = repoName
                                override fun getURL(): String = repoUrl
                            })
                        }
                    }

                    val completedLibs = mutableListOf<String>()
                    val errors = mutableListOf<String>()
                    val rootFolderName = "$artifactId-v$version"

                    resolver.resolveDependency(object : DependencyResolver.DependencyResolverCallback() {
                        override fun onArtifactNotFound(artifact: Artifact) {
                            errors.add("Artifact tidak ditemukan: $artifact")
                        }
                        override fun onVersionNotFound(artifact: Artifact) {
                            errors.add("Versi tidak ditemukan: $artifact")
                        }
                        override fun onResolutionError(error: Exception) {
                            errors.add("Gagal me-resolve '$coord': ${error.message}")
                        }
                        override fun onDownloadError(artifact: Artifact, error: Throwable) {
                            errors.add("Gagal mengunduh '$artifact': ${error.message}")
                        }
                        override fun dexingFailed(artifact: Artifact, e: Exception) {
                            errors.add("Gagal kompilasi D8/dex '$artifact': ${e.message}")
                        }
                        override fun onTaskCompleted(artifacts: List<String>) {
                            completedLibs.add(rootFolderName)
                            artifacts.forEach { if (!completedLibs.contains(it)) completedLibs.add(it) }
                        }
                    })

                    val rootDir = File(localLibsRoot, rootFolderName)
                    if (File(rootDir, "classes.dex").isFile && !completedLibs.contains(rootFolderName)) {
                        completedLibs.add(rootFolderName)
                    }

                    if (completedLibs.isEmpty()) {
                        return "Error: Gagal memasang Maven '$coord'. Detail: ${errors.joinToString("; ").ifEmpty { "Artifact tidak ditemukan di repositori" }}"
                    }

                    val newlyEnabled = mutableListOf<String>()
                    for (libFolder in completedLibs.distinct()) {
                        val libDir = File(localLibsRoot, libFolder)
                        if (!File(libDir, "classes.jar").isFile && !File(libDir, "classes.dex").isFile) continue
                        val existingIdx = currentList.indexOfFirst { it["name"]?.toString().equals(libFolder, ignoreCase = true) }
                        @Suppress("UNCHECKED_CAST")
                        val mapEntry = LocalLibrariesUtil.createLibraryMap(libFolder, coord) as HashMap<String, Any>
                        if (existingIdx >= 0) {
                            currentList[existingIdx] = mapEntry
                        } else {
                            currentList.add(mapEntry)
                        }
                        newlyEnabled.add(libFolder)
                    }
                    FileUtil.writeFile(configPath, gson.toJson(currentList))
                    buildString {
                        appendLine("Berhasil mengunduh, mengompilasi D8 (classes.dex), dan mengaktifkan Maven '$coord'!")
                        appendLine("Local libraries aktif yang ditambahkan/diperbarui: ${newlyEnabled.joinToString(", ")}")
                        if (errors.isNotEmpty()) {
                            appendLine("Catatan sub-dependensi: ${errors.joinToString("; ")}")
                        }
                    }.trim()
                }

                "import_jar", "import_aar", "compile_jar" -> {
                    val srcPath = sourcePath.trim()
                    if (srcPath.isEmpty()) {
                        return "Error: Parameter 'source_path' wajib diisi (path ke file .jar atau .aar)."
                    }
                    val srcFile = resolveSketchwarePath(scId, srcPath)
                    if (!srcFile.isFile) {
                        return "Error: File sumber tidak ditemukan: ${srcFile.absolutePath}"
                    }
                    val libName = targets.firstOrNull()
                        ?: srcFile.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val libDir = File(localLibsRoot, libName).apply { mkdirs() }
                    val targetJar = File(libDir, "classes.jar")

                    if (srcFile.name.endsWith(".aar", ignoreCase = true)) {
                        ZipFile(srcFile).use { zip ->
                            zip.entries().asSequence().forEach { entry ->
                                val outDest = File(libDir, entry.name)
                                if (entry.isDirectory) {
                                    outDest.mkdirs()
                                } else {
                                    outDest.parentFile?.mkdirs()
                                    zip.getInputStream(entry).use { input ->
                                        outDest.outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                            }
                        }
                        val pkg = packageName.trim().ifEmpty {
                            val manifest = File(libDir, "AndroidManifest.xml")
                            if (manifest.isFile) {
                                Regex("package=\"([^\"]+)\"").find(manifest.readText())?.groupValues?.getOrNull(1).orEmpty()
                            } else ""
                        }
                        if (pkg.isNotEmpty()) {
                            File(libDir, "config").writeText(pkg)
                        }
                    } else {
                        srcFile.copyTo(targetJar, overwrite = true)
                        if (packageName.isNotBlank()) {
                            File(libDir, "config").writeText(packageName.trim())
                        }
                    }

                    if (!targetJar.isFile) {
                        return "Error: classes.jar tidak ditemukan di ${libDir.absolutePath} setelah ekstraksi."
                    }

                    BuiltInLibraries.maybeExtractAndroidJar()
                    BuiltInLibraries.maybeExtractCoreLambdaStubsJar()
                    val buildSettings = BuildSettings(scId)
                    val libraryJars = listOf(
                        BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"),
                        Paths.get(
                            buildSettings.getValue(
                                BuildSettings.SETTING_ANDROID_JAR_PATH,
                                BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
                            )
                        )
                    )
                    D8.run(
                        D8Command.builder()
                            .setIntermediate(true)
                            .setMode(CompilationMode.RELEASE)
                            .addProgramFiles(targetJar.toPath())
                            .addLibraryFiles(libraryJars)
                            .setOutput(libDir.toPath(), OutputMode.DexIndexed)
                            .build()
                    )

                    @Suppress("UNCHECKED_CAST")
                    val mapEntry = LocalLibrariesUtil.createLibraryMap(libName, null) as HashMap<String, Any>
                    val existingIdx = currentList.indexOfFirst { it["name"]?.toString().equals(libName, ignoreCase = true) }
                    if (existingIdx >= 0) currentList[existingIdx] = mapEntry else currentList.add(mapEntry)
                    FileUtil.writeFile(configPath, gson.toJson(currentList))
                    "Berhasil mengimpor '${srcFile.name}' -> local library '$libName' (classes.jar + classes.dex siap) dan mengaktifkannya di proyek #$scId."
                }

                "repair", "repair_missing_dirs", "sanitize" -> {
                    var createdDirs = 0
                    var deletedJunk = 0
                    for (item in currentList) {
                        val resPath = item["resPath"]?.toString().orEmpty()
                        if (resPath.isNotEmpty()) {
                            val resDir = File(resPath)
                            if (!resDir.exists()) {
                                resDir.mkdirs()
                                createdDirs++
                            }
                            resDir.walkTopDown().forEach { f ->
                                if (f.isFile && (f.name.endsWith(".java") || f.name.endsWith(".kt") || (f.name.endsWith(".xml") && f.length() == 0L))) {
                                    if (f.delete()) deletedJunk++
                                }
                            }
                        }
                    }
                    "Repair local libraries selesai: $createdDirs folder res/ dibuat, $deletedJunk file junk (.java/.kt/0-byte xml) di dalam res/ dihapus."
                }

                else -> "Error: action '$action' tidak dikenal. Gunakan: list, enable, disable (bisa pakai library_names='all_missing'), install_maven, import_jar, atau repair."
            }
        }
    }

    /**
     * Reads or updates Sketchware project metadata (`project` file + `BuildSettings` + `ProjectSettings`),
     * and can also delete obsolete screens or events.
     */
    fun configureProjectMetadata(
        context: Context,
        scId: String,
        action: String = "read",
        appName: String = "",
        packageName: String = "",
        workspaceName: String = "",
        versionCode: String = "",
        versionName: String = "",
        colorPrimaryHex: String = "",
        colorPrimaryDarkHex: String = "",
        colorAccentHex: String = "",
        minSdk: String = "",
        targetSdk: String = "",
        javaVersion: String = "",
        iconSourcePath: String = "",
        screenToDelete: String = "",
        eventScreen: String = "",
        eventTargetId: String = "",
        eventNameToDelete: String = ""
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val act = action.trim().lowercase(Locale.US)
            val info = lC.b(scId) ?: HashMap<String, Any>()
            val buildSettings = BuildSettings(scId)

            return when (act) {
                "read", "inspect", "status" -> {
                    val colorHex = { key: String ->
                        val v = yB.a(info, key, 0)
                        if (v != 0) String.format("#%08X", v) else "(default)"
                    }
                    buildString {
                        appendLine("=== PROJECT METADATA & BUILD SETTINGS (sc_id=$scId) ===")
                        appendLine("App Name           : ${yB.c(info, "my_app_name")}")
                        appendLine("Workspace Name     : ${yB.c(info, "my_ws_name")}")
                        appendLine("Package Name       : ${yB.c(info, "my_sc_pkg_name")}")
                        appendLine("Version Name       : ${yB.c(info, "sc_ver_name")}")
                        appendLine("Version Code       : ${yB.c(info, "sc_ver_code")}")
                        appendLine("Custom App Icon    : ${yB.a(info, "custom_icon")}")
                        appendLine("colorPrimary       : ${colorHex("color_primary")}")
                        appendLine("colorPrimaryDark   : ${colorHex("color_primary_dark")}")
                        appendLine("colorAccent        : ${colorHex("color_accent")}")
                        appendLine("minSdkVersion      : ${buildSettings.getValue(BuildSettings.SETTING_MINIMUM_SDK_VERSION, "21")}")
                        appendLine("targetSdkVersion   : ${buildSettings.getValue(BuildSettings.SETTING_TARGET_SDK_VERSION, "28")}")
                        appendLine("javaVersion        : ${buildSettings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_7)}")
                    }.trim()
                }

                "update", "set", "write" -> {
                    val changes = mutableListOf<String>()
                    if (appName.isNotBlank()) {
                        info["my_app_name"] = appName.trim()
                        changes.add("app_name='${appName.trim()}'")
                    }
                    if (workspaceName.isNotBlank()) {
                        info["my_ws_name"] = workspaceName.trim()
                        changes.add("workspace_name='${workspaceName.trim()}'")
                    }
                    if (packageName.isNotBlank()) {
                        info["my_sc_pkg_name"] = packageName.trim()
                        changes.add("package_name='${packageName.trim()}'")
                    }
                    if (versionCode.isNotBlank()) {
                        info["sc_ver_code"] = versionCode.trim()
                        changes.add("version_code='${versionCode.trim()}'")
                    }
                    if (versionName.isNotBlank()) {
                        info["sc_ver_name"] = versionName.trim()
                        changes.add("version_name='${versionName.trim()}'")
                    }
                    if (colorPrimaryHex.isNotBlank()) {
                        info["color_primary"] = parseColorOrDefault(colorPrimaryHex, Color.parseColor("#2196F3"))
                        changes.add("color_primary='$colorPrimaryHex'")
                    }
                    if (colorPrimaryDarkHex.isNotBlank()) {
                        info["color_primary_dark"] = parseColorOrDefault(colorPrimaryDarkHex, Color.parseColor("#1976D2"))
                        changes.add("color_primary_dark='$colorPrimaryDarkHex'")
                    }
                    if (colorAccentHex.isNotBlank()) {
                        info["color_accent"] = parseColorOrDefault(colorAccentHex, Color.parseColor("#FF4081"))
                        changes.add("color_accent='$colorAccentHex'")
                    }
                    if (iconSourcePath.isNotBlank()) {
                        val src = resolveSketchwarePath(scId, iconSourcePath.trim())
                        if (!src.isFile) {
                            return "Error: File ikon tidak ditemukan di ${src.absolutePath}"
                        }
                        val iconDest = File(wq.e() + File.separator + scId + File.separator + "icon.png")
                        iconDest.parentFile?.mkdirs()
                        src.copyTo(iconDest, overwrite = true)
                        info["custom_icon"] = true
                        changes.add("custom_icon=${iconDest.absolutePath}")
                    }
                    info["sketchware_ver"] = GB.d(context.applicationContext)
                    lC.b(scId, info)

                    if (minSdk.isNotBlank()) {
                        buildSettings.setValue(BuildSettings.SETTING_MINIMUM_SDK_VERSION, minSdk.trim())
                        changes.add("min_sdk='${minSdk.trim()}'")
                    }
                    if (targetSdk.isNotBlank()) {
                        buildSettings.setValue(BuildSettings.SETTING_TARGET_SDK_VERSION, targetSdk.trim())
                        changes.add("target_sdk='${targetSdk.trim()}'")
                    }
                    if (javaVersion.isNotBlank()) {
                        buildSettings.setValue(BuildSettings.SETTING_JAVA_VERSION, javaVersion.trim())
                        changes.add("java_version='${javaVersion.trim()}'")
                    }
                    notifyUiChanged(scId, null)
                    "Berhasil memperbarui metadata proyek #$scId: ${changes.joinToString(", ").ifEmpty { "(tidak ada perubahan)" }}"
                }

                "delete_screen" -> {
                    val target = screenToDelete.trim()
                    if (target.isEmpty() || target.equals("main", ignoreCase = true) || target.equals("MainActivity", ignoreCase = true) || target.equals("main.xml", ignoreCase = true)) {
                        return "Error: Masukkan nama screen yang valid (MainActivity tidak boleh dihapus)."
                    }
                    val fileMgr = jC.b(scId)
                    val dataMgr = jC.a(scId)
                    val actMatch = fileMgr.b().orEmpty().firstOrNull {
                        it.fileName.equals(target.removeSuffix(".xml"), ignoreCase = true) ||
                            it.javaName.equals(target, ignoreCase = true) ||
                            it.activityName.equals(target, ignoreCase = true)
                    }
                    val cvMatch = fileMgr.c().orEmpty().firstOrNull {
                        it.fileName.equals(target.removeSuffix(".xml"), ignoreCase = true) ||
                            it.xmlName.equals(target, ignoreCase = true)
                    }
                    if (actMatch == null && cvMatch == null) {
                        return "Error: Screen atau CustomView '$target' tidak ditemukan."
                    }
                    if (actMatch != null) {
                        fileMgr.b()?.remove(actMatch)
                        dataMgr.d(actMatch.xmlName)?.clear()
                    }
                    if (cvMatch != null) {
                        fileMgr.c()?.remove(cvMatch)
                        dataMgr.d(cvMatch.xmlName)?.clear()
                    }
                    fileMgr.l()
                    fileMgr.m()
                    dataMgr.k()
                    notifyUiChanged(scId, null)
                    "Berhasil menghapus screen/custom_view '$target' dari proyek #$scId."
                }

                "delete_event" -> {
                    val pf = resolveProjectFile(scId, eventScreen.ifBlank { "MainActivity" })
                        ?: return "Error: Activity '$eventScreen' tidak ditemukan."
                    val javaName = pf.javaName
                    val dataMgr = jC.a(scId)
                    val (normTarget, normEvent, eventKey) = normalizeEventTarget(eventTargetId, eventNameToDelete)
                    val events = dataMgr.g(javaName)
                    val removed = events?.removeIf {
                        it.targetId.equals(normTarget, ignoreCase = true) && it.eventName.equals(normEvent, ignoreCase = true)
                    } ?: false
                    dataMgr.a(javaName, eventKey, ArrayList())
                    dataMgr.k()
                    notifyUiChanged(scId, javaName)
                    "Event '$eventKey' dihapus (removedBean=$removed) dari $javaName."
                }

                else -> "Error: action '$action' tidak dikenal. Gunakan: read, update, delete_screen, atau delete_event."
            }
        }
    }

    /**
     * Deep project-wide search across all Activities/CustomViews XML views, all Events & MoreBlocks logic blocks,
     * all Variables/Lists/Components, and all custom Java/Kotlin/XML/Asset files.
     */
    fun searchProjectCode(
        scId: String,
        query: String,
        caseSensitive: Boolean = false
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val q = query.trim()
            if (q.isEmpty()) return "Error: query pencarian tidak boleh kosong."
            val fileMgr = jC.b(scId)
            val dataMgr = jC.a(scId)
            val hits = mutableListOf<String>()

            fun matches(text: String?): Boolean {
                if (text.isNullOrEmpty()) return false
                return text.contains(q, ignoreCase = !caseSensitive)
            }

            // 1. Search XML layouts (Activities + Custom Views)
            val allLayouts = buildList {
                fileMgr.b().orEmpty().forEach { add("Activity ${it.javaName} (${it.xmlName})" to it.xmlName) }
                fileMgr.c().orEmpty().forEach { add("CustomView (${it.xmlName})" to it.xmlName) }
            }
            for ((label, xmlName) in allLayouts) {
                val views = dataMgr.d(xmlName).orEmpty()
                for (v in views) {
                    val viewText = v.text?.text.orEmpty()
                    val viewHint = v.text?.hint.orEmpty()
                    val imgRes = v.image?.resName.orEmpty()
                    val bgRes = v.layout?.backgroundResource.orEmpty()
                    if (matches(v.id) || matches(viewText) || matches(viewHint) || matches(imgRes) || matches(bgRes) || matches(v.customView)) {
                        hits.add("[LAYOUT] $label -> view id='${v.id}' (type=${v.type}, text='${viewText.take(40)}', res='$imgRes')")
                    }
                }
            }

            // 2. Search Variables, Lists, Components, Events, MoreBlocks, and BlockBeans
            for (act in fileMgr.b().orEmpty()) {
                val javaName = act.javaName
                for (v in dataMgr.k(javaName).orEmpty()) {
                    if (matches(v.second)) hits.add("[VAR] $javaName -> variable '${v.second}' (type=${v.first})")
                }
                for (l in dataMgr.j(javaName).orEmpty()) {
                    if (matches(l.second)) hits.add("[LIST] $javaName -> list '${l.second}' (type=${l.first})")
                }
                for (c in dataMgr.e(javaName).orEmpty()) {
                    if (matches(c.componentId) || matches(c.param1) || matches(c.param2) || matches(c.param3)) {
                        hits.add("[COMPONENT] $javaName -> id='${c.componentId}' (type=${ComponentBean.getComponentTypeName(c.type)}, p1='${c.param1}')")
                    }
                }

                val eventKeys = mutableListOf<String>()
                eventKeys.add("onCreate_initializeLogic")
                dataMgr.g(javaName).orEmpty().forEach { ev ->
                    eventKeys.add("${ev.targetId}_${ev.eventName}")
                }
                dataMgr.i(javaName).orEmpty().forEach { mb ->
                    val mbName = ReturnMoreblockManager.getMbName(mb.first)
                    eventKeys.add("${mbName}_moreBlock")
                    if (matches(mb.first) || matches(mb.second)) {
                        hits.add("[MOREBLOCK DEF] $javaName -> ${mb.first} : ${mb.second}")
                    }
                }

                for (evKey in eventKeys.distinct()) {
                    val blocks = dataMgr.a(javaName, evKey).orEmpty()
                    for ((idx, b) in blocks.withIndex()) {
                        val paramsStr = b.parameters?.joinToString(" | ").orEmpty()
                        if (matches(b.opCode) || matches(b.spec) || matches(paramsStr)) {
                            hits.add("[BLOCK] $javaName :: $evKey [#$idx id=${b.id} op=${b.opCode}] params=[${paramsStr.take(100)}]")
                        }
                    }
                }
            }

            // 3. Search custom files (files/java, files/resource, files/assets)
            val fpu = FilePathUtil()
            val dirsToScan = listOf(
                "java" to File(fpu.getPathJava(scId)),
                "res" to File(fpu.getPathResource(scId)),
                "assets" to File(fpu.getPathAssets(scId))
            )
            for ((tag, rootDir) in dirsToScan) {
                if (!rootDir.isDirectory) continue
                rootDir.walkTopDown().maxDepth(6).forEach { file ->
                    if (!file.isFile || file.length() > 512_000L) return@forEach
                    val ext = file.extension.lowercase(Locale.US)
                    if (ext !in setOf("java", "kt", "xml", "json", "txt", "html", "js", "css", "pro", "gradle")) return@forEach
                    try {
                        val rel = file.relativeTo(rootDir).path.replace('\\', '/')
                        if (matches(rel)) {
                            hits.add("[FILE:$tag] $rel (nama file cocok)")
                        }
                        file.useLines { lines ->
                            lines.forEachIndexed { lineIdx, line ->
                                if (matches(line) && hits.size < 150) {
                                    hits.add("[FILE:$tag] $rel:${lineIdx + 1}: ${line.trim().take(120)}")
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            }

            return buildString {
                appendLine("=== HASIL PENCARIAN PROYEK #$scId UNTUK '$q' (${hits.size} temuan) ===")
                if (hits.isEmpty()) {
                    appendLine("(Tidak ditemukan kecocokan di layout XML, blok logika, variabel, komponen, maupun file kustom)")
                } else {
                    hits.take(120).forEach { appendLine("- $it") }
                    if (hits.size > 120) {
                        appendLine("... dan ${hits.size - 120} temuan lainnya.")
                    }
                }
            }.trim()
        }
    }

    /**
     * Manages Sketchware project resources:
     * - Registered images/drawables (`jC.d(scId).b`), including direct creation of Android VectorDrawable `.xml` icons!
     * - Registered sounds (`jC.d(scId).c`) and fonts (`jC.d(scId).d`)
     * - Custom Android `res/` XML files (`values/colors.xml`, `values/styles.xml`, `drawable/`, `anim/`, `xml/`)
     * - Project `assets/` files
     */
    fun manageProjectResources(
        scId: String,
        action: String,
        resourceType: String = "image",
        resourceName: String = "",
        content: String = "",
        sourcePath: String = ""
    ): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val resMgr = jC.d(scId)
            val dataMgr = jC.a(scId)
            val fpu = FilePathUtil()
            val act = action.trim().lowercase(Locale.US)
            val rType = resourceType.trim().lowercase(Locale.US)

            return when (act) {
                "list", "ls", "status" -> {
                    val images = resMgr.b.orEmpty()
                    val sounds = resMgr.c.orEmpty()
                    val fonts = resMgr.d.orEmpty()
                    val assetsDir = File(fpu.getPathAssets(scId))
                    val resDir = File(fpu.getPathResource(scId))
                    val assetFiles = if (assetsDir.isDirectory) {
                        assetsDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(assetsDir).path.replace('\\', '/') }.toList()
                    } else emptyList()
                    val customResFiles = if (resDir.isDirectory) {
                        resDir.walkTopDown()
                            .filter { it.isFile && !it.parentFile.name.equals("images", true) && !it.parentFile.name.equals("sounds", true) && !it.parentFile.name.equals("fonts", true) }
                            .map { it.relativeTo(resDir).path.replace('\\', '/') }
                            .toList()
                    } else emptyList()

                    buildString {
                        appendLine("=== PROJECT RESOURCES (sc_id=$scId) ===")
                        appendLine("Images / Drawables (${images.size}): ${if (images.isEmpty()) "(kosong)" else images.joinToString(", ") { "${it.resName} (${it.resFullName})" }}")
                        appendLine("Sounds (${sounds.size})            : ${if (sounds.isEmpty()) "(kosong)" else sounds.joinToString(", ") { "${it.resName} (${it.resFullName})" }}")
                        appendLine("Fonts (${fonts.size})             : ${if (fonts.isEmpty()) "(kosong)" else fonts.joinToString(", ") { "${it.resName} (${it.resFullName})" }}")
                        appendLine("Assets (${assetFiles.size})            : ${if (assetFiles.isEmpty()) "(kosong)" else assetFiles.joinToString(", ")}")
                        appendLine("Custom res/ XML (${customResFiles.size})   : ${if (customResFiles.isEmpty()) "(kosong)" else customResFiles.joinToString(", ")}")
                    }.trim()
                }

                "add_vector", "add_vector_drawable", "create_vector" -> {
                    val cleanName = resourceName.trim().removeSuffix(".xml").lowercase(Locale.US)
                    if (!cleanName.matches(Regex("[a-z][a-z0-9_]*"))) {
                        return "Error: resource_name untuk vector drawable harus lowercase a-z0-9_ diawali huruf (contoh: 'ic_send_white')."
                    }
                    if (content.isBlank() || !content.contains("<vector")) {
                        return "Error: parameter 'content' harus berisi XML Android <vector ...>...</vector> yang valid."
                    }
                    val imgDir = File(resMgr.l()).apply { mkdirs() }
                    val targetXml = File(imgDir, "$cleanName.xml")
                    targetXml.writeText(content.trim())

                    val list = ArrayList<ProjectResourceBean>(resMgr.b.orEmpty())
                    list.removeIf { it.resName.equals(cleanName, ignoreCase = true) }
                    list.add(ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, cleanName, "$cleanName.xml"))
                    resMgr.b(list)
                    resMgr.y()
                    dataMgr.k()
                    notifyUiChanged(scId, null)
                    "Vector drawable '$cleanName' berhasil disimpan di ${targetXml.absolutePath} dan didaftarkan ke ProjectResourceBean! Sekarang bisa langsung dipakai di ImageView (imageResource='$cleanName') atau backgroundResource."
                }

                "import", "add_file" -> {
                    val cleanName = resourceName.trim().substringBeforeLast('.').lowercase(Locale.US)
                    if (sourcePath.isBlank()) {
                        return "Error: parameter 'source_path' wajib diisi untuk action='import'."
                    }
                    val srcFile = resolveSketchwarePath(scId, sourcePath.trim())
                    if (!srcFile.isFile) {
                        return "Error: File sumber tidak ditemukan: ${srcFile.absolutePath}"
                    }
                    when (rType) {
                        "image", "drawable", "icon" -> {
                            if (!cleanName.matches(Regex("[a-z][a-z0-9_]*"))) {
                                return "Error: resource_name untuk image harus lowercase a-z0-9_ diawali huruf."
                            }
                            val ext = when {
                                srcFile.name.endsWith(".9.png", true) -> ".9.png"
                                srcFile.name.endsWith(".xml", true) -> ".xml"
                                else -> ".png"
                            }
                            val imgDir = File(resMgr.l()).apply { mkdirs() }
                            val destFile = File(imgDir, "$cleanName$ext")
                            srcFile.copyTo(destFile, overwrite = true)
                            val list = ArrayList<ProjectResourceBean>(resMgr.b.orEmpty())
                            list.removeIf { it.resName.equals(cleanName, ignoreCase = true) }
                            list.add(ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, cleanName, "$cleanName$ext"))
                            resMgr.b(list)
                            resMgr.y()
                            dataMgr.k()
                            notifyUiChanged(scId, null)
                            "Image/drawable '$cleanName$ext' berhasil diimpor dan didaftarkan ke proyek #$scId."
                        }
                        "sound", "audio", "raw" -> {
                            if (!cleanName.matches(Regex("[a-z][a-z0-9_]*"))) {
                                return "Error: resource_name untuk sound harus lowercase a-z0-9_ diawali huruf."
                            }
                            val ext = "." + srcFile.extension.lowercase(Locale.US).ifEmpty { "mp3" }
                            val soundDir = File(resMgr.o()).apply { mkdirs() }
                            val destFile = File(soundDir, "$cleanName$ext")
                            srcFile.copyTo(destFile, overwrite = true)
                            val list = ArrayList<ProjectResourceBean>(resMgr.c.orEmpty())
                            list.removeIf { it.resName.equals(cleanName, ignoreCase = true) }
                            list.add(ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, cleanName, "$cleanName$ext"))
                            resMgr.c(list)
                            resMgr.y()
                            dataMgr.c(resMgr)
                            dataMgr.k()
                            notifyUiChanged(scId, null)
                            "Sound '$cleanName$ext' berhasil diimpor dan didaftarkan ke proyek #$scId."
                        }
                        "font", "ttf", "otf" -> {
                            if (!cleanName.matches(Regex("[a-z][a-z0-9_]*"))) {
                                return "Error: resource_name untuk font harus lowercase a-z0-9_ diawali huruf."
                            }
                            val ext = "." + srcFile.extension.lowercase(Locale.US).ifEmpty { "ttf" }
                            val fontDir = File(resMgr.j()).apply { mkdirs() }
                            val destFile = File(fontDir, "$cleanName$ext")
                            srcFile.copyTo(destFile, overwrite = true)
                            val list = ArrayList<ProjectResourceBean>(resMgr.d.orEmpty())
                            list.removeIf { it.resName.equals(cleanName, ignoreCase = true) }
                            list.add(ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, cleanName, "$cleanName$ext"))
                            resMgr.a(list)
                            resMgr.y()
                            dataMgr.a(resMgr)
                            dataMgr.k()
                            notifyUiChanged(scId, null)
                            "Font '$cleanName$ext' berhasil diimpor dan didaftarkan ke proyek #$scId."
                        }
                        "asset", "assets" -> {
                            val relName = resourceName.trim().ifEmpty { srcFile.name }
                            val destFile = File(fpu.getPathAssets(scId), relName)
                            destFile.parentFile?.mkdirs()
                            srcFile.copyTo(destFile, overwrite = true)
                            "Asset '$relName' berhasil disalin ke ${destFile.absolutePath}."
                        }
                        else -> "Error: resource_type '$resourceType' tidak dikenal untuk import. Gunakan: image, sound, font, atau asset."
                    }
                }

                "write_res_xml", "write_custom_res", "write_xml" -> {
                    val relPath = resourceName.trim().removePrefix("res/").removePrefix("/")
                    if (relPath.isEmpty() || !relPath.contains("/")) {
                        return "Error: resource_name untuk write_res_xml harus menyertakan subfolder res, contoh: 'drawable/bg_rounded.xml', 'values/colors.xml', atau 'xml/file_paths.xml'."
                    }
                    val destFile = File(fpu.getPathResource(scId), relPath)
                    destFile.parentFile?.mkdirs()
                    destFile.writeText(content)
                    "Custom Android resource XML berhasil ditulis ke ${destFile.absolutePath} (${destFile.length()} bytes)."
                }

                "write_asset" -> {
                    val relPath = resourceName.trim().removePrefix("assets/").removePrefix("/")
                    if (relPath.isEmpty()) {
                        return "Error: resource_name wajib diisi (contoh: 'data/config.json' atau 'index.html')."
                    }
                    val destFile = File(fpu.getPathAssets(scId), relPath)
                    destFile.parentFile?.mkdirs()
                    destFile.writeText(content)
                    "File asset berhasil ditulis ke ${destFile.absolutePath} (${destFile.length()} bytes)."
                }

                "delete", "remove" -> {
                    val cleanName = resourceName.trim()
                    when (rType) {
                        "image", "drawable", "vector" -> {
                            val list = ArrayList<ProjectResourceBean>(resMgr.b.orEmpty())
                            val target = list.firstOrNull { it.resName.equals(cleanName, ignoreCase = true) }
                            if (target != null) {
                                File(resMgr.l(), target.resFullName).delete()
                                list.remove(target)
                                resMgr.b(list)
                                resMgr.y()
                                dataMgr.k()
                                notifyUiChanged(scId, null)
                                "Image/drawable '$cleanName' berhasil dihapus."
                            } else "Image/drawable '$cleanName' tidak ditemukan."
                        }
                        "sound" -> {
                            val list = ArrayList<ProjectResourceBean>(resMgr.c.orEmpty())
                            val target = list.firstOrNull { it.resName.equals(cleanName, ignoreCase = true) }
                            if (target != null) {
                                File(resMgr.o(), target.resFullName).delete()
                                list.remove(target)
                                resMgr.c(list)
                                resMgr.y()
                                dataMgr.k()
                                "Sound '$cleanName' berhasil dihapus."
                            } else "Sound '$cleanName' tidak ditemukan."
                        }
                        "font" -> {
                            val list = ArrayList<ProjectResourceBean>(resMgr.d.orEmpty())
                            val target = list.firstOrNull { it.resName.equals(cleanName, ignoreCase = true) }
                            if (target != null) {
                                File(resMgr.j(), target.resFullName).delete()
                                list.remove(target)
                                resMgr.a(list)
                                resMgr.y()
                                dataMgr.k()
                                "Font '$cleanName' berhasil dihapus."
                            } else "Font '$cleanName' tidak ditemukan."
                        }
                        "asset", "assets" -> {
                            val f = File(fpu.getPathAssets(scId), cleanName)
                            if (f.exists() && f.delete()) "Asset '$cleanName' berhasil dihapus." else "Asset '$cleanName' tidak ditemukan."
                        }
                        "res_xml", "custom_res" -> {
                            val f = File(fpu.getPathResource(scId), cleanName.removePrefix("res/"))
                            if (f.exists() && f.delete()) "Custom res '$cleanName' berhasil dihapus." else "Custom res '$cleanName' tidak ditemukan."
                        }
                        else -> "Error: resource_type '$resourceType' tidak dikenal."
                    }
                }

                else -> "Error: action '$action' tidak dikenal. Gunakan: list, add_vector_drawable, import, write_res_xml, write_asset, atau delete."
            }
        }
    }

    /**
     * Generates and returns the complete Java or XML source code for a given file (e.g. `"MainActivity.java"` or `"main.xml"`)
     * using Sketchware's native [yq.getFileSrc] code generator.
     */
    fun previewGeneratedFileSource(context: Context, scId: String, fileName: String): String {
        synchronized(lock) {
            ensureProjectLoaded(scId)
            val projectInfo = lC.b(scId) ?: HashMap()
            val q = yq(context.applicationContext, wq.d(scId), projectInfo)
            val src = q.getFileSrc(fileName.trim(), jC.b(scId), jC.a(scId), jC.c(scId))
            if (src.isNullOrEmpty()) {
                return "// Tidak dapat men-generate source untuk '$fileName'. Pastikan nama file benar (contoh: MainActivity.java atau main.xml)."
            }
            return src
        }
    }

    private fun parseColorOrDefault(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            fallback
        }
    }
}
