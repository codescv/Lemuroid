package com.swordfish.lemuroid.app.shared.update

import java.io.IOException

/** Failure categories of [AppUpdater] and [UpdateManager], mapped to localized string resources in UI. */
enum class UpdateErrorCode {
    NETWORK,
    TIMEOUT,
    HTTP,
    RATE_LIMITED,
    NOT_FOUND,
    INVALID_RESPONSE,
    CORRUPT_DOWNLOAD,
    WRONG_PACKAGE,
    NOT_NEWER,
    SIGNATURE_MISMATCH,
    NO_APK,
    INSTALLER,
    CANCELLED,
}

/** Diagnostic exception thrown by [AppUpdater]; [detail] is for logs/tests, UI maps [code] to resources. */
class UpdateException(
    val code: UpdateErrorCode,
    val detail: String = "",
    cause: Throwable? = null,
    val httpStatus: Int = 0,
) : IOException(detail.ifEmpty { code.name }, cause)
