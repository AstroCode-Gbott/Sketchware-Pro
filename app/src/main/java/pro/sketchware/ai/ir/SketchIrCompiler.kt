package pro.sketchware.ai.ir

import a.a.a.Fx
import a.a.a.jq
import android.content.Context
import com.besome.sketch.beans.BlockBean
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicInteger

/**
 * Compiles high-level block definitions (either JSON block trees or Block-Script DSL)
 * into genuine Sketchware [BlockBean] graphs with properly linked IDs, `nextBlock`,
 * `subStack1`, `subStack2`, and `@id` nested expression blocks.
 *
 * Also decompiles [BlockBean] lists back to readable Block-Script + generated Java via [Fx].
 */
object SketchIrCompiler {

    data class IrBlockNode(
        val opCode: String,
        val type: String? = null,
        val typeName: String? = null,
        val spec: String? = null,
        val params: List<IrParam> = emptyList(),
        val sub1: List<IrBlockNode> = emptyList(),
        val sub2: List<IrBlockNode> = emptyList()
    )

    sealed class IrParam {
        data class Literal(val value: String) : IrParam()
        data class Expr(val node: IrBlockNode) : IrParam()
    }

    /**
     * Compiles either a JSON array string (`[{"opCode": ...}]`) or a line-oriented Block-Script
     * into a flat `ArrayList<BlockBean>` that Sketchware's `eC`, `LogicEditorActivity`, and `Fx`
     * can render and compile to Java.
     */
    fun compile(context: Context?, input: String, startId: Int = 10): ArrayList<BlockBean> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ArrayList()

        val rootNodes: List<IrBlockNode> = if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJsonInput(trimmed)
        } else {
            parseBlockScript(trimmed)
        }

        val idCounter = AtomicInteger(startId)
        val result = ArrayList<BlockBean>()
        emitStatementChain(context, rootNodes, idCounter, result)
        return result
    }

    private fun emitStatementChain(
        context: Context?,
        nodes: List<IrBlockNode>,
        idCounter: AtomicInteger,
        out: ArrayList<BlockBean>
    ): Int {
        if (nodes.isEmpty()) return -1
        var firstBlockId = -1
        var prevBlock: BlockBean? = null

        for (node in nodes) {
            val currentId = idCounter.getAndIncrement()
            if (firstBlockId == -1) {
                firstBlockId = currentId
            }
            if (prevBlock != null) {
                prevBlock.nextBlock = currentId
            }

            val bean = buildBlockBean(context, currentId, node, idCounter, out)
            prevBlock = bean
        }
        return firstBlockId
    }

    private fun buildBlockBean(
        context: Context?,
        blockId: Int,
        node: IrBlockNode,
        idCounter: AtomicInteger,
        out: ArrayList<BlockBean>
    ): BlockBean {
        // Special handling for getVar / getArg where spec is the variable name
        val effectiveType = when {
            !node.type.isNullOrEmpty() -> node.type
            node.opCode == "getVar" || node.opCode == "getArg" -> "s"
            node.sub2.isNotEmpty() || node.opCode == "ifElse" || node.opCode == "tryCatch" -> "e"
            node.sub1.isNotEmpty() -> "c"
            else -> null
        }

        val effectiveSpec = when {
            !node.spec.isNullOrEmpty() && !(node.opCode == "definedFunc" && node.spec.trim().startsWith("%")) -> node.spec
            (node.opCode == "getVar" || node.opCode == "getArg") && node.params.isNotEmpty() -> {
                (node.params.first() as? IrParam.Literal)?.value ?: node.opCode
            }
            node.opCode == "definedFunc" && node.params.isNotEmpty() -> {
                val firstVal = (node.params.first() as? IrParam.Literal)?.value?.trim().orEmpty()
                if (firstVal.isEmpty()) {
                    "definedFunc"
                } else if (firstVal.contains(" ") || node.params.size == 1) {
                    firstVal
                } else {
                    val argSpecs = node.params.drop(1).mapIndexed { idx, p ->
                        val ph = when (p) {
                            is IrParam.Expr -> when (p.node.type) {
                                "b" -> "%b"
                                "d" -> "%d"
                                else -> "%s"
                            }
                            is IrParam.Literal -> {
                                val v = p.value.trim()
                                if (v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true)) "%b"
                                else if (v.toDoubleOrNull() != null) "%d"
                                else "%s"
                            }
                        }
                        "$ph.arg${idx + 1}"
                    }
                    "$firstVal ${argSpecs.joinToString(" ")}"
                }
            }
            else -> null
        }

        val sig = BlockCatalog.resolve(context, node.opCode, effectiveType, effectiveSpec)
        val bean = BlockBean(
            blockId.toString(),
            sig.spec,
            sig.type,
            node.typeName ?: sig.typeName,
            node.opCode
        )
        bean.color = sig.color

        // Add the parent block to `out` BEFORE its children/parameters so root is first in list
        out.add(bean)

        val consumesFirstParamAsSpec = node.opCode == "definedFunc" &&
            (node.spec.isNullOrEmpty() || node.spec.trim().startsWith("%")) &&
            node.params.isNotEmpty()

        val isZeroParamSpec = (node.opCode == "getVar" || node.opCode == "getArg" || node.opCode == "getResStr") ||
            (node.opCode == "definedFunc" && !sig.spec.contains("%"))

        val rawParams = when {
            isZeroParamSpec -> emptyList()
            consumesFirstParamAsSpec -> node.params.drop(1)
            else -> node.params
        }

        val resolvedParams = ArrayList<String>()
        for (param in rawParams) {
            when (param) {
                is IrParam.Literal -> resolvedParams.add(param.value)
                is IrParam.Expr -> {
                    val exprId = idCounter.getAndIncrement()
                    buildBlockBean(context, exprId, param.node, idCounter, out)
                    resolvedParams.add("@$exprId")
                }
            }
        }

        // Pad any missing parameters required by spec so Fx never throws IndexOutOfBoundsException
        val expectedParamCount = if (isZeroParamSpec) 0 else sig.paramPlaceholders.size
        while (resolvedParams.size < expectedParamCount) {
            resolvedParams.add("")
        }
        bean.parameters = if (isZeroParamSpec) ArrayList() else resolvedParams

        if (node.sub1.isNotEmpty()) {
            bean.subStack1 = emitStatementChain(context, node.sub1, idCounter, out)
        }
        if (node.sub2.isNotEmpty()) {
            bean.subStack2 = emitStatementChain(context, node.sub2, idCounter, out)
        }

        // Rebuild classInfo after setting spec and parameters
        bean.classInfo
        bean.paramClassInfo
        return bean
    }

    private fun parseJsonInput(jsonStr: String): List<IrBlockNode> {
        val el = JsonParser.parseString(jsonStr)
        return when {
            el.isJsonArray -> el.asJsonArray.mapNotNull { parseJsonNode(it) }
            el.isJsonObject -> {
                val obj = el.asJsonObject
                if (obj.has("blocks") && obj.get("blocks").isJsonArray) {
                    obj.getAsJsonArray("blocks").mapNotNull { parseJsonNode(it) }
                } else {
                    listOfNotNull(parseJsonNode(obj))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseJsonNode(el: JsonElement): IrBlockNode? {
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val opCode = obj.get("opCode")?.takeIf { !it.isJsonNull }?.asString
            ?: obj.get("op")?.takeIf { !it.isJsonNull }?.asString
            ?: return null

        val type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString
        val typeName = obj.get("typeName")?.takeIf { !it.isJsonNull }?.asString
        val spec = obj.get("spec")?.takeIf { !it.isJsonNull }?.asString

        val paramsArr: JsonArray? = when {
            obj.has("params") && obj.get("params").isJsonArray -> obj.getAsJsonArray("params")
            obj.has("parameters") && obj.get("parameters").isJsonArray -> obj.getAsJsonArray("parameters")
            obj.has("args") && obj.get("args").isJsonArray -> obj.getAsJsonArray("args")
            else -> null
        }

        val params = mutableListOf<IrParam>()
        if (paramsArr != null) {
            for (p in paramsArr) {
                when {
                    p.isJsonObject -> {
                        val nested = parseJsonNode(p)
                        if (nested != null) {
                            params.add(IrParam.Expr(nested))
                        } else {
                            params.add(IrParam.Literal(""))
                        }
                    }
                    p.isJsonPrimitive -> params.add(IrParam.Literal(p.asString))
                    else -> params.add(IrParam.Literal(""))
                }
            }
        }

        val sub1Arr = when {
            obj.has("sub1") && obj.get("sub1").isJsonArray -> obj.getAsJsonArray("sub1")
            obj.has("subStack1") && obj.get("subStack1").isJsonArray -> obj.getAsJsonArray("subStack1")
            obj.has("then") && obj.get("then").isJsonArray -> obj.getAsJsonArray("then")
            else -> null
        }
        val sub2Arr = when {
            obj.has("sub2") && obj.get("sub2").isJsonArray -> obj.getAsJsonArray("sub2")
            obj.has("subStack2") && obj.get("subStack2").isJsonArray -> obj.getAsJsonArray("subStack2")
            obj.has("else") && obj.get("else").isJsonArray -> obj.getAsJsonArray("else")
            else -> null
        }

        val sub1 = sub1Arr?.mapNotNull { parseJsonNode(it) }.orEmpty()
        val sub2 = sub2Arr?.mapNotNull { parseJsonNode(it) }.orEmpty()

        return IrBlockNode(
            opCode = opCode,
            type = type,
            typeName = typeName,
            spec = spec,
            params = params,
            sub1 = sub1,
            sub2 = sub2
        )
    }

    /**
     * Parses a human/LLM-friendly Block-Script format:
     * Example:
     * ```
     * setText("tv_title", "Halo Sketchware AI!")
     * if (stringEquals([getText("et_name")], "")) {
     *   doToast("Nama tidak boleh kosong")
     * } else {
     *   doToast([stringJoin("Halo, ", [getText("et_name")])])
     * }
     * ```
     */
    private fun parseBlockScript(script: String): List<IrBlockNode> {
        val lines = script.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("#") }

        var index = 0
        fun parseBlockList(): List<IrBlockNode> {
            val nodes = mutableListOf<IrBlockNode>()
            while (index < lines.size) {
                val line = lines[index]
                if (line == "}" || line.startsWith("} else")) {
                    break
                }
                index++
                val hasOpenBrace = line.endsWith("{")
                val cleanLine = if (hasOpenBrace) line.removeSuffix("{").trim() else line
                val callNode = parseCallExpression(cleanLine)

                if (hasOpenBrace) {
                    val sub1 = parseBlockList()
                    var sub2 = emptyList<IrBlockNode>()
                    if (index < lines.size) {
                        val nextLine = lines[index]
                        if (nextLine.startsWith("} else") || nextLine == "else {") {
                            index++
                            sub2 = parseBlockList()
                        }
                        if (index < lines.size && lines[index] == "}") {
                            index++
                        }
                    }
                    val actualOp = if (callNode.opCode == "if" && sub2.isNotEmpty()) "ifElse" else callNode.opCode
                    nodes.add(callNode.copy(opCode = actualOp, sub1 = sub1, sub2 = sub2))
                } else {
                    nodes.add(callNode)
                }
            }
            return nodes
        }

        return parseBlockList()
    }

    private fun parseCallExpression(expr: String): IrBlockNode {
        val trimmed = expr.trim().removePrefix("[").removeSuffix("]").trim()
        val openParen = trimmed.indexOf('(')
        val closeParen = trimmed.lastIndexOf(')')
        if (openParen == -1 || closeParen == -1 || closeParen < openParen) {
            return IrBlockNode(opCode = trimmed)
        }
        val rawOp = trimmed.substring(0, openParen).trim()
        val opParts = rawOp.split(":")
        val opCode = when (opParts[0]) {
            "raw_java", "java" -> "addSourceDirectly"
            else -> opParts[0]
        }
        val explicitType = opParts.getOrNull(1)

        val argsStr = trimmed.substring(openParen + 1, closeParen).trim()
        val argTokens = splitTopLevelArgs(argsStr)
        val params = argTokens.map { token ->
            val t = token.trim()
            if ((t.startsWith("[") && t.endsWith("]")) || (t.contains("(") && t.endsWith(")") && !t.startsWith("\"") && !t.startsWith("'"))) {
                IrParam.Expr(parseCallExpression(t))
            } else {
                IrParam.Literal(unquote(t))
            }
        }
        return IrBlockNode(opCode = opCode, type = explicitType, params = params)
    }

    private fun splitTopLevelArgs(argsStr: String): List<String> {
        if (argsStr.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depthParen = 0
        var depthBracket = 0
        var inQuotes = false
        var quoteChar = '"'
        var i = 0
        while (i < argsStr.length) {
            val c = argsStr[i]
            if (inQuotes) {
                if (c == '\\' && i + 1 < argsStr.length) {
                    current.append(c).append(argsStr[i + 1])
                    i += 2
                    continue
                } else if (c == quoteChar) {
                    inQuotes = false
                }
                current.append(c)
            } else {
                when (c) {
                    '"', '\'' -> {
                        inQuotes = true
                        quoteChar = c
                        current.append(c)
                    }
                    '(' -> {
                        depthParen++
                        current.append(c)
                    }
                    ')' -> {
                        depthParen--
                        current.append(c)
                    }
                    '[' -> {
                        depthBracket++
                        current.append(c)
                    }
                    ']' -> {
                        depthBracket--
                        current.append(c)
                    }
                    ',' -> {
                        if (depthParen == 0 && depthBracket == 0) {
                            result.add(current.toString())
                            current.clear()
                        } else {
                            current.append(c)
                        }
                    }
                    else -> current.append(c)
                }
            }
            i++
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            return t.substring(1, t.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\")
        }
        return t
    }

    /**
     * Generates a readable representation of an existing event's [BlockBean] list
     * along with the exact Java source code generated by Sketchware's [Fx] block compiler.
     */
    fun decompileEventBlocks(
        activityName: String,
        blocks: List<BlockBean>?,
        isViewBindingEnabled: Boolean = false
    ): String {
        if (blocks.isNullOrEmpty()) {
            return "// (Tidak ada blok di dalam event ini)"
        }
        val byId = blocks.associateBy { it.id }
        val referencedIds = mutableSetOf<String>()
        for (b in blocks) {
            if (b.nextBlock != -1) referencedIds.add(b.nextBlock.toString())
            if (b.subStack1 != -1) referencedIds.add(b.subStack1.toString())
            if (b.subStack2 != -1) referencedIds.add(b.subStack2.toString())
            b.parameters?.forEach { p ->
                if (p != null && p.startsWith("@")) {
                    referencedIds.add(p.removePrefix("@"))
                }
            }
        }
        val rootBlock = blocks.firstOrNull { !referencedIds.contains(it.id) } ?: blocks.first()

        val dslBuilder = StringBuilder()
        dslBuilder.append("// === Block-Script Tree (${blocks.size} blocks) ===\n")
        val visited = mutableSetOf<String>()
        renderChain(rootBlock.id, byId, 0, dslBuilder, visited)

        val javaCode = try {
            val arrayList = ArrayList<BlockBean>(blocks)
            Fx(activityName, jq(), arrayList, isViewBindingEnabled).a()
        } catch (t: Throwable) {
            "// (Gagal render preview Java: ${t.message})"
        }

        dslBuilder.append("\n// === Generated Java Preview (Fx) ===\n")
        dslBuilder.append(javaCode.ifBlank { "// (kosong)" })
        return dslBuilder.toString()
    }

    private fun renderChain(
        startId: String?,
        byId: Map<String, BlockBean>,
        indent: Int,
        out: StringBuilder,
        visited: MutableSet<String>
    ) {
        var currId = startId
        val pad = "  ".repeat(indent)
        while (!currId.isNullOrEmpty() && currId != "-1") {
            if (!visited.add(currId)) break
            val b = byId[currId] ?: break
            val paramsStr = b.parameters.orEmpty().joinToString(", ") { formatParam(it, byId, visited) }
            val hasSub1 = b.subStack1 > 0
            val hasSub2 = b.subStack2 > 0

            if (hasSub1 || hasSub2) {
                out.append("$pad${b.opCode}($paramsStr) {\n")
                if (hasSub1) {
                    renderChain(b.subStack1.toString(), byId, indent + 1, out, visited)
                }
                if (hasSub2) {
                    out.append("$pad} else {\n")
                    renderChain(b.subStack2.toString(), byId, indent + 1, out, visited)
                }
                out.append("$pad}\n")
            } else {
                out.append("$pad${b.opCode}($paramsStr)\n")
            }
            currId = if (b.nextBlock > 0) b.nextBlock.toString() else null
        }
    }

    private fun formatParam(
        param: String?,
        byId: Map<String, BlockBean>,
        visited: MutableSet<String>
    ): String {
        if (param == null) return "\"\""
        if (param.startsWith("@")) {
            val childId = param.removePrefix("@")
            val child = byId[childId] ?: return "\"$param\""
            visited.add(childId)
            if (child.opCode == "getVar" || child.opCode == "getArg") {
                return "[${child.opCode}:${child.type}(\"${child.spec}\")]"
            }
            val nestedArgs = child.parameters.orEmpty().joinToString(", ") { formatParam(it, byId, visited) }
            return "[${child.opCode}($nestedArgs)]"
        }
        return "\"${param.replace("\"", "\\\"").replace("\n", "\\n")}\""
    }
}
