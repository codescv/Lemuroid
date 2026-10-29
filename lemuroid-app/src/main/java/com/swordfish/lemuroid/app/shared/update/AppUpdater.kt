package com.swordfish.lemuroid.app.shared.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.zip.ZipFile

/**
 * Blocking HTTP client that checks GitHub Releases for updates and downloads APK assets.
 * Call all methods on `Dispatchers.IO`. [openConnection] and the URL parameters act as test seams.
 */
class AppUpdater(
    private val latestReleaseUrl: String = DEFAULT_LATEST_RELEASE_URL,
    private val releasesListUrl: String = DEFAULT_RELEASES_LIST_URL,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    companion object {
        private const val DEFAULT_LATEST_RELEASE_URL =
            "https://api.github.com/repos/codescv/Lemuroid/releases/latest"
        private const val DEFAULT_RELEASES_LIST_URL =
            "https://api.github.com/repos/codescv/Lemuroid/releases?per_page=5"

        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val APK_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_JSON_BYTES = 2 * 1024 * 1024
        const val MAX_UNKNOWN_SIZE_BYTES = 512L * 1024 * 1024

        private const val PROGRESS_INTERVAL_MS = 100L
        private const val PROGRESS_INTERVAL_BYTES = 256L * 1024
    }

    /**
     * Queries `/releases/latest` (falling back to `/releases?per_page=5` on HTTP 404 when all
     * releases are marked pre-release), parses the release metadata, and compares against
     * [currentVersion] with `-DEBUG` stripped.
     */
    fun checkLatestRelease(
        currentVersion: String,
        cancel: CancelToken? = null,
        timeoutMs: Int = 15_000,
    ): UpdateCheckResult {
        val baseVersion = SemanticVersion.stripDebugSuffix(currentVersion) ?: currentVersion
        val headers =
            mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to userAgent(currentVersion),
            )
        val jsonText =
            try {
                fetchJson(latestReleaseUrl, headers, cancel, timeoutMs)
            } catch (e: UpdateException) {
                if (e.code == UpdateErrorCode.NOT_FOUND && releasesListUrl.isNotEmpty()) {
                    fetchJson(releasesListUrl, headers, cancel, timeoutMs)
                } else {
                    throw e
                }
            }
        val latest = ReleaseJson.parse(jsonText)
        return UpdateCheckResult(
            currentVersion = baseVersion,
            latest = latest,
            updateAvailable = SemanticVersion.isNewerVersion(baseVersion, latest.tagName),
        )
    }

    /**
     * Downloads [url] (following up to [MAX_REDIRECTS] redirects) into `destFile.tmp`, verifies
     * length + ZIP/APK structure (`PK\x03\x04` + `AndroidManifest.xml`) + optional [validator]
     * (package name, versionCode, signing certificate), and atomically renames to [destFile].
     * Any failure or cancellation deletes `.tmp` and never leaves a partial file behind.
     */
    fun downloadApk(
        url: String,
        destFile: File,
        cancelToken: CancelToken? = null,
        connectTimeoutMs: Int = 20_000,
        readTimeoutMs: Int = 60_000,
        expectedSize: Long = -1L,
        validator: ((File) -> Unit)? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File {
        destFile.parentFile?.mkdirs()
        val tmp = File(destFile.path + ".tmp")
        tmp.delete()
        val headers =
            mapOf(
                "Accept" to "application/octet-stream",
                "Accept-Encoding" to "identity",
                "User-Agent" to userAgent(null),
            )
        var conn: HttpURLConnection? = null
        var ok = false
        try {
            conn = openFollowingRedirects(url, headers, cancelToken, connectTimeoutMs, readTimeoutMs)
            val status = conn.responseCode
            if (status != 200) throw httpError(conn, status)
            val contentType = conn.contentType.orEmpty()
            if (contentType.trim().startsWith("text/html", ignoreCase = true)) {
                throw UpdateException(UpdateErrorCode.CORRUPT_DOWNLOAD, "Server returned text/html instead of APK")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            val written = streamToFile(conn.inputStream, tmp, total, cancelToken, onProgress)
            verifyZipApk(tmp, written, total, expectedSize)
            validator?.invoke(tmp)
            destFile.delete()
            if (!tmp.renameTo(destFile)) {
                throw UpdateException(UpdateErrorCode.NETWORK, "Failed to rename downloaded APK to ${destFile.name}")
            }
            ok = true
            return destFile
        } catch (e: Exception) {
            throw mapError(e, cancelToken, readTimeoutMs)
        } finally {
            cancelToken?.detach()
            conn?.disconnect()
            if (!ok) tmp.delete()
        }
    }

    private fun fetchJson(
        url: String,
        headers: Map<String, String>,
        cancel: CancelToken?,
        timeoutMs: Int,
    ): String {
        var conn: HttpURLConnection? = null
        try {
            conn = openFollowingRedirects(url, headers, cancel, timeoutMs, timeoutMs)
            val status = conn.responseCode
            if (status !in 200..299) throw httpError(conn, status)
            val text = readLimited(conn.inputStream, MAX_JSON_BYTES, cancel)
            if (cancel?.isCancelled == true) throw cancelled()
            return text
        } catch (e: Exception) {
            throw mapError(e, cancel, timeoutMs)
        } finally {
            cancel?.detach()
            conn?.disconnect()
        }
    }

    private fun streamToFile(
        input: InputStream,
        tmp: File,
        total: Long,
        cancel: CancelToken?,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        var written = 0L
        var lastReportBytes = 0L
        var lastReportAt = 0L
        var reported = false
        input.use { ins ->
            val fos =
                try {
                    tmp.outputStream()
                } catch (e: IOException) {
                    throw UpdateException(UpdateErrorCode.NETWORK, "Cannot open temp file for writing", e)
                }
            fos.use { out ->
                val buf = ByteArray(BUFFER_SIZE)
                while (true) {
                    if (cancel?.isCancelled == true) throw cancelled()
                    val n =
                        try {
                            ins.read(buf)
                        } catch (e: SocketTimeoutException) {
                            throw e
                        } catch (e: IOException) {
                            if (cancel?.isCancelled == true) throw cancelled()
                            throw UpdateException(
                                UpdateErrorCode.CORRUPT_DOWNLOAD,
                                "Download stream interrupted at $written bytes",
                                e,
                            )
                        }
                    if (n < 0) break
                    if (n == 0) continue
                    try {
                        out.write(buf, 0, n)
                    } catch (e: IOException) {
                        throw UpdateException(UpdateErrorCode.NETWORK, "Disk write failed", e)
                    }
                    written += n
                    if (total > 0 && written > total) {
                        throw UpdateException(
                            UpdateErrorCode.CORRUPT_DOWNLOAD,
                            "Download exceeded expected length ($written > $total)",
                        )
                    }
                    if (total <= 0 && written > MAX_UNKNOWN_SIZE_BYTES) {
                        throw UpdateException(
                            UpdateErrorCode.CORRUPT_DOWNLOAD,
                            "Download exceeded safety cap ($MAX_UNKNOWN_SIZE_BYTES bytes)",
                        )
                    }
                    val now = System.currentTimeMillis()
                    if (
                        !reported ||
                        now - lastReportAt >= PROGRESS_INTERVAL_MS ||
                        written - lastReportBytes >= PROGRESS_INTERVAL_BYTES
                    ) {
                        onProgress(written, total)
                        reported = true
                        lastReportAt = now
                        lastReportBytes = written
                    }
                }
                try {
                    out.flush()
                    out.fd.sync()
                } catch (e: IOException) {
                    throw UpdateException(UpdateErrorCode.NETWORK, "Failed to sync downloaded file", e)
                }
            }
        }
        if (cancel?.isCancelled == true) throw cancelled()
        if (lastReportBytes != written || !reported) {
            onProgress(written, total)
        }
        return written
    }

    private fun verifyZipApk(
        tmp: File,
        written: Long,
        total: Long,
        expectedSize: Long,
    ) {
        if (total > 0 && written != total) {
            throw UpdateException(
                UpdateErrorCode.CORRUPT_DOWNLOAD,
                "Incomplete download ($written / $total bytes)",
            )
        }
        if (expectedSize > 0 && written != expectedSize) {
            throw UpdateException(
                UpdateErrorCode.CORRUPT_DOWNLOAD,
                "Size mismatch ($written != expected $expectedSize bytes)",
            )
        }
        val head = ByteArray(4)
        val n = if (written >= 4) tmp.inputStream().use { readFully(it, head) } else 0
        if (n < 4 || !head.contentEquals(APK_MAGIC)) {
            throw UpdateException(UpdateErrorCode.CORRUPT_DOWNLOAD, "Missing ZIP/APK magic header")
        }
        val hasManifest =
            try {
                ZipFile(tmp).use { it.getEntry("AndroidManifest.xml") != null }
            } catch (e: Exception) {
                throw UpdateException(UpdateErrorCode.CORRUPT_DOWNLOAD, "Corrupt or truncated ZIP archive", e)
            }
        if (!hasManifest) {
            throw UpdateException(UpdateErrorCode.CORRUPT_DOWNLOAD, "ZIP archive has no AndroidManifest.xml")
        }
    }

    /**
     * Follows up to [MAX_REDIRECTS] HTTP redirects manually across hosts (e.g. `github.com` ->
     * `objects.githubusercontent.com`), refusing non-http(s) schemes and `https -> http` downgrades.
     */
    private fun openFollowingRedirects(
        url: String,
        headers: Map<String, String>,
        cancel: CancelToken?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpURLConnection {
        @Suppress("DEPRECATION")
        var current =
            try {
                URL(url)
            } catch (e: MalformedURLException) {
                throw UpdateException(UpdateErrorCode.NETWORK, "Invalid URL: $url", e)
            }
        requireHttpScheme(current)
        var hops = 0
        while (true) {
            if (cancel?.isCancelled == true) throw cancelled()
            val conn = openConnection(current)
            var handedOff = false
            try {
                cancel?.attach { conn.disconnect() }
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = connectTimeoutMs.coerceAtLeast(1_000)
                conn.readTimeout = readTimeoutMs.coerceAtLeast(1_000)
                headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
                val status = conn.responseCode
                if (status !in REDIRECT_CODES) {
                    handedOff = true
                    return conn
                }
                val location = conn.getHeaderField("Location")?.trim()
                if (location.isNullOrEmpty()) {
                    throw UpdateException(
                        UpdateErrorCode.HTTP,
                        "Redirect missing Location header (HTTP $status)",
                        httpStatus = status,
                    )
                }
                if (++hops > MAX_REDIRECTS) {
                    throw UpdateException(UpdateErrorCode.NETWORK, "Too many redirects (>$MAX_REDIRECTS)")
                }
                @Suppress("DEPRECATION")
                val next =
                    try {
                        URL(current, location)
                    } catch (e: MalformedURLException) {
                        throw UpdateException(
                            UpdateErrorCode.HTTP,
                            "Malformed redirect Location: $location",
                            e,
                            status,
                        )
                    }
                requireHttpScheme(next)
                if (current.protocol.equals("https", ignoreCase = true) &&
                    next.protocol.equals("http", ignoreCase = true)
                ) {
                    throw UpdateException(UpdateErrorCode.NETWORK, "Insecure redirect downgrade (https -> http)")
                }
                current = next
            } finally {
                if (!handedOff) {
                    cancel?.detach()
                    conn.disconnect()
                }
            }
        }
    }

    private fun httpError(
        conn: HttpURLConnection,
        status: Int,
    ): UpdateException {
        val body = runCatching { conn.errorStream?.use { readPrefix(it, 4096) } }.getOrNull().orEmpty()
        val rateLimited =
            status == 429 ||
                (
                    status == 403 &&
                        (
                            conn.getHeaderField("X-RateLimit-Remaining")?.trim() == "0" ||
                                body.contains("rate limit", ignoreCase = true)
                        )
                )
        return when {
            rateLimited -> UpdateException(UpdateErrorCode.RATE_LIMITED, "GitHub API rate limited", httpStatus = status)
            status == 404 -> UpdateException(UpdateErrorCode.NOT_FOUND, "Release not found (404)", httpStatus = 404)
            else -> UpdateException(UpdateErrorCode.HTTP, "HTTP $status: ${body.trim().take(160)}", httpStatus = status)
        }
    }

    private fun mapError(
        e: Exception,
        cancel: CancelToken?,
        timeoutMs: Int,
    ): UpdateException {
        if (cancel?.isCancelled == true) {
            return if (e is UpdateException && e.code == UpdateErrorCode.CANCELLED) {
                e
            } else {
                UpdateException(UpdateErrorCode.CANCELLED, "Cancelled", e)
            }
        }
        if (e is UpdateException) return e
        return when (e) {
            is SocketTimeoutException -> UpdateException(UpdateErrorCode.TIMEOUT, "Timed out after $timeoutMs ms", e)
            is UnknownServiceException -> UpdateException(UpdateErrorCode.NETWORK, "Cleartext HTTP not permitted", e)
            is UnknownHostException -> UpdateException(UpdateErrorCode.NETWORK, "Unknown host: ${e.message}", e)
            is IOException -> UpdateException(UpdateErrorCode.NETWORK, e.message ?: "Network I/O error", e)
            else -> UpdateException(UpdateErrorCode.NETWORK, "${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun cancelled() = UpdateException(UpdateErrorCode.CANCELLED, "Cancelled")

    private fun requireHttpScheme(url: URL) {
        val protocol = url.protocol.lowercase()
        if (protocol != "http" && protocol != "https") {
            throw UpdateException(UpdateErrorCode.NETWORK, "Unsupported URL scheme: $protocol")
        }
        if (url.host.isNullOrEmpty()) {
            throw UpdateException(UpdateErrorCode.NETWORK, "URL missing host: $url")
        }
    }

    private fun userAgent(version: String?): String {
        val sanitized = version?.trim()?.replace(Regex("[^0-9A-Za-z._-]"), "")?.takeIf { it.isNotEmpty() }
        return if (sanitized != null) "Lemuroid/$sanitized" else "Lemuroid"
    }

    private fun readLimited(
        input: InputStream,
        limit: Int,
        cancel: CancelToken?,
    ): String {
        input.use { ins ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                if (cancel?.isCancelled == true) throw cancelled()
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) {
                    throw UpdateException(UpdateErrorCode.INVALID_RESPONSE, "Release JSON exceeds $limit bytes")
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun readPrefix(
        input: InputStream,
        limit: Int,
    ): String {
        val buf = ByteArray(limit)
        val n = readFully(input, buf)
        return String(buf, 0, n, Charsets.UTF_8)
    }

    private fun readFully(
        input: InputStream,
        buf: ByteArray,
    ): Int {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }
}
