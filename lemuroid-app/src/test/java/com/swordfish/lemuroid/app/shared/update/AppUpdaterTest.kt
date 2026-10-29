package com.swordfish.lemuroid.app.shared.update

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class AppUpdaterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rawServer: ServerSocket

    private val validApkBytes: ByteArray by lazy {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("<manifest/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(Random(42).nextBytes(600 * 1024))
            zip.closeEntry()
        }
        bos.toByteArray()
    }

    private val zipWithoutManifestBytes: ByteArray by lazy {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("not an apk".toByteArray())
            zip.closeEntry()
        }
        bos.toByteArray()
    }

    @Before
    fun setUp() {
        rawServer = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!rawServer.isClosed) {
                val socket = runCatching { rawServer.accept() }.getOrNull() ?: break
                Thread {
                    runCatching {
                        socket.use { s ->
                            val ins = s.getInputStream()
                            val headerBuf = ByteArrayOutputStream()
                            var last4 = 0
                            while (last4 != 0x0D0A0D0A) {
                                val b = ins.read()
                                if (b < 0) break
                                headerBuf.write(b)
                                last4 = (last4 shl 8) or b
                            }
                            val reqLine =
                                headerBuf.toString(
                                    Charsets.UTF_8.name(),
                                ).lineSequence().firstOrNull().orEmpty()
                            val out = s.getOutputStream()
                            if (reqLine.contains("/truncated")) {
                                val head =
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/octet-stream\r\n" +
                                        "Content-Length: 1000\r\n\r\n"
                                out.write(head.toByteArray())
                                out.write(validApkBytes, 0, 500)
                            } else {
                                val head =
                                    "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: application/vnd.android.package-archive\r\n" +
                                        "Content-Length: ${validApkBytes.size}\r\n\r\n"
                                out.write(head.toByteArray())
                                out.write(validApkBytes)
                            }
                            out.flush()
                        }
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    @After
    fun tearDown() {
        runCatching { rawServer.close() }
    }

    @Test
    fun semanticVersionParsesAndComparesCorrectly() {
        assertEquals(SemanticVersion(1, 17, 2), SemanticVersion.parse("v1.17.2"))
        assertEquals(SemanticVersion(1, 17, 2), SemanticVersion.parse(" V1.17.2 "))
        assertEquals(SemanticVersion(1, 17, 0), SemanticVersion.parse("1.17"))
        assertEquals(SemanticVersion(1, 17, 1, "DEBUG"), SemanticVersion.parse("1.17.1-DEBUG"))
        assertEquals(SemanticVersion(1, 17, 2, "rc.1"), SemanticVersion.parse("v1.17.2-rc.1+build.9"))
        assertEquals("1.17.1", SemanticVersion.stripDebugSuffix("1.17.1-DEBUG"))
        assertEquals("1.17.1", SemanticVersion.stripDebugSuffix("1.17.1"))

        val invalid = listOf("", "v", "1", "1.2.0-", "1.2.0.1", "latest", "1.2.0-beta..1", "99999999999.0.0", null)
        for (input in invalid) {
            assertNull("Expected null for: $input", SemanticVersion.parse(input))
        }

        assertTrue(SemanticVersion.isNewerVersion("1.17.1", "v1.17.2"))
        assertTrue(SemanticVersion.isNewerVersion("1.2.0", "v1.10.0"))
        assertTrue(SemanticVersion.isNewerVersion("1.17.1-DEBUG", "v1.17.1"))
        assertFalse(SemanticVersion.isNewerVersion("1.17.2", "v1.17.2"))
        assertFalse(SemanticVersion.isNewerVersion("1.17.2", "v1.17.1"))
        assertFalse(SemanticVersion.isNewerVersion("1.17.2", "nightly"))
    }

    @Test
    fun releaseJsonPicksLemuroidReleaseApkAndParsesArrays() {
        val dynamic = ReleaseAsset("lemuroid-app-free-dynamic-release.apk", "https://x/1.apk", 100L)
        val bundle = ReleaseAsset("lemuroid-app-free-bundle-release.apk", "https://x/2.apk", 200L)
        val otherApk = ReleaseAsset("debug.APK", "https://x/3.apk", 50L)
        val archive = ReleaseAsset("source.zip", "https://x/src.zip", 10L)

        assertEquals(dynamic, ReleaseJson.pickApk(listOf(archive, otherApk, bundle, dynamic)))
        assertEquals(bundle, ReleaseJson.pickApk(listOf(archive, otherApk, bundle)))
        assertEquals(otherApk, ReleaseJson.pickApk(listOf(archive, otherApk)))
        assertNull(ReleaseJson.pickApk(listOf(archive)))

        val parsed =
            ReleaseJson.parse(
                githubPayload(
                    "v1.17.2",
                    "https://github.com/codescv/Lemuroid/releases/download/v1.17.2/" +
                        "lemuroid-app-free-dynamic-release.apk",
                    42L,
                ),
            )
        assertEquals("v1.17.2", parsed.tagName)
        assertEquals("1.17.2", parsed.version)
        assertEquals("lemuroid-app-free-dynamic-release.apk", parsed.apkAsset?.name)

        val listJson =
            """
            [
              {"tag_name":"v1.18.0","draft":true},
              {"tag_name":"v1.17.3-rc1","prerelease":true},
              {"tag_name":"v1.17.2","prerelease":false}
            ]
            """.trimIndent()
        assertEquals("v1.17.2", ReleaseJson.parse(listJson).tagName)

        val onlyPrereleaseJson = """[{"tag_name":"v1.17.3-rc1","prerelease":true}]"""
        assertEquals("v1.17.3-rc1", ReleaseJson.parse(onlyPrereleaseJson).tagName)

        val draftOnlyErr =
            assertThrows(UpdateException::class.java) {
                ReleaseJson.parse("""[{"tag_name":"v1.18.0","draft":true}]""")
            }
        assertEquals(UpdateErrorCode.NOT_FOUND, draftOnlyErr.code)
    }

    @Test
    fun checkLatestReleaseStripsDebugSuffixAndFallsBackOn404() {
        val seenRequests = Collections.synchronizedList(mutableListOf<Pair<String, Map<String, String>>>())
        val payload = githubPayload("v1.17.2", "https://github.com/dl/a.apk", validApkBytes.size.toLong())

        val updater =
            AppUpdater { url ->
                FakeHttpURLConnection(
                    url = url,
                    code = 200,
                    body = payload.toByteArray(),
                    onConnect = { headers -> seenRequests += url.toString() to headers },
                )
            }

        val available = updater.checkLatestRelease("1.17.1")
        assertTrue(available.updateAvailable)
        assertEquals("1.17.2", available.latest.version)
        val (reqUrl, reqHeaders) = seenRequests.single()
        assertEquals("https://api.github.com/repos/codescv/Lemuroid/releases/latest", reqUrl)
        assertEquals("application/vnd.github+json", reqHeaders["accept"])
        assertEquals("2022-11-28", reqHeaders["x-github-api-version"])
        assertEquals("Lemuroid/1.17.1", reqHeaders["user-agent"])

        // Same base version with -DEBUG suffix must NOT report an update.
        val debugSame = updater.checkLatestRelease("1.17.2-DEBUG")
        assertFalse(debugSame.updateAvailable)
        assertEquals("1.17.2", debugSame.currentVersion)

        // When /releases/latest returns 404 (e.g. only prereleases exist), falls back to /releases?per_page=5.
        val listJson =
            """
            [
              {"tag_name":"v1.18.0","draft":true},
              {"tag_name":"v1.17.2","prerelease":true,"name":"Lemuroid v1.17.2",
               "assets":[{"name":"lemuroid-app-free-dynamic-release.apk",
                          "browser_download_url":"https://x/a.apk","size":10}]}
            ]
            """.trimIndent()
        val fallbackVisited = mutableListOf<String>()
        val fallbackUpdater =
            AppUpdater { url ->
                fallbackVisited += url.toString()
                if (url.path.endsWith("/latest")) {
                    FakeHttpURLConnection(
                        url = url,
                        code = 404,
                        errorBody = """{"message":"Not Found"}""".toByteArray(),
                    )
                } else {
                    FakeHttpURLConnection(url = url, code = 200, body = listJson.toByteArray())
                }
            }
        val fallbackResult = fallbackUpdater.checkLatestRelease("1.17.1")
        assertTrue(fallbackResult.updateAvailable)
        assertEquals("1.17.2", fallbackResult.latest.version)
        assertEquals(2, fallbackVisited.size)
        assertTrue(fallbackVisited[1].endsWith("/releases?per_page=5"))
    }

    @Test
    fun checkLatestReleaseMapsErrors() {
        fun checkWith(connFactory: (URL) -> HttpURLConnection): UpdateException =
            assertThrows(UpdateException::class.java) {
                AppUpdater(releasesListUrl = "", openConnection = connFactory).checkLatestRelease("1.17.1")
            }

        assertEquals(
            UpdateErrorCode.NOT_FOUND,
            checkWith { FakeHttpURLConnection(it, code = 404) }.code,
        )
        assertEquals(
            UpdateErrorCode.RATE_LIMITED,
            checkWith {
                FakeHttpURLConnection(
                    it,
                    code = 403,
                    headers = mapOf("X-RateLimit-Remaining" to "0"),
                )
            }.code,
        )
        assertEquals(
            UpdateErrorCode.RATE_LIMITED,
            checkWith { FakeHttpURLConnection(it, code = 429) }.code,
        )
        assertEquals(
            UpdateErrorCode.INVALID_RESPONSE,
            checkWith {
                FakeHttpURLConnection(
                    it,
                    code = 200,
                    body = ByteArray(3 * 1024 * 1024) { 'a'.code.toByte() },
                )
            }.code,
        )
        assertEquals(
            UpdateErrorCode.INVALID_RESPONSE,
            checkWith { FakeHttpURLConnection(it, code = 200, body = "<html>error</html>".toByteArray()) }.code,
        )
    }

    @Test
    fun downloadFollowsCrossHostRedirectsAndValidatesBeforeAtomicRename() {
        val destFile = File(tempFolder.root, "updates/lemuroid-1.17.2.apk")
        val visitedUrls = mutableListOf<String>()
        val cdnHeaders = mutableMapOf<String, String>()
        val progressUpdates = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        var validatedTempFile: File? = null

        val updater =
            AppUpdater { url ->
                visitedUrls += url.toString()
                when (url.host) {
                    "github.com" ->
                        if (url.path.endsWith("/start.apk")) {
                            FakeHttpURLConnection(
                                url = url,
                                code = 302,
                                headers = mapOf("Location" to "/codescv/Lemuroid/releases/download/v1.17.2/hop2.apk"),
                            )
                        } else {
                            FakeHttpURLConnection(
                                url = url,
                                code = 302,
                                headers =
                                    mapOf(
                                        "Location" to
                                            "https://objects.githubusercontent.com/github-production/lemuroid.apk",
                                    ),
                            )
                        }
                    "objects.githubusercontent.com" ->
                        FakeHttpURLConnection(
                            url = url,
                            code = 200,
                            headers =
                                mapOf(
                                    "Content-Type" to "application/octet-stream",
                                    "Content-Length" to validApkBytes.size.toString(),
                                ),
                            body = validApkBytes,
                            onConnect = { headers -> cdnHeaders.putAll(headers) },
                        )
                    else -> error("Unexpected host: ${url.host}")
                }
            }

        val out =
            updater.downloadApk(
                url = "https://github.com/codescv/Lemuroid/releases/download/v1.17.2/start.apk",
                destFile = destFile,
                expectedSize = validApkBytes.size.toLong(),
                validator = { tmpFile ->
                    validatedTempFile = tmpFile
                    assertTrue(tmpFile.isFile)
                    assertFalse("destFile must not exist before validator passes", destFile.exists())
                },
                onProgress = { done, total -> progressUpdates += done to total },
            )

        assertEquals(destFile, out)
        assertNotNull(validatedTempFile)
        assertArrayEquals(validApkBytes, out.readBytes())
        assertFalse(File(destFile.path + ".tmp").exists())
        assertEquals(3, visitedUrls.size)
        assertEquals("https://objects.githubusercontent.com/github-production/lemuroid.apk", visitedUrls.last())
        assertEquals("identity", cdnHeaders["accept-encoding"])
        assertNull(cdnHeaders["authorization"])
        assertEquals(validApkBytes.size.toLong(), progressUpdates.last().first)

        // Real TCP socket download test via loopback ServerSocket.
        val realSocketDest = File(tempFolder.root, "updates/lemuroid-socket.apk")
        val socketOut =
            AppUpdater().downloadApk(
                url = "http://127.0.0.1:${rawServer.localPort}/valid.apk",
                destFile = realSocketDest,
                expectedSize = validApkBytes.size.toLong(),
            )
        assertArrayEquals(validApkBytes, socketOut.readBytes())

        // Validator failure (SIGNATURE_MISMATCH) cleans up .tmp and never creates destFile.
        val sigDest = File(tempFolder.root, "updates/lemuroid-bad-sig.apk")
        val sigErr =
            assertThrows(UpdateException::class.java) {
                updater.downloadApk(
                    url = "https://github.com/codescv/Lemuroid/releases/download/v1.17.2/start.apk",
                    destFile = sigDest,
                    validator = {
                        throw UpdateException(UpdateErrorCode.SIGNATURE_MISMATCH, "Cert mismatch")
                    },
                )
            }
        assertEquals(UpdateErrorCode.SIGNATURE_MISMATCH, sigErr.code)
        assertFalse(sigDest.exists())
        assertFalse(File(sigDest.path + ".tmp").exists())
    }

    @Test
    fun redirectSecurityGuardsAndHttpsDowngradeRefusal() {
        val destFile = File(tempFolder.root, "updates/guard.apk")

        // Refuses https -> http downgrade.
        val downgradeUpdater =
            AppUpdater { url ->
                FakeHttpURLConnection(
                    url = url,
                    code = 302,
                    headers = mapOf("Location" to "http://objects.githubusercontent.com/lemuroid.apk"),
                )
            }
        val downgradeErr =
            assertThrows(UpdateException::class.java) {
                downgradeUpdater.downloadApk(
                    "https://github.com/codescv/Lemuroid/releases/download/v1.17.2/a.apk",
                    destFile,
                )
            }
        assertEquals(UpdateErrorCode.NETWORK, downgradeErr.code)
        assertTrue(downgradeErr.detail.contains("https -> http"))

        // Refuses >5 redirects.
        var hopsSeen = 0
        val loopUpdater =
            AppUpdater { url ->
                hopsSeen++
                FakeHttpURLConnection(
                    url = url,
                    code = 302,
                    headers = mapOf("Location" to "/hop/$hopsSeen"),
                )
            }
        val loopErr =
            assertThrows(UpdateException::class.java) {
                loopUpdater.downloadApk("https://github.com/hop/0", destFile)
            }
        assertEquals(UpdateErrorCode.NETWORK, loopErr.code)
        assertEquals(1 + AppUpdater.MAX_REDIRECTS, hopsSeen)

        // Refuses file:// redirect scheme.
        val fileSchemeUpdater =
            AppUpdater { url ->
                FakeHttpURLConnection(url = url, code = 302, headers = mapOf("Location" to "file:///etc/passwd"))
            }
        val fileSchemeErr =
            assertThrows(UpdateException::class.java) {
                fileSchemeUpdater.downloadApk("https://github.com/dl", destFile)
            }
        assertEquals(UpdateErrorCode.NETWORK, fileSchemeErr.code)

        // Refuses missing Location header.
        val noLocUpdater = AppUpdater { url -> FakeHttpURLConnection(url = url, code = 302) }
        val noLocErr =
            assertThrows(UpdateException::class.java) {
                noLocUpdater.downloadApk("https://github.com/dl", destFile)
            }
        assertEquals(UpdateErrorCode.HTTP, noLocErr.code)
    }

    @Test
    fun rejectsCorruptDownloadsAndSupportsCancellation() {
        val destFile = File(tempFolder.root, "updates/corrupt.apk")

        val badUpdaters =
            listOf(
                AppUpdater { url ->
                    FakeHttpURLConnection(
                        url = url,
                        code = 200,
                        headers = mapOf("Content-Type" to "text/html; charset=utf-8"),
                        body = "<html>portal</html>".toByteArray(),
                    )
                },
                AppUpdater { url ->
                    FakeHttpURLConnection(url = url, code = 200, body = zipWithoutManifestBytes)
                },
                AppUpdater { url ->
                    FakeHttpURLConnection(url = url, code = 200, body = validApkBytes.copyOf(validApkBytes.size / 2))
                },
            )

        for (badUpdater in badUpdaters) {
            val err =
                assertThrows(UpdateException::class.java) {
                    badUpdater.downloadApk("https://github.com/bad.apk", destFile)
                }
            assertEquals(UpdateErrorCode.CORRUPT_DOWNLOAD, err.code)
            assertFalse(destFile.exists())
            assertFalse(File(destFile.path + ".tmp").exists())
        }

        // Fixed Content-Length truncated TCP stream.
        val truncErr =
            assertThrows(UpdateException::class.java) {
                AppUpdater().downloadApk(
                    "http://127.0.0.1:${rawServer.localPort}/truncated",
                    destFile,
                    readTimeoutMs = 5_000,
                )
            }
        assertEquals(UpdateErrorCode.CORRUPT_DOWNLOAD, truncErr.code)
        assertFalse(destFile.exists())
        assertFalse(File(destFile.path + ".tmp").exists())

        // Mid-download CancelToken cancellation cleans up .tmp.
        val token = CancelToken()
        val firstChunk = CountDownLatch(1)
        val slowUpdater =
            AppUpdater { url ->
                val slowStream =
                    object : FilterInputStream(ByteArrayInputStream(validApkBytes)) {
                        override fun read(
                            b: ByteArray,
                            off: Int,
                            len: Int,
                        ): Int {
                            if (token.isCancelled) throw IOException("disconnected")
                            Thread.sleep(25)
                            return super.read(b, off, minOf(len, 16 * 1024))
                        }
                    }
                FakeHttpURLConnection(
                    url = url,
                    code = 200,
                    headers = mapOf("Content-Length" to validApkBytes.size.toString()),
                    stream = slowStream,
                )
            }
        val canceller =
            Thread {
                firstChunk.await(5, TimeUnit.SECONDS)
                token.cancel()
            }.apply { start() }
        val cancelErr =
            assertThrows(UpdateException::class.java) {
                slowUpdater.downloadApk("https://github.com/slow.apk", destFile, cancelToken = token) { _, _ ->
                    firstChunk.countDown()
                }
            }
        canceller.join(5_000)
        assertEquals(UpdateErrorCode.CANCELLED, cancelErr.code)
        assertFalse(destFile.exists())
        assertFalse(File(destFile.path + ".tmp").exists())
    }

    @Test
    fun signatureComparisonIsOrderInsensitiveAndByteExact() {
        val certA = byteArrayOf(1, 2, 3, 4)
        val certB = byteArrayOf(5, 6, 7, 8)
        val certC = byteArrayOf(1, 2, 3, 9)

        assertTrue(UpdateManager.signaturesMatch(listOf(certA), listOf(certA.copyOf())))
        assertTrue(UpdateManager.signaturesMatch(listOf(certA, certB), listOf(certB, certA)))
        assertFalse(UpdateManager.signaturesMatch(listOf(certA), listOf(certC)))
        assertFalse(UpdateManager.signaturesMatch(listOf(certA), emptyList()))
        assertFalse(UpdateManager.signaturesMatch(emptyList(), listOf(certA)))
    }

    private fun githubPayload(
        tag: String,
        apkUrl: String,
        size: Long,
    ): String =
        """
        {
          "html_url": "https://github.com/codescv/Lemuroid/releases/tag/$tag",
          "tag_name": "$tag",
          "name": "Lemuroid $tag",
          "draft": false,
          "prerelease": false,
          "published_at": "2026-10-29T12:00:00Z",
          "assets": [
            {"name": "SHA256SUMS", "browser_download_url": "https://example.invalid/SHA256SUMS", "size": 64},
            {"name": "lemuroid-app-free-dynamic-release.apk", "browser_download_url": "$apkUrl",
             "size": $size, "content_type": "application/vnd.android.package-archive"}
          ],
          "body": "### Changes\n- In-app auto update"
        }
        """.trimIndent()

    private class FakeHttpURLConnection(
        url: URL,
        private val code: Int,
        private val headers: Map<String, String> = emptyMap(),
        private val body: ByteArray = ByteArray(0),
        private val errorBody: ByteArray = ByteArray(0),
        private val stream: InputStream? = null,
        private val onConnect: ((Map<String, String>) -> Unit)? = null,
    ) : HttpURLConnection(url) {
        private val requestProps = linkedMapOf<String, String>()
        private var notified = false

        override fun setRequestProperty(
            key: String,
            value: String,
        ) {
            requestProps[key.lowercase()] = value
        }

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun connect() {
            if (!notified) {
                notified = true
                onConnect?.invoke(requestProps.toMap())
            }
        }

        override fun getResponseCode(): Int {
            connect()
            return code
        }

        override fun getContentType(): String? = getHeaderField("Content-Type")

        override fun getContentLengthLong(): Long = getHeaderField("Content-Length")?.toLongOrNull() ?: -1L

        override fun getHeaderField(name: String?): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getInputStream(): InputStream {
            connect()
            return stream ?: ByteArrayInputStream(body)
        }

        override fun getErrorStream(): InputStream = ByteArrayInputStream(errorBody)
    }
}
