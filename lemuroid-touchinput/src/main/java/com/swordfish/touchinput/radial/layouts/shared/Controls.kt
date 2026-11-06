package com.swordfish.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LocalLemuroidFastForward
import com.swordfish.touchinput.radial.controls.LemuroidControlAnalog
import com.swordfish.touchinput.radial.controls.LemuroidControlButton
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonSelect(position: Int = 0) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f - 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_select,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL1() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL2() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
        label = "L2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR1() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR2() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
        label = "R2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonStart(position: Int = 0) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(60f + 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        icon = R.drawable.button_start,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenu(settings: TouchControllerSettingsManager.Settings) {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(-60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        icon = R.drawable.button_menu,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenuPlaceholder(settings: TouchControllerSettingsManager.Settings) {
    Box(
        modifier =
            Modifier.radialPosition(
                -120f - 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
            ),
    )
}

/**
 * Fast forward toggle placed on the left pad at the mirrored position of [SecondaryButtonMenu], i.e.
 * where [SecondaryButtonMenuPlaceholder] used to keep the two pads symmetric.
 */
context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonFastForward(settings: TouchControllerSettingsManager.Settings) {
    SecondaryButtonFastForward(positionInDegrees = FastForwardPlacement.default(settings.rotation))
}

/**
 * Fast forward toggle at an explicit [positionInDegrees]. When fast forward is not supported by the current
 * system an empty [Box] is placed instead, so the radial layout bounds never depend on the support flag.
 */
context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonFastForward(positionInDegrees: Float) {
    val fastForward = LocalLemuroidFastForward.current
    if (!fastForward.supported) {
        Box(modifier = Modifier.radialPosition(positionInDegrees))
        return
    }

    LemuroidControlButton(
        modifier = Modifier.radialPosition(positionInDegrees),
        id = Id.Key(ComposeTouchLayouts.KEY_FAST_FORWARD),
        icon = R.drawable.button_fast_forward,
        active = fastForward.active,
    )
}

/**
 * Left pad secondary dial angles used by [SecondaryButtonFastForward]. Angles are relative to the pad base
 * rotation, like any other `radialPosition`.
 */
object FastForwardPlacement {
    /** Bottom outer corner of the left pad, mirroring [SecondaryButtonMenu] on the right pad. */
    fun default(rotation: Float): Float {
        return -120f - 2f * rotation * TouchControllerSettingsManager.MAX_ROTATION
    }

    /** Desmume and MelonDS: bottom, between the microphone (-120) and the close screen (-60) buttons. */
    const val DUAL_SCREEN = -90f

    /** Lynx: bottom inner corner, since OPTION 2 already sits at the bottom outer corner (-120). */
    const val LYNX = -60f

    /**
     * DOS: inner side, between the d-pad and the left analog stick (-80). The bottom outer corner is used by
     * the keyboard button and the top is filled by L1, L2 and Select.
     */
    const val DOS = -30f
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogLeft() {
    LemuroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(-80f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogRight() {
    LemuroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(+80f - 180f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonCoin() {
    LemuroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_coin,
    )
}

object ComposeTouchLayouts {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_LEFT_STICK = 1
    const val MOTION_SOURCE_RIGHT_STICK = 2
    const val MOTION_SOURCE_DPAD_AND_LEFT_STICK = 3
    const val MOTION_SOURCE_RIGHT_DPAD = 4

    /**
     * Synthetic, touch-only key id of the fast forward button. It is far above every Android
     * `KeyEvent.KEYCODE_*` value, so it can never collide with a real gamepad or keyboard key, and the app
     * consumes it instead of forwarding it to the core.
     */
    const val KEY_FAST_FORWARD = 0x00F0_0001
}
