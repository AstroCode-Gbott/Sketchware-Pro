package pro.sketchware.ai.shell

import android.content.Context
import android.content.ContextWrapper
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Minimal Context exposing only the private files dir used by [LinuxSubsystem]. */
internal class TestContext(private val root: File) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
}

private fun tarHeader(name: String, size: Int, type: Char): ByteArray {
    val header = ByteArray(512)
    fun put(offset: Int, text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        System.arraycopy(bytes, 0, header, offset, minOf(bytes.size, 512 - offset))
    }
    put(0, name.take(100))
    put(100, "0000755\u0000")
    put(108, "0000000\u0000")
    put(116, "0000000\u0000")
    put(124, String.format("%011o", size) + "\u0000")
    put(136, "00000000000\u0000")
    put(148, "        ")
    header[156] = type.code.toByte()
    put(257, "ustar\u0000")
    put(263, "00")
    val checksum = header.sumOf { it.toInt() and 0xff }
    put(148, String.format("%06o", checksum) + "\u0000 ")
    return header
}

private fun ByteArrayOutputStream.writeBlock(header: ByteArray, data: ByteArray) {
    write(header)
    write(data)
    write(ByteArray((512 - data.size % 512) % 512))
}

/** One PAX record: "<len> key=value\n", where <len> counts the whole record. */
private fun paxRecord(key: String, value: String): String {
    val body = " $key=$value\n"
    var len = body.length + 1
    while ("$len$body".length != len) len = "$len$body".length
    return "$len$body"
}

/**
 * Builds an uncompressed archive like GNU tar --format=pax (as used by proot-distro):
 * every regular file is preceded by a PAX 'x' header carrying its full `path`, while the
 * ustar name field holds only a truncated 100-char name.
 */
internal fun buildTar(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    for ((name, data) in entries) {
        val pax = paxRecord("path", name).toByteArray(Charsets.UTF_8)
        out.writeBlock(tarHeader("PaxHeaders/" + name.takeLast(80), pax.size, 'x'), pax)
        out.writeBlock(tarHeader(name, data.size, '0'), data)
    }
    out.write(ByteArray(1024))
    return out.toByteArray()
}

internal fun writeTarXz(target: File, entries: List<Pair<String, ByteArray>>): File {
    XZOutputStream(target.outputStream(), LZMA2Options()).use { it.write(buildTar(entries)) }
    return target
}

/** A path longer than the 100-char ustar name field, as found in the real Debian bootstrap. */
internal val LONG_DEBIAN_PATH = "usr/share/doc/" + "a".repeat(60) + "/" + "b".repeat(40) + "/copyright"

/** Mirrors the real proot-distro layout: everything wrapped in one top-level folder. */
internal fun debianRootfsEntries(top: String = "debian-trixie-aarch64"): List<Pair<String, ByteArray>> = listOf(
    "$top/etc/os-release" to "PRETTY_NAME=\"Debian GNU/Linux 13 (trixie)\"\nID=debian\n".toByteArray(),
    "$top/bin/sh" to "#!/bin/sh\n".toByteArray(),
    "$top/usr/bin/apt-get" to "#!/bin/sh\n".toByteArray(),
    "$top/$LONG_DEBIAN_PATH" to "license\n".toByteArray()
)
