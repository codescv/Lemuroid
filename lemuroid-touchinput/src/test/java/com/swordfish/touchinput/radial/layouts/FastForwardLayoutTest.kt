package com.swordfish.touchinput.radial.layouts

import android.view.KeyEvent
import com.swordfish.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.swordfish.touchinput.radial.layouts.shared.FastForwardPlacement
import com.swordfish.touchinput.radial.settings.TouchControllerID
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Verifies that the fast forward button is placed on every touch layout without overlapping other controls and
 * without breaking the left/right pad symmetry. Geometry mirrors PadKit's `LayoutRadial`: secondary dials sit
 * around a primary dial of diameter 1, with a base size that fits 12 dials around the circumference.
 */
class FastForwardLayoutTest {
    /** A secondary dial: [angle] is the relative position for a given rotation in degrees. */
    private class Dial(
        val name: String,
        val scale: Float = 1f,
        val angle: (Float) -> Float,
    )

    private class PadSpec(
        val left: List<Dial>,
        val right: List<Dial>,
    )

    @Test
    fun syntheticFastForwardKeyNeverCollidesWithRealKeys() {
        val layoutKeys =
            listOf(
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_THUMBR,
            )
        assertFalse(layoutKeys.contains(ComposeTouchLayouts.KEY_FAST_FORWARD))
        // Android key codes are small (< 1000); keep the synthetic id far above any present or future one.
        assertTrue(ComposeTouchLayouts.KEY_FAST_FORWARD > 0xFFFF)
    }

    @Test
    fun fastForwardNeverOverlapsAnyOtherControlAtAnyRotation() {
        for (id in TouchControllerID.values()) {
            val spec = padSpec(id)
            val fastForward = spec.left.single { it.name == FAST_FORWARD }
            val others = spec.left.filter { it.name != FAST_FORWARD }

            for (step in 0..ROTATION_STEPS) {
                val rotation = TouchControllerSettingsManager.MAX_ROTATION * step / ROTATION_STEPS
                for (other in others) {
                    val gap = gap(fastForward, other, rotation)
                    assertTrue(
                        "$id: fast forward overlaps ${other.name} at rotation $rotation (gap $gap)",
                        gap >= -EPSILON,
                    )
                }
            }
        }
    }

    @Test
    fun padsWithRelocatedFastForwardKeepSymmetricBounds() {
        val relocated =
            listOf(
                TouchControllerID.DESMUME,
                TouchControllerID.MELONDS,
                TouchControllerID.LYNX,
                TouchControllerID.DOS,
            )
        for (id in relocated) {
            val spec = padSpec(id)
            val left = bounds(spec.left, rotation = 0f, baseRotationSign = 1f)
            val right = bounds(spec.right, rotation = 0f, baseRotationSign = -1f)
            assertEquals("$id width", left.width, right.width, EPSILON)
            assertEquals("$id height", left.height, right.height, EPSILON)
        }
    }

    @Test
    fun defaultFastForwardMirrorsTheMenuButton() {
        for (step in 0..ROTATION_STEPS) {
            val rotation = step.toFloat() / ROTATION_STEPS
            val rotationDegrees = rotation * TouchControllerSettingsManager.MAX_ROTATION
            val menuEffective = -60f + 2f * rotationDegrees - rotationDegrees
            val fastForwardEffective = FastForwardPlacement.default(rotation) + rotationDegrees
            assertEquals(
                normalize(180f - menuEffective).toDouble(),
                normalize(fastForwardEffective).toDouble(),
                EPSILON,
            )
        }
    }

    @Test
    fun everyLeftLayoutDeclaresExactlyOneFastForwardButtonAndRightLayoutsNone() {
        val layoutsDir = File("src/main/java/com/swordfish/touchinput/radial/layouts")
        val files = layoutsDir.listFiles { file -> file.extension == "kt" && file.name != "BaseLayout.kt" }!!
        assertEquals(24, files.size)

        val functionRegex = Regex("""fun PadKitScope\.(\w+)\(""")
        var leftCount = 0
        for (file in files) {
            val source = file.readText()
            val functions = functionRegex.findAll(source).toList()
            functions.forEachIndexed { index, match ->
                val end = functions.getOrNull(index + 1)?.range?.first ?: source.length
                val body = source.substring(match.range.first, end)
                val occurrences = Regex("""SecondaryButtonFastForward\(""").findAll(body).count()
                val name = match.groupValues[1]
                if (name.endsWith("Left")) {
                    leftCount++
                    assertEquals("$name must declare one fast forward button", 1, occurrences)
                } else {
                    assertEquals("$name must not declare a fast forward button", 0, occurrences)
                }
            }
        }
        assertEquals(TouchControllerID.values().size, leftCount)
    }

    private fun padSpec(id: TouchControllerID): PadSpec {
        val fastForward = Dial(FAST_FORWARD) { FastForwardPlacement.default(it / MAX) }
        val menu = Dial("Menu") { -60f + 2f * it }
        return when (id) {
            TouchControllerID.GB,
            TouchControllerID.NES,
            TouchControllerID.ATARI7800,
            TouchControllerID.GENESIS_3,
            TouchControllerID.PCE,
            -> simple(listOf(fixed("Select", 120f), fastForward))

            TouchControllerID.GG,
            TouchControllerID.SMS,
            TouchControllerID.NGP,
            TouchControllerID.WS_LANDSCAPE,
            TouchControllerID.WS_PORTRAIT,
            -> simple(listOf(fixed("Placeholder", 120f), fastForward))

            TouchControllerID.GBA,
            TouchControllerID.SNES,
            ->
                simple(
                    listOf(fixed("L", 120f), fixed("Select", 60f), fastForward),
                )

            TouchControllerID.GENESIS_6 ->
                simple(
                    listOf(fixed("Select", 120f), fixed("Start", 90f), fastForward),
                )

            TouchControllerID.ARCADE_4 ->
                simple(listOf(fixed("Coin", 120f), fastForward))

            TouchControllerID.ARCADE_6 ->
                simple(
                    listOf(fixed("Coin", 120f), fixed("Start", 90f), fastForward),
                )

            TouchControllerID.ATARI2600 ->
                simple(
                    listOf(fixed("DIFF.A", 120f), fixed("DIFF.B", 60f), fastForward),
                )

            TouchControllerID.PSX ->
                simple(
                    listOf(fixed("L1", 90f), fixed("L2", 120f), fixed("Select", 60f), fastForward),
                )

            TouchControllerID.PSX_DUALSHOCK ->
                simple(
                    listOf(fixed("L1", 90f), fixed("L2", 120f), fixed("Select", 60f), analogLeft(), fastForward),
                )

            TouchControllerID.N64 ->
                simple(
                    listOf(analogLeft(), fixed("L", 120f), fixed("Z", 60f), fastForward),
                )

            TouchControllerID.PSP,
            TouchControllerID.NINTENDO_3DS,
            ->
                simple(
                    listOf(fixed("L", 120f), fixed("Select", 60f), fastForward, analogLeft()),
                )

            TouchControllerID.DESMUME,
            TouchControllerID.MELONDS,
            ->
                PadSpec(
                    left =
                        listOf(
                            fixed("L", 120f),
                            fixed("Select", 60f),
                            fixed("Microphone", -120f),
                            fixed("Close screen", -60f),
                            Dial(FAST_FORWARD) { FastForwardPlacement.DUAL_SCREEN },
                        ),
                    right =
                        listOf(
                            fixed("R", 60f),
                            fixed("Start", 120f),
                            menu,
                            fixed("Placeholder", -120f),
                            fixed("Fast forward mirror", 180f - FastForwardPlacement.DUAL_SCREEN),
                        ),
                )

            TouchControllerID.LYNX ->
                PadSpec(
                    left =
                        listOf(
                            fixed("Option 1", 120f),
                            fixed("Option 2", -120f),
                            Dial(FAST_FORWARD) { FastForwardPlacement.LYNX },
                        ),
                    right =
                        listOf(
                            fixed("Start", 60f),
                            menu,
                            fixed("Fast forward mirror", 180f - FastForwardPlacement.LYNX),
                        ),
                )

            TouchControllerID.DOS ->
                PadSpec(
                    left =
                        listOf(
                            fixed("L1", 90f),
                            fixed("L2", 120f),
                            fixed("Select", 60f),
                            analogLeft(),
                            Dial("Keyboard") { -120f - 2f * it },
                            Dial(FAST_FORWARD) { FastForwardPlacement.DOS },
                        ),
                    right =
                        listOf(
                            fixed("R1", 90f),
                            fixed("R2", 60f),
                            fixed("Start", 120f),
                            analogRight(),
                            menu,
                            fixed("Fast forward mirror", 180f - FastForwardPlacement.DOS),
                        ),
                )
        }
    }

    /** Layouts keeping the default placement: only the left pad matters for overlap checks. */
    private fun simple(left: List<Dial>) = PadSpec(left, right = emptyList())

    private fun fixed(
        name: String,
        angle: Float,
    ) = Dial(name) { angle }

    private fun analogLeft() = Dial("Left analog", scale = 2f) { -80f }

    private fun analogRight() = Dial("Right analog", scale = 2f) { 80f - 180f }

    private data class Circle(val x: Double, val y: Double, val radius: Double)

    private data class Bounds(val width: Double, val height: Double)

    private fun circle(
        dial: Dial,
        rotation: Float,
        baseRotationSign: Float,
    ): Circle {
        val effective = dial.angle(rotation) + baseRotationSign * rotation
        val radians = -effective * PI / 180.0
        val size = BASE_SCALE * dial.scale
        val distance = 0.5 * (1.0 + size)
        return Circle(cos(radians) * distance, sin(radians) * distance, size / 2.0)
    }

    private fun gap(
        a: Dial,
        b: Dial,
        rotation: Float,
    ): Double {
        val ca = circle(a, rotation, baseRotationSign = 1f)
        val cb = circle(b, rotation, baseRotationSign = 1f)
        return hypot(ca.x - cb.x, ca.y - cb.y) - ca.radius - cb.radius
    }

    private fun bounds(
        dials: List<Dial>,
        rotation: Float,
        baseRotationSign: Float,
    ): Bounds {
        var left = -0.5
        var right = 0.5
        var top = -0.5
        var bottom = 0.5
        for (dial in dials) {
            val c = circle(dial, rotation, baseRotationSign)
            left = min(left, c.x - c.radius)
            right = max(right, c.x + c.radius)
            top = min(top, c.y - c.radius)
            bottom = max(bottom, c.y + c.radius)
        }
        return Bounds(right - left, bottom - top)
    }

    private fun normalize(angle: Float): Float {
        var result = angle
        while (result > 180f) result -= 360f
        while (result <= -180f) result += 360f
        return if (abs(result) < EPSILON) 0f else result
    }

    companion object {
        private const val FAST_FORWARD = "Fast forward"
        private const val ROTATION_STEPS = 45
        private const val EPSILON = 1e-4
        private const val MAX = TouchControllerSettingsManager.MAX_ROTATION

        private val BASE_SCALE: Double =
            sin(PI / 12).let { it / (1.0 - it) }
    }
}
