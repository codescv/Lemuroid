package com.swordfish.lemuroid.app.shared.game.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the fast forward state of a game session.
 *
 * Every entry point (virtual touch button, game menu switch, gamepad shortcut) goes through [setEnabled] or
 * [toggle], and the bound frame speed sink (the current `GLRetroView`) is always updated with the resulting
 * value, so the UI indicator and the emulation speed can never diverge.
 */
class FastForwardController(val supported: Boolean) {
    private val enabledState = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    private var frameSpeedSink: ((Int) -> Unit)? = null

    val frameSpeed: Int
        get() = frameSpeedFor(enabledState.value)

    /** Binds the frame speed [sink] of a newly created game view and immediately applies the current state. */
    fun bind(sink: (Int) -> Unit) {
        frameSpeedSink = sink
        sink(frameSpeed)
    }

    fun unbind() {
        frameSpeedSink = null
    }

    /** Sets fast forward to [enabled] (always `false` when unsupported) and returns the resulting state. */
    fun setEnabled(enabled: Boolean): Boolean {
        val target = enabled && supported
        enabledState.value = target
        frameSpeedSink?.invoke(frameSpeedFor(target))
        return target
    }

    /** Toggles fast forward and returns the resulting state. It is a no-op when unsupported. */
    fun toggle(): Boolean {
        if (!supported) return false
        return setEnabled(!enabledState.value)
    }

    companion object {
        const val NORMAL_FRAME_SPEED = 1
        const val FAST_FORWARD_FRAME_SPEED = 2

        fun frameSpeedFor(enabled: Boolean): Int {
            return if (enabled) FAST_FORWARD_FRAME_SPEED else NORMAL_FRAME_SPEED
        }
    }
}
