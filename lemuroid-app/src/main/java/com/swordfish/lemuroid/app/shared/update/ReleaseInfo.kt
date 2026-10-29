package com.swordfish.lemuroid.app.shared.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One downloadable asset attached to a GitHub release. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String = "",
)

/** Parsed GitHub release metadata needed by the in-app updater. */
data class ReleaseInfo(
    val tagName: String,
    /** [tagName] without a leading `v`/`V`. */
    val version: String,
    val title: String,
    val body: String,
    val publishedAt: String? = null,
    val htmlUrl: String,
    /** Preferred APK asset ([ReleaseJson.pickApk]); null when the release has no `.apk`. */
    val apkAsset: ReleaseAsset?,
    val isPreRelease: Boolean = false,
)

/** Result of [AppUpdater.checkLatestRelease]. */
data class UpdateCheckResult(
    val currentVersion: String,
    val latest: ReleaseInfo,
    val updateAvailable: Boolean,
)

/**
 * Manual `JsonObject` parser for GitHub Releases payloads (avoids `@Serializable` codegen so R8
 * needs zero extra serializer rules).
 */
object ReleaseJson {
    const val DEFAULT_RELEASE_HTML_URL = "https://github.com/codescv/Lemuroid/releases"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val LEMUROID_RELEASE_APK = Regex("^lemuroid-.*-release\\.apk$", RegexOption.IGNORE_CASE)

    /**
     * Parses a single release object (`/releases/latest`) or a release array (`/releases?per_page=5`),
     * preferring the first non-draft non-prerelease entry and falling back to the first non-draft entry.
     */
    fun parse(text: String): ReleaseInfo {
        val root =
            try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                throw UpdateException(UpdateErrorCode.INVALID_RESPONSE, "Invalid release JSON", e)
            }

        val obj =
            when (root) {
                is JsonObject -> {
                    if (root.bool("draft")) {
                        throw UpdateException(UpdateErrorCode.NOT_FOUND, "Release is a draft")
                    }
                    root
                }
                is JsonArray -> {
                    if (root.isNotEmpty() && root.none { it is JsonObject }) {
                        throw UpdateException(UpdateErrorCode.INVALID_RESPONSE, "Release array contains non-objects")
                    }
                    val nonDraft = root.filterIsInstance<JsonObject>().filter { !it.bool("draft") }
                    nonDraft.firstOrNull { !it.bool("prerelease") }
                        ?: nonDraft.firstOrNull()
                        ?: throw UpdateException(UpdateErrorCode.NOT_FOUND, "No non-draft release found")
                }
                else -> throw UpdateException(UpdateErrorCode.INVALID_RESPONSE, "Release JSON is not an object")
            }

        val tag =
            obj.str("tag_name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw UpdateException(UpdateErrorCode.INVALID_RESPONSE, "Release JSON missing tag_name")

        val assets =
            (obj["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
                val assetObj = element as? JsonObject ?: return@mapNotNull null
                val name = assetObj.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val url = assetObj.str("browser_download_url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ReleaseAsset(
                    name = name,
                    downloadUrl = url,
                    sizeBytes = (assetObj["size"] as? JsonPrimitive)?.longOrNull ?: -1L,
                    contentType = assetObj.str("content_type").orEmpty(),
                )
            }

        return ReleaseInfo(
            tagName = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            title = obj.str("name")?.takeIf { it.isNotBlank() } ?: tag,
            body = obj.str("body").orEmpty(),
            publishedAt = obj.str("published_at"),
            htmlUrl = obj.str("html_url")?.takeIf { it.isNotBlank() } ?: DEFAULT_RELEASE_HTML_URL,
            apkAsset = pickApk(assets),
            isPreRelease = obj.bool("prerelease"),
        )
    }

    /**
     * Picks the best APK asset:
     * 1) `lemuroid-app-free-dynamic-release.apk`
     * 2) `^lemuroid-.*-release\.apk$` (case-insensitive)
     * 3) any `*.apk` (case-insensitive)
     */
    fun pickApk(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name.equals("lemuroid-app-free-dynamic-release.apk", ignoreCase = true) }
            ?: assets.firstOrNull { LEMUROID_RELEASE_APK.matches(it.name) }
            ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.str(key: String): String? {
        val el: JsonElement = this[key] ?: return null
        if (el is JsonNull) return null
        return (el as? JsonPrimitive)?.contentOrNull
    }
}
