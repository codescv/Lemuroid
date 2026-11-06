package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.view.KeyEvent
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualFastForwardTest {
    private class Harness(supported: Boolean = true) {
        val controller = FastForwardController(supported)
        val frameSpeeds = mutableListOf<Int>()
        val sentKeys = mutableListOf<Pair<Int, Int>>()
        val router =
            VirtualButtonRouter(
                onFastForwardToggle = { controller.toggle() },
                sendKeyEvent = { action, keyCode -> sentKeys += action to keyCode },
            )

        init {
            controller.bind { frameSpeeds += it }
            frameSpeeds.clear()
        }

        fun press(keyCode: Int = ComposeTouchLayouts.KEY_FAST_FORWARD) = router.route(keyCode, true)

        fun release(keyCode: Int = ComposeTouchLayouts.KEY_FAST_FORWARD) = router.route(keyCode, false)
    }

    @Test
    fun singleTapTogglesOnAndReleaseKeepsItOn() {
        val harness = Harness()

        harness.press()
        assertTrue(harness.controller.enabled.value)
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), harness.frameSpeeds)

        harness.release()
        assertTrue(harness.controller.enabled.value)
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), harness.frameSpeeds)
    }

    @Test
    fun secondTapTogglesOff() {
        val harness = Harness()

        harness.press()
        harness.release()
        harness.press()
        assertFalse(harness.controller.enabled.value)
        harness.release()
        assertFalse(harness.controller.enabled.value)

        assertEquals(
            listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED, FastForwardController.NORMAL_FRAME_SPEED),
            harness.frameSpeeds,
        )
    }

    @Test
    fun repeatedPressEventsWhileHeldDoNotToggleAgain() {
        val harness = Harness()

        harness.press()
        // Multi-touch: other fingers press and release buttons while fast forward is held.
        harness.press(KeyEvent.KEYCODE_BUTTON_A)
        harness.press()
        harness.release(KeyEvent.KEYCODE_BUTTON_A)
        harness.press()
        assertTrue(harness.controller.enabled.value)

        harness.release()
        assertTrue(harness.controller.enabled.value)
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), harness.frameSpeeds)
    }

    @Test
    fun tappingFastForwardWhileHoldingAnotherButtonKeepsThatButtonPressed() {
        val harness = Harness()

        harness.press(KeyEvent.KEYCODE_BUTTON_B)
        harness.press()
        harness.release()
        harness.release(KeyEvent.KEYCODE_BUTTON_B)

        assertTrue(harness.controller.enabled.value)
        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_B,
            ),
            harness.sentKeys,
        )
    }

    @Test
    fun syntheticFastForwardKeyIsNeverForwardedToTheCore() {
        val harness = Harness()

        repeat(3) {
            harness.press()
            harness.release()
        }

        assertTrue(harness.sentKeys.none { it.second == ComposeTouchLayouts.KEY_FAST_FORWARD })
        assertTrue(harness.sentKeys.isEmpty())
    }

    @Test
    fun regularAndMenuButtonsAreStillForwarded() {
        val harness = Harness()

        harness.press(KeyEvent.KEYCODE_BUTTON_MODE)
        harness.release(KeyEvent.KEYCODE_BUTTON_MODE)
        harness.press(KeyEvent.KEYCODE_BUTTON_A)
        harness.release(KeyEvent.KEYCODE_BUTTON_A)

        assertEquals(
            listOf(
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.ACTION_UP to KeyEvent.KEYCODE_BUTTON_A,
            ),
            harness.sentKeys,
        )
        assertFalse(harness.controller.enabled.value)
    }

    @Test
    fun unsupportedSystemIgnoresTheVirtualButtonAndExplicitRequests() {
        val harness = Harness(supported = false)

        harness.press()
        harness.release()
        assertFalse(harness.controller.enabled.value)
        assertFalse(harness.controller.toggle())
        assertFalse(harness.controller.setEnabled(true))
        assertFalse(harness.controller.enabled.value)
        assertTrue(harness.sentKeys.isEmpty())
        assertTrue(harness.frameSpeeds.all { it == FastForwardController.NORMAL_FRAME_SPEED })
    }

    @Test
    fun resetAfterLostReleaseAllowsTheNextTapToToggle() {
        val harness = Harness()

        harness.press()
        // The pad was disposed while the finger was down: the release never arrives.
        harness.router.reset()
        harness.press()

        assertFalse(harness.controller.enabled.value)
    }

    @Test
    fun menuSwitchShortcutAndTouchButtonShareTheSameState() {
        val harness = Harness()

        // Game menu switch.
        harness.controller.setEnabled(true)
        assertTrue(harness.controller.enabled.value)

        // Touch button turns it off.
        harness.press()
        harness.release()
        assertFalse(harness.controller.enabled.value)

        // Gamepad shortcut turns it on again.
        assertTrue(harness.controller.toggle())
        assertTrue(harness.controller.enabled.value)

        // Game menu switch with an unchanged value keeps it on.
        harness.controller.setEnabled(true)
        assertTrue(harness.controller.enabled.value)

        assertEquals(
            listOf(
                FastForwardController.FAST_FORWARD_FRAME_SPEED,
                FastForwardController.NORMAL_FRAME_SPEED,
                FastForwardController.FAST_FORWARD_FRAME_SPEED,
                FastForwardController.FAST_FORWARD_FRAME_SPEED,
            ),
            harness.frameSpeeds,
        )
    }

    @Test
    fun bindingANewViewAppliesTheCurrentStateAndUnbindStopsUpdates() {
        val controller = FastForwardController(supported = true)
        controller.setEnabled(true)

        val oldView = mutableListOf<Int>()
        controller.bind { oldView += it }
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), oldView)

        // The view is recreated: the old one must not receive updates anymore.
        controller.unbind()
        controller.setEnabled(false)
        controller.setEnabled(true)
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), oldView)

        val newView = mutableListOf<Int>()
        controller.bind { newView += it }
        assertEquals(listOf(FastForwardController.FAST_FORWARD_FRAME_SPEED), newView)
        assertEquals(FastForwardController.FAST_FORWARD_FRAME_SPEED, controller.frameSpeed)
    }
}
