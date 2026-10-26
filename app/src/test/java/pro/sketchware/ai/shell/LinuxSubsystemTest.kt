package pro.sketchware.ai.shell

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LinuxSubsystemTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        LinuxSubsystem.init(TestContext(tmp.newFolder("app")))
    }

    @Test
    fun installArchiveInstallsDownloadedRawProotBinary() {
        // proot-me publishes proot as a bare static ELF, not as an archive.
        val elf = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1, 1, 0) +
            ByteArray(56)
        val raw = File(tmp.root, "proot-v5.3.0-aarch64-static").apply { writeBytes(elf) }

        val report = LinuxSubsystem.installArchive(raw)

        assertFalse(report, report.startsWith("Error"))
        assertTrue(report, LinuxSubsystem.isProotInstalled())
        assertArrayEquals(elf, LinuxSubsystem.prootBinary().readBytes())
    }

    @Test
    fun installArchiveInstallsDebianTarXzAsDebianDistro() {
        val archive = writeTarXz(
            File(tmp.root, "debian-trixie-aarch64-pd-v4.29.0.tar.xz"),
            debianRootfsEntries()
        )

        val report = LinuxSubsystem.installArchive(archive)

        assertTrue(report, LinuxSubsystem.installedDistros().contains("debian"))
        val root = LinuxSubsystem.rootfsDir("debian")
        assertTrue(report, File(root, "usr/bin/apt-get").isFile)
        assertTrue(report, File(root, LONG_DEBIAN_PATH).isFile)
        assertFalse(report, report.startsWith("Error"))
    }

    @Test
    fun guestPathIncludesLocalBinForGloballyInstalledTools() {
        // npm -g, pip and manual installs place executables in /usr/local/bin.
        assertTrue(LinuxSubsystem.GUEST_ENV_PREFIX.contains("/usr/local/bin"))
        // /usr/local/sbin is absent in the Alpine rootfs; listing it breaks command lookup on device.
        assertFalse(LinuxSubsystem.GUEST_ENV_PREFIX.contains("/usr/local/sbin"))
    }

    @Test
    fun prootCommandSeedsDnsAndBindsProjectDirectory() {
        val debianRoot = LinuxSubsystem.rootfsDir("debian")
        File(debianRoot, "etc/apt").mkdirs()
        val project = tmp.newFolder("project")

        val args = LinuxSubsystem.buildProotCommand("debian", project, listOf("/bin/sh"))

        val resolv = File(debianRoot, "etc/resolv.conf").readText()
        assertTrue(resolv, resolv.contains("nameserver "))
        val aptConf = File(debianRoot, "etc/apt/apt.conf.d/99sketchware").readText()
        assertTrue(aptConf, aptConf.contains("Acquire::ForceIPv4"))
        assertTrue(aptConf, aptConf.contains("APT::Sandbox::User \"root\""))
        assertTrue(args.toString(), args.any { it.endsWith(":/dev/shm") })
        val bindIndex = args.indexOf(project.absolutePath)
        assertTrue(args.toString(), bindIndex > 0 && args[bindIndex - 1] == "-b")
        assertTrue(args.toString(), args.lastIndexOf(project.absolutePath) > bindIndex)
    }

    @Test
    fun defaultDistroPrefersDebianOverAlpine() {
        File(LinuxSubsystem.rootfsDir("alpine"), "bin").mkdirs()
        File(LinuxSubsystem.rootfsDir("debian"), "bin").mkdirs()

        org.junit.Assert.assertEquals("debian", LinuxSubsystem.defaultDistro())
    }
}
