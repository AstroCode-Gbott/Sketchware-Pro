package pro.sketchware.ai.tools

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/**
 * Native HTTP tools for the agent (the equivalent of Claude Code's WebFetch / WebSearch).
 * They run on the JVM, so browsing works even before a Linux distro is installed, and they return
 * readable text (HTML is reduced to text with links preserved) instead of raw markup.
 */
object WebTools {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36 SketchwareAiAgent/1.0"
    private const val MAX_BODY_BYTES = 4 * 1024 * 1024
    private const val DEFAULT_MAX_CHARS = 20_000

    data class HttpResponse(
        val code: Int,
        val finalUrl: String,
        val contentType: String,
        val headers: Map<String, String>,
        val body: String
    )

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 30_000
    ): HttpResponse {
        var current = URL(url)
        var redirects = 0
        while (true) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = method.uppercase()
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/json,text/plain,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (body != null && method.uppercase() !in setOf("GET", "HEAD")) {
                    conn.doOutput = true
                    if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code in 300..399 && redirects < 8) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        current = URL(current, location)
                        redirects++
                        continue
                    }
                }
                val stream: InputStream? = if (code >= 400) conn.errorStream else conn.inputStream
                val raw = stream?.let { s ->
                    val decoded = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) GZIPInputStream(s) else s
                    decoded.use { readLimited(it) }
                } ?: ByteArray(0)
                val contentType = conn.contentType.orEmpty()
                val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                    .find(contentType)?.groupValues?.get(1)
                    ?.let { runCatching { charset(it) }.getOrNull() } ?: Charsets.UTF_8
                val headerMap = conn.headerFields.orEmpty()
                    .filterKeys { it != null }
                    .mapKeys { it.key!! }
                    .mapValues { it.value.joinToString(", ") }
                return HttpResponse(code, current.toString(), contentType, headerMap, String(raw, charset))
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_BODY_BYTES) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** `web_fetch`: fetch a URL and return readable content. */
    fun fetch(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        raw: Boolean,
        maxChars: Int,
        offset: Int
    ): String {
        val target = normalizeUrl(url) ?: return "Error: URL tidak valid: '$url'"
        val res = request(target, method.ifBlank { "GET" }, headers, body)
        val isHtml = res.contentType.contains("html", ignoreCase = true) ||
            (res.contentType.isBlank() && res.body.trimStart().startsWith("<"))
        val content = if (isHtml && !raw) htmlToText(res.body, res.finalUrl) else res.body
        val limit = if (maxChars <= 0) DEFAULT_MAX_CHARS else maxChars.coerceAtMost(200_000)
        val start = offset.coerceIn(0, content.length)
        val end = (start + limit).coerceAtMost(content.length)
        return buildString {
            appendLine("=== web_fetch ${method.uppercase().ifBlank { "GET" }} ${res.finalUrl} ===")
            appendLine("HTTP ${res.code} | ${res.contentType.ifBlank { "unknown" }} | ${content.length} chars" +
                (if (isHtml && !raw) " (HTML → teks)" else ""))
            if (start > 0 || end < content.length) {
                appendLine("Menampilkan karakter $start..$end dari ${content.length}." +
                    (if (end < content.length) " Lanjutkan dengan offset=$end." else ""))
            }
            appendLine()
            append(content.substring(start, end))
        }.trim()
    }

    /** `web_search`: DuckDuckGo HTML search (no API key needed). */
    fun search(query: String, maxResults: Int): String {
        if (query.isBlank()) return "Error: parameter 'query' kosong."
        val limit = maxResults.coerceIn(1, 20)
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val res = request(url, "GET", mapOf("Referer" to "https://html.duckduckgo.com/"))
        if (res.code >= 400) return "Error: pencarian gagal (HTTP ${res.code})."

        val results = mutableListOf<Triple<String, String, String>>()
        val blockRegex = Regex(
            """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>(.*?)(?=<a[^>]+class="result__a"|$)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val snippetRegex = Regex(
            """class="result__snippet"[^>]*>(.*?)</(?:a|div|td)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (m in blockRegex.findAll(res.body)) {
            val href = resolveDdgLink(decodeEntities(m.groupValues[1]))
            if (href.contains("duckduckgo.com/y.js")) continue // ads
            val title = stripTags(m.groupValues[2])
            val snippet = snippetRegex.find(m.groupValues[3])?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            if (title.isNotBlank() && href.startsWith("http")) results.add(Triple(title, href, snippet))
            if (results.size >= limit) break
        }
        if (results.isEmpty()) {
            return "Tidak ada hasil untuk \"$query\" (atau halaman pencarian berubah format). " +
                "Coba web_fetch langsung ke dokumentasi, atau run_shell dengan curl."
        }
        return buildString {
            appendLine("=== web_search: \"$query\" (${results.size} hasil) ===")
            results.forEachIndexed { i, (title, link, snippet) ->
                appendLine("${i + 1}. $title")
                appendLine("   $link")
                if (snippet.isNotBlank()) appendLine("   $snippet")
            }
            appendLine()
            append("Gunakan web_fetch(url=...) untuk membaca isi halaman.")
        }.trim()
    }

    private fun resolveDdgLink(href: String): String {
        val full = if (href.startsWith("//")) "https:$href" else href
        val uddg = Regex("[?&]uddg=([^&]+)").find(full)?.groupValues?.get(1) ?: return full
        return runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrDefault(full)
    }

    private fun normalizeUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return runCatching { URL(withScheme).toString() }.getOrNull()
    }

    /** Reduces HTML to readable text: drops scripts/styles/nav chrome, keeps headings, lists and links. */
    fun htmlToText(html: String, baseUrl: String): String {
        var s = html
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(s)?.groupValues?.get(1)?.let { stripTags(it) }
        s = s.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        for (tag in listOf("script", "style", "noscript", "svg", "head", "iframe", "template")) {
            s = s.replace(Regex("<$tag\\b.*?</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        }
        s = s.replace(Regex("<pre\\b[^>]*>(.*?)</pre>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n```\n" + decodeEntities(it.groupValues[1].replace(Regex("<[^>]+>"), "")) + "\n```\n"
        }
        s = s.replace(Regex("<code\\b[^>]*>(.*?)</code>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "`" + it.groupValues[1].replace(Regex("<[^>]+>"), "") + "`"
        }
        s = s.replace(Regex("<h([1-6])[^>]*>(.*?)</h\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            "\n\n" + "#".repeat(it.groupValues[1].toInt()) + " " + stripTags(it.groupValues[2]) + "\n"
        }
        s = s.replace(Regex("<a\\b[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))) {
            val text = stripTags(it.groupValues[2])
            val href = decodeEntities(it.groupValues[1])
            if (text.isBlank() || href.startsWith("#") || href.startsWith("javascript:")) text
            else "[$text](${runCatching { URL(URL(baseUrl), href).toString() }.getOrDefault(href)})"
        }
        s = s.replace(Regex("<li\\b[^>]*>", RegexOption.IGNORE_CASE), "\n- ")
        s = s.replace(Regex("<(br|/p|/div|/tr|/section|/article|/table|/ul|/ol)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<(td|th)\\b[^>]*>", RegexOption.IGNORE_CASE), " | ")
        s = s.replace(Regex("<[^>]+>"), "")
        s = decodeEntities(s)
        val lines = s.lines().map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
        val compact = StringBuilder()
        var blank = 0
        for (line in lines) {
            if (line.isEmpty()) {
                if (++blank <= 1) compact.append('\n')
            } else {
                blank = 0
                compact.append(line).append('\n')
            }
        }
        val text = compact.toString().trim()
        return if (!title.isNullOrBlank()) "# $title\n\n$text" else text
    }

    private fun stripTags(s: String): String =
        decodeEntities(s.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()

    private fun decodeEntities(s: String): String {
        var r = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&#x27;", "'")
        r = r.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        r = r.replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        return r.replace("&amp;", "&")
    }
}
