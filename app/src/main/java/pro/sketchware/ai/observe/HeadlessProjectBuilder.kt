package pro.sketchware.ai.observe

import a.a.a.ProjectBuilder
import a.a.a.jC
import a.a.a.lC
import a.a.a.wq
import a.a.a.yB
import a.a.a.yq
import a.a.a.zy
import android.content.Context
import android.util.Log
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge
import mod.hey.studios.project.proguard.ProguardHandler
import mod.hey.studios.project.stringfog.StringfogHandler
import mod.jbk.build.BuildProgressReceiver
import mod.jbk.build.BuiltInLibraries
import mod.jbk.diagnostic.CompileErrorSaver
import mod.jbk.diagnostic.MissingFileException
import pro.sketchware.ai.project.ProjectGateway
import pro.sketchware.utility.FileUtil
import java.io.File

data class BuildResult(
    val success: Boolean,
    val stageReached: String,
    val apkPath: String?,
    val errorSummary: String?,
    val sourceSnippetWithContext: String?,
    val durationMs: Long
)

/**
 * Runs Sketchware Pro's native build pipeline headlessly (Resource Generation -> AAPT2 ->
 * ViewBinding -> Kotlin -> ECJ Java Compiler -> StringFog -> ProGuard -> D8/DX -> APK Packaging -> Signing)
 * and parses any compiler errors with line-level Java source context so the AI Agent can self-heal.
 */
object HeadlessProjectBuilder {

    fun buildProject(
        context: Context,
        scId: String,
        compileOnlyCheck: Boolean = false,
        onStageProgress: ((String, Int) -> Unit)? = null
    ): BuildResult {
        val startTime = System.currentTimeMillis()
        val appCtx = context.applicationContext
        ProjectGateway.ensureProjectLoaded(scId)
        ProjectGateway.saveProjectToDisk(appCtx, scId)

        var lastStage = "Initializing"
        val progressReceiver = BuildProgressReceiver { progress, step ->
            lastStage = "$progress (step $step/20)"
            onStageProgress?.invoke(progress, step)
        }

        val projectInfo = lC.b(scId)
            ?: return BuildResult(
                success = false,
                stageReached = "Project Metadata",
                apkPath = null,
                errorSummary = "Metadata proyek sc_id=$scId tidak ditemukan.",
                sourceSnippetWithContext = null,
                durationMs = System.currentTimeMillis() - startTime
            )

        val q = yq(appCtx, wq.d(scId), projectInfo)

        return try {
            progressReceiver.onProgress("Deleting temporary files...", 1)
            FileUtil.deleteFile(q.projectMyscPath)

            q.c(appCtx)
            q.a()
            q.a(appCtx, wq.e("600"))
            if (yB.a(lC.b(scId), "custom_icon")) {
                q.aa(wq.e() + File.separator + scId + File.separator + "mipmaps")
                if (yB.a(lC.b(scId), "isIconAdaptive", false)) {
                    q.createLauncherIconXml(
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                        <background android:drawable="@mipmap/ic_launcher_background"/>
                        <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                        <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                        </adaptive-icon>
                        """.trimIndent()
                    )
                } else {
                    q.a(wq.e() + File.separator + scId + File.separator + "icon.png")
                }
            }

            progressReceiver.onProgress("Generating source code...", 2)
            val resMgr = jC.d(scId)
            resMgr.b(q.resDirectoryPath + File.separator + "drawable-xhdpi")
            resMgr.c(q.resDirectoryPath + File.separator + "raw")
            resMgr.a(q.assetsPath + File.separator + "fonts")

            val builder = ProjectBuilder(progressReceiver, appCtx, q)
            val fileManager = jC.b(scId)
            val dataManager = jC.a(scId)
            val libraryManager = jC.c(scId)

            q.a(libraryManager, fileManager, dataManager)
            builder.buildBuiltInLibraryInformation()
            q.b(fileManager, dataManager, libraryManager, builder.builtInLibraryManager)
            q.f()
            q.e()

            builder.maybeExtractAapt2()

            progressReceiver.onProgress("Extracting built-in libraries...", 3)
            BuiltInLibraries.extractCompileAssets(progressReceiver)

            progressReceiver.onProgress("AAPT2 is running...", 8)
            builder.compileResources()

            progressReceiver.onProgress("Generating view binding...", 11)
            builder.generateViewBinding()

            KotlinCompilerBridge.compileKotlinCodeIfPossible(progressReceiver, builder)

            progressReceiver.onProgress("Java is compiling...", 13)
            builder.compileJavaCode()

            if (compileOnlyCheck) {
                return BuildResult(
                    success = true,
                    stageReached = "Java & Resource Compilation Passed (Fast Verification)",
                    apkPath = null,
                    errorSummary = null,
                    sourceSnippetWithContext = null,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            val stringfogHandler = StringfogHandler(scId)
            stringfogHandler.start(progressReceiver, builder)

            val proguardHandler = ProguardHandler(scId)
            proguardHandler.start(progressReceiver, builder)

            progressReceiver.onProgress(builder.dxRunningText, 17)
            builder.createDexFilesFromClasses()

            progressReceiver.onProgress("Merging DEX files...", 18)
            builder.getDexFilesReady()

            progressReceiver.onProgress("Building APK...", 19)
            builder.buildApk()

            progressReceiver.onProgress("Signing APK...", 20)
            builder.signDebugApk()

            BuildResult(
                success = true,
                stageReached = "Signed Debug APK Ready (20/20)",
                apkPath = q.finalToInstallApkPath,
                errorSummary = null,
                sourceSnippetWithContext = null,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: MissingFileException) {
            if (e.isMissingDirectory) {
                e.missingFile.mkdirs()
            }
            val msg = "MissingFileException: ${e.missingFile.absolutePath} (isDirectory=${e.isMissingDirectory})"
            CompileErrorSaver(scId).writeLogsToFile(msg)
            BuildResult(
                success = false,
                stageReached = lastStage,
                apkPath = null,
                errorSummary = msg,
                sourceSnippetWithContext = null,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: zy) {
            val rawError = e.message.orEmpty()
            CompileErrorSaver(scId).writeLogsToFile(rawError)
            val contextSnippet = extractErrorSourceContext(rawError)
            BuildResult(
                success = false,
                stageReached = lastStage,
                apkPath = null,
                errorSummary = rawError,
                sourceSnippetWithContext = contextSnippet,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (tr: Throwable) {
            val trace = Log.getStackTraceString(tr)
            CompileErrorSaver(scId).writeLogsToFile(trace)
            val contextSnippet = extractErrorSourceContext(trace)
            BuildResult(
                success = false,
                stageReached = lastStage,
                apkPath = null,
                errorSummary = trace,
                sourceSnippetWithContext = contextSnippet,
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Parses ECJ / AAPT2 compiler error paths (e.g. `.../MainActivity.java:84: error: ...` or
     * `1. ERROR in /.../MainActivity.java (at line 84)`) and extracts the surrounding lines of code.
     */
    private fun extractErrorSourceContext(errorLog: String): String? {
        val snippets = mutableListOf<String>()

        // Match ECJ format: ERROR in /path/to/File.java (at line 123)
        val ecjRegex = Regex("""ERROR in ([^\s()]+\.(?:java|kt|xml)) \(at line (\d+)\)""")
        // Match javac/aapt2 format: /path/to/File.java:123:
        val colonRegex = Regex("""([/\\][^\s:]+\.(?:java|kt|xml)):(\d+):""")

        val matches = mutableListOf<Pair<String, Int>>()
        for (m in ecjRegex.findAll(errorLog)) {
            val path = m.groupValues[1]
            val line = m.groupValues[2].toIntOrNull() ?: continue
            matches.add(path to line)
        }
        for (m in colonRegex.findAll(errorLog)) {
            val path = m.groupValues[1]
            val line = m.groupValues[2].toIntOrNull() ?: continue
            if (matches.none { it.first == path && it.second == line }) {
                matches.add(path to line)
            }
        }

        for ((filePath, lineNum) in matches.take(3)) {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                val lines = file.readLines()
                val from = (lineNum - 6).coerceAtLeast(1)
                val to = (lineNum + 6).coerceAtMost(lines.size)
                val sb = StringBuilder()
                sb.appendLine("--- Source Context: ${file.name} (around line $lineNum) ---")
                for (i in from..to) {
                    val marker = if (i == lineNum) ">> " else "   "
                    sb.appendLine("$marker$i: ${lines[i - 1]}")
                }
                snippets.add(sb.toString().trimEnd())
            }
        }

        return if (snippets.isEmpty()) null else snippets.joinToString("\n\n")
    }
}
