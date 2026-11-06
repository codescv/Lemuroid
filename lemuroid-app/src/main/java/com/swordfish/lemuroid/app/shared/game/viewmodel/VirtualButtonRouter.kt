package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.view.KeyEvent
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts

/**
 * Routes virtual (touch) button events.
 *
 * Regular buttons are forwarded to the core through [sendKeyEvent]. The synthetic fast forward key
 * ([ComposeTouchLayouts.KEY_FAST_FORWARD]) is consumed here and never forwarded: it calls [onFastForwardToggle]
 * only on the rising edge of the press, so releasing the button or re-emitted press events (e.g. while other
 * fingers are on the pad) never toggle it again.
 */
class VirtualButtonRouter(
    private val onFastForwardToggle: () -> Unit,
    private val sendKeyEvent: (action: Int, keyCode: Int) -> Unit,
) {
    private var fastForwardPressed = false

    fun route(
        keyCode: Int,
        pressed: Boolean,
    ) {
        if (keyCode == ComposeTouchLayouts.KEY_FAST_FORWARD) {
            onFastForwardButton(pressed)
            return
        }

        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        sendKeyEvent(action, keyCode)
    }

    /** Forgets the tracked press, e.g. when the virtual pad is disposed and its pending release is lost. */
    fun reset() {
        fastForwardPressed = false
    }

    private fun onFastForwardButton(pressed: Boolean) {
        val wasPressed = fastForwardPressed
        fastForwardPressed = pressed
        if (pressed && !wasPressed) {
            onFastForwardToggle()
        }
    }
}
