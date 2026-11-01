package com.swordfish.lemuroid.app.shared.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import java.net.URLEncoder

/**
 * Helper for locally persisted Agent feedback email configuration, email validation,
 * subject formatting, and `mailto:` URI generation.
 */
object AgentFeedbackHelper {
    const val PREF_KEY_AGENT_FEEDBACK_EMAIL = "agent_feedback_email"
    const val DEFAULT_EMAIL = ""
    const val EMAIL_PLACEHOLDER = "agent@example.com"
    const val PROJECT_PREFIX = "lemuroid"

    private const val MAX_EMAIL_LENGTH = 254
    private const val MAX_LOCAL_PART_LENGTH = 64
    private const val MAX_FALLBACK_SUMMARY_LENGTH = 60
    private const val DEFAULT_SUMMARY_FALLBACK = "Feedback"

    private val LOCAL_PART_REGEX = Regex("^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+\$")
    private val DOMAIN_LABEL_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val TLD_LABEL_REGEX = Regex("^[A-Za-z]{2,63}$")
    private val WHITESPACE_OR_CONTROL_REGEX = Regex("[\\s\\p{Cntrl}]")
    private val LINE_BREAK_RUN_REGEX = Regex("[\\r\\n]+")

    fun normalizeEmail(raw: String?): String = raw?.trim().orEmpty()

    /**
     * Validates a standard single-recipient email address without relying on Android framework stubs.
     * Rejects blank addresses, header-injection control chars, missing TLD, and malformed labels.
     */
    fun isValidEmail(raw: String?): Boolean {
        val candidate = normalizeEmail(raw)
        if (candidate.isEmpty() || candidate.length > MAX_EMAIL_LENGTH) return false
        if (WHITESPACE_OR_CONTROL_REGEX.containsMatchIn(candidate)) return false

        val atIndex = candidate.indexOf('@')
        if (atIndex <= 0 || atIndex != candidate.lastIndexOf('@') || atIndex == candidate.lastIndex) {
            return false
        }

        val local = candidate.substring(0, atIndex)
        val domain = candidate.substring(atIndex + 1)
        if (local.length > MAX_LOCAL_PART_LENGTH) return false
        if (local.startsWith('.') || local.endsWith('.') || local.contains("..")) return false
        if (!LOCAL_PART_REGEX.matches(local)) return false

        if (domain.startsWith('.') || domain.endsWith('.') || domain.contains("..")) return false
        val labels = domain.split('.')
        if (labels.size < 2) return false
        if (!labels.all { DOMAIN_LABEL_REGEX.matches(it) }) return false
        return TLD_LABEL_REGEX.matches(labels.last())
    }

    /** Normalizes an app `versionName` (such as `1.17.4` or `1.17.4-DEBUG`) to a `v1.17.4` tag. */
    fun normalizeVersionTag(versionName: String): String {
        val stripped =
            SemanticVersion.stripDebugSuffix(versionName)
                .orEmpty()
                .removePrefix("v")
                .removePrefix("V")
                .trim()
        return if (stripped.isEmpty()) "v0.0.0" else "v$stripped"
    }

    /**
     * Resolves a single-line summary from explicit [summary] input or falls back to the first
     * non-blank line of [details] when [summary] is blank.
     */
    fun resolveSummary(
        summary: String,
        details: String = "",
    ): String {
        val explicit = summary.trim().replace(LINE_BREAK_RUN_REGEX, " ").trim()
        if (explicit.isNotEmpty()) return explicit

        val firstDetailLine =
            details.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        return if (firstDetailLine.length > MAX_FALLBACK_SUMMARY_LENGTH) {
            firstDetailLine.take(MAX_FALLBACK_SUMMARY_LENGTH).trimEnd() + "…"
        } else {
            firstDetailLine
        }
    }

    /** Formats the email subject with the `lemuroid:` project prefix and normalized version tag. */
    fun formatSubject(
        summary: String,
        versionName: String = BuildConfig.VERSION_NAME,
    ): String {
        val tag = normalizeVersionTag(versionName)
        val cleanSummary =
            summary.trim()
                .replace(LINE_BREAK_RUN_REGEX, " ")
                .trim()
                .ifEmpty { DEFAULT_SUMMARY_FALLBACK }
        return "$PROJECT_PREFIX: [$tag] $cleanSummary"
    }

    /** Formats the email body combining user feedback details with app version metadata. */
    fun formatEmailBody(
        summary: String,
        details: String,
        versionName: String = BuildConfig.VERSION_NAME,
        versionCode: Int = BuildConfig.VERSION_CODE,
    ): String {
        val normalizedDetails = details.replace("\r\n", "\n").replace('\r', '\n').trim()
        val normalizedSummary = summary.trim().replace(LINE_BREAK_RUN_REGEX, " ").trim()
        val content = normalizedDetails.ifEmpty { normalizedSummary }
        val tag = normalizeVersionTag(versionName)
        return buildString {
            if (content.isNotEmpty()) {
                append(content)
                append("\n\n")
            }
            append("---\n")
            append("App: Lemuroid ")
            append(tag)
            append(" (")
            append(versionCode)
            append(")")
        }
    }

    /** Percent-encodes a `mailto:` query parameter value per RFC 6068 (`%20` instead of `+`). */
    internal fun encodeMailtoParam(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    /**
     * Builds an RFC 6068 `mailto:` URI for [email] with formatted `lemuroid:` subject
     * and optional [body]. Throws [IllegalArgumentException] if [email] is not valid.
     */
    fun buildMailtoUri(
        email: String,
        summary: String,
        body: String = "",
        versionName: String = BuildConfig.VERSION_NAME,
    ): String {
        val normalizedEmail = normalizeEmail(email)
        require(isValidEmail(normalizedEmail)) { "Invalid feedback email address" }

        val subject = formatSubject(summary = summary, versionName = versionName)
        val encodedSubject = encodeMailtoParam(subject)
        val normalizedBody = body.replace("\r\n", "\n").replace('\r', '\n').trim()

        return if (normalizedBody.isEmpty()) {
            "mailto:$normalizedEmail?subject=$encodedSubject"
        } else {
            "mailto:$normalizedEmail?subject=$encodedSubject&body=${encodeMailtoParam(normalizedBody)}"
        }
    }

    fun getSavedEmail(prefs: SharedPreferences): String =
        normalizeEmail(prefs.getString(PREF_KEY_AGENT_FEEDBACK_EMAIL, DEFAULT_EMAIL))

    fun getSavedEmail(context: Context): String = getSavedEmail(SharedPreferencesHelper.getSharedPreferences(context))

    /**
     * Saves a trimmed valid email (or empty string to clear) into local [SharedPreferences].
     * Returns `false` without mutating preferences when [email] is non-empty and invalid.
     */
    fun saveEmail(
        prefs: SharedPreferences,
        email: String,
    ): Boolean {
        val normalized = normalizeEmail(email)
        if (normalized.isNotEmpty() && !isValidEmail(normalized)) {
            return false
        }
        prefs.edit().putString(PREF_KEY_AGENT_FEEDBACK_EMAIL, normalized).apply()
        return true
    }

    fun saveEmail(
        context: Context,
        email: String,
    ): Boolean = saveEmail(SharedPreferencesHelper.getSharedPreferences(context), email)

    fun createSendToIntent(
        email: String,
        summary: String,
        body: String = "",
        versionName: String = BuildConfig.VERSION_NAME,
    ): Intent {
        val mailtoUri = buildMailtoUri(email = email, summary = summary, body = body, versionName = versionName)
        return Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUri))
    }

    fun launchFeedbackEmail(
        context: Context,
        email: String,
        summary: String,
        details: String,
        versionName: String = BuildConfig.VERSION_NAME,
        versionCode: Int = BuildConfig.VERSION_CODE,
    ): Boolean {
        val effectiveSummary = resolveSummary(summary, details)
        val bodyBase =
            formatEmailBody(
                summary = effectiveSummary,
                details = details,
                versionName = versionName,
                versionCode = versionCode,
            )
        val fullBody = "$bodyBase • Android ${Build.VERSION.SDK_INT}"
        val intent =
            createSendToIntent(
                email = email,
                summary = effectiveSummary,
                body = fullBody,
                versionName = versionName,
            )
        val hostActivity = context.findHostActivity()
        if (hostActivity == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            (hostActivity ?: context).startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.agent_feedback_no_email_app),
                Toast.LENGTH_LONG,
            ).show()
            false
        }
    }

    private tailrec fun Context.findHostActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findHostActivity()
            else -> null
        }
}
