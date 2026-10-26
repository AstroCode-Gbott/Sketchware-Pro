package pro.sketchware.ai.shell

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun extractsTarXzBootstrapUsedByDebianRootfs() {
        val archive = writeTarXz(
            File(tmp.root, "debian-trixie-aarch64-pd-v4.29.0.tar.xz"),
            debianRootfsEntries()
        )
        val dest = tmp.newFolder("out")

        ArchiveExtractor.extract(archive, dest)

        val root = File(dest, "debian-trixie-aarch64")
        assertEquals(
            "PRETTY_NAME=\"Debian GNU/Linux 13 (trixie)\"\nID=debian\n",
            File(root, "etc/os-release").readText()
        )
        assertEquals("#!/bin/sh\n", File(root, "usr/bin/apt-get").readText())
    }

    @Test
    fun usesPaxPathForNamesLongerThanUstarField() {
        val archive = writeTarXz(File(tmp.root, "pax.tar.xz"), debianRootfsEntries())
        val dest = tmp.newFolder("out")

        ArchiveExtractor.extract(archive, dest)

        assertEquals("license\n", File(dest, "debian-trixie-aarch64/$LONG_DEBIAN_PATH").readText())
    }
}
