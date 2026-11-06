package com.swordfish.touchinput.radial

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Fast forward state exposed to the touch layouts.
 *
 * @param supported Whether the running system supports fast forward. When false the virtual button is replaced
 * by an empty placeholder with the same footprint.
 * @param active Whether fast forward is currently enabled, used to highlight the virtual button.
 */
@Immutable
data class LemuroidFastForwardState(
    val supported: Boolean = false,
    val active: Boolean = false,
) {
    companion object {
        val Disabled = LemuroidFastForwardState()
    }
}

/** Defaults to [LemuroidFastForwardState.Disabled] so previews and standalone callers keep working. */
val LocalLemuroidFastForward =
    compositionLocalOf { LemuroidFastForwardState.Disabled }
