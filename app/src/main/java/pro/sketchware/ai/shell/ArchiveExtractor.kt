package pro.sketchware.ai.shell

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.tukaani.xz.XZInputStream

/**
 * Minimal archive extractor for Linux userland bootstraps.
 * Supports `.zip`, `.tar`, `.tar.gz` and `.tar.xz` (including GNU long names and symlinks) and refuses
 * entries that would escape the destination directory.
 */
object ArchiveExtractor {

    fun extract(archive: File, destDir: File) {
        val name = archive.name.lowercase()
        destDir.mkdirs()
        archive.inputStream().buffered().use { raw ->
            when {
                name.endsWith(".zip") -> extractZip(raw, destDir)
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> extractTar(GZIPInputStream(raw), destDir)
                // Debian/Ubuntu proot-distro bootstraps are published as .tar.xz.
                name.endsWith(".tar.xz") || name.endsWith(".txz") -> extractTar(XZInputStream(raw), destDir)
                name.endsWith(".tar") -> extractTar(raw, destDir)
                else -> throw IllegalArgumentException(
                    "Format arsip '${archive.name}' tidak didukung. Gunakan .zip, .tar, .tar.gz, atau .tar.xz."
                )
            }
        }
    }

    private fun extractZip(input: InputStream, destDir: File) {
        val zis = ZipInputStream(input)
        try {
            var entry = zis.nextEntry
            while (entry != null) {
                val target = safeResolve(destDir, entry.name)
                if (target != null) {
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        } finally {
            zis.close()
        }
    }

    private fun extractTar(input: InputStream, destDir: File) {
        var pendingLongName: String? = null
        var pendingLongLink: String? = null
        var pendingPax: Map<String, String> = emptyMap()
        while (true) {
            val header = ByteArray(512)
            if (!readFully(input, header)) break
            if (isZeroBlock(header)) break

            var name = readString(header, 0, 100)
            // POSIX ustar stores a path prefix at offset 345; GNU tar reuses that area, so only
            // apply the prefix when the header is genuine POSIX (magic "ustar" + NUL).
            if (header[262].toInt() == 0 && String(header, 257, 5, Charsets.US_ASCII) == "ustar") {
                val prefix = readString(header, 345, 155)
                if (prefix.isNotEmpty()) name = "$prefix/$name"
            }
            val typeFlag = header[156].toInt().toChar()
            val size = parseNumber(header, 124, 12)
            val mode = parseNumber(header, 100, 8)
            val rawLinkName = readString(header, 157, 100)

            when (typeFlag) {
                'L' -> {
                    pendingLongName = readDataAsString(input, size)
                    continue
                }
                'K' -> {
                    pendingLongLink = readDataAsString(input, size)
                    continue
                }
                'x' -> {
                    // Per-entry PAX header: carries the full `path`/`linkpath` for the next entry.
                    pendingPax = parsePax(readDataAsString(input, size))
                    continue
                }
                'g' -> {
                    // Global PAX header; must still consume its padding to stay block-aligned.
                    readDataAsString(input, size)
                    continue
                }
            }

            val effectiveName = pendingPax["path"] ?: pendingLongName ?: name
            val linkName = pendingPax["linkpath"] ?: pendingLongLink ?: rawLinkName
            pendingLongName = null
            pendingLongLink = null
            pendingPax = emptyMap()

            when (typeFlag) {
                else -> {
                    val target = safeResolve(destDir, effectiveName)
                    if (target != null) {
                        when (typeFlag) {
                            '5' -> target.mkdirs()
                            '2' -> createSymlink(target, linkName)
                            '1' -> createHardLink(target, linkName, destDir)
                            '0', '\u0000' -> {
                                target.parentFile?.mkdirs()
                                writeData(input, target, size)
                                applyMode(target, mode)
                            }
                            else -> skipData(input, size)
                        }
                    } else {
                        skipData(input, size)
                    }
                }
            }
        }
    }

    private fun createSymlink(link: File, targetName: String) {
        try {
            link.parentFile?.mkdirs()
            if (link.exists()) link.delete()
            Files.createSymbolicLink(link.toPath(), Paths.get(targetName))
        } catch (_: Throwable) {
        }
    }

    private fun createHardLink(link: File, targetName: String, destDir: File) {
        try {
            link.parentFile?.mkdirs()
            if (link.exists()) link.delete()
            val source = safeResolve(destDir, targetName)
            if (source != null && source.exists()) {
                try {
                    Files.createLink(link.toPath(), source.toPath())
                } catch (_: Throwable) {
                    source.copyTo(link, overwrite = true)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun writeData(input: InputStream, target: File, size: Long) {
        var remaining = size
        val buffer = ByteArray(64 * 1024)
        target.outputStream().buffered().use { out ->
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        skipData(input, remaining)
        skipPadding(input, size)
    }

    private fun readDataAsString(input: InputStream, size: Long): String {
        val bytes = ByteArray(size.toInt())
        readFully(input, bytes)
        skipPadding(input, size)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }

    /** Parses PAX extended-header records of the form "<len> key=value\n". */
    private fun parsePax(text: String): Map<String, String> {
        val result = HashMap<String, String>()
        val bytes = text.toByteArray(Charsets.UTF_8)
        var pos = 0
        while (pos < bytes.size) {
            var space = pos
            while (space < bytes.size && bytes[space] != ' '.code.toByte()) space++
            val len = String(bytes, pos, space - pos, Charsets.US_ASCII).toIntOrNull() ?: break
            if (len <= 0 || pos + len > bytes.size) break
            val record = String(bytes, space + 1, pos + len - space - 1, Charsets.UTF_8).removeSuffix("\n")
            val eq = record.indexOf('=')
            if (eq > 0) result[record.substring(0, eq)] = record.substring(eq + 1)
            pos += len
        }
        return result
    }

    private fun skipData(input: InputStream, size: Long) {
        var remaining = size
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val padding = ((512 - (size % 512)) % 512)
        if (padding > 0) skipData(input, padding)
    }

    private fun applyMode(file: File, mode: Long) {
        if (mode and 0b001_000_000L != 0L) file.setExecutable(true, false)
    }

    private fun safeResolve(destDir: File, rawName: String): File? {
        val cleaned = rawName.replace('\\', '/').removePrefix("./").trim()
        if (cleaned.isEmpty()) return null
        return try {
            val destPath = destDir.canonicalFile.toPath()
            val target = File(destDir, cleaned)
            val targetPath = target.canonicalFile.toPath()
            if (targetPath.startsWith(destPath)) target else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun isZeroBlock(bytes: ByteArray): Boolean {
        for (b in bytes) if (b.toInt() != 0) return false
        return true
    }

    private fun readString(bytes: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = minOf(offset + length, bytes.size)
        while (end < limit && bytes[end].toInt() != 0) end++
        return String(bytes, offset, end - offset, Charsets.US_ASCII).trim()
    }

    private fun parseNumber(bytes: ByteArray, offset: Int, length: Int): Long {
        if (bytes[offset].toInt() and 0x80 != 0) {
            var value = (bytes[offset].toInt() and 0x7f).toLong()
            for (i in 1 until length) {
                value = (value shl 8) or (bytes[offset + i].toInt() and 0xff).toLong()
            }
            return value
        }
        val text = String(bytes, offset, length, Charsets.US_ASCII).trim { it == ' ' || it == '\u0000' }
        return text.toLongOrNull(8) ?: 0L
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) return false
            read += n
        }
        return true
    }
}
