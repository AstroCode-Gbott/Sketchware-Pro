package pro.sketchware.ai.shell

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LinuxDownloaderTest {

    private lateinit var originalClient: OkHttpClient
    private val requested = mutableListOf<String>()

    @Before
    fun setUp() {
        originalClient = LinuxDownloader.client
    }

    @After
    fun tearDown() {
        LinuxDownloader.client = originalClient
    }

    private fun fakeClient(routes: Map<String, String>): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            requested.add(url)
            val body = routes.entries.firstOrNull { url.startsWith(it.key) }?.value
                ?: throw IOException("unexpected request: $url")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

    @Test
    fun debianResolvesDebianRootfsInsteadOfAlpine() {
        val url = "https://github.com/termux/proot-distro/releases/download/v4.29.0/" +
            "debian-trixie-aarch64-pd-v4.29.0.tar.xz"
        val releases = """
            [{"tag_name":"v4.29.0","assets":[
              {"name":"ubuntu-noble-aarch64-pd-v4.29.0.tar.xz","browser_download_url":"https://example.invalid/u","digest":"sha256:00"},
              {"name":"debian-trixie-x86_64-pd-v4.29.0.tar.xz","browser_download_url":"https://example.invalid/x","digest":"sha256:11"},
              {"name":"debian-trixie-aarch64-pd-v4.29.0.tar.xz","browser_download_url":"$url","digest":"sha256:abc123"}
            ]}]
        """.trimIndent()
        LinuxDownloader.client = fakeClient(
            mapOf("https://api.github.com/repos/termux/proot-distro/releases" to releases)
        )

        val resolved = try {
            LinuxDownloader.resolveRootfs("debian", "arm64")
        } catch (e: IOException) {
            throw AssertionError("debian resolved through $requested", e)
        }

        assertEquals(url, resolved?.first)
        assertEquals("abc123", resolved?.second)
        assertTrue(requested.toString(), requested.none { it.contains("alpinelinux") })
    }

    @Test
    fun unknownDistroDoesNotSilentlyInstallAlpine() {
        LinuxDownloader.client = fakeClient(emptyMap())

        val resolved = try {
            LinuxDownloader.resolveRootfs("fedora", "arm64")
        } catch (e: IOException) {
            throw AssertionError("unsupported distro triggered $requested", e)
        }

        assertNull(resolved)
        assertTrue(requested.toString(), requested.isEmpty())
    }
}
