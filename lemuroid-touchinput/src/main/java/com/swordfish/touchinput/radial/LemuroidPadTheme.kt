package com.swordfish.touchinput.radial

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun gray(
    luminosity: Float,
    opacity: Float,
): Color {
    return Color(luminosity, luminosity, luminosity, opacity)
}

/**
 * Colors of the virtual gamepad. Every parameter defaults to the original frosted glass look, so
 * `LemuroidPadTheme()` renders exactly like it always did; skins (e.g. the GBA skins) override only colors.
 *
 * Geometry ([padding], [foregroundPadding], shadow widths, corner radius) is intentionally not configurable:
 * switching theme must never change the measured size of the pads (and therefore the game viewport).
 *
 * The `primary*` colors are used by the main face buttons (e.g. GBA A/B) and default to the regular button
 * colors.
 */
@Immutable
class LemuroidPadTheme(
    val icons: Color = gray(0.0f, 0.50f),
    val iconsPressed: Color = gray(1.0f, 0.50f),
    val iconsActive: Color = gray(1.0f, 0.90f),
    val level3Fill: Color = gray(1.0f, 0.50f),
    val level3FillPressed: Color = gray(0.0f, 0.50f),
    val level3FillActive: Color = Color(1.0f, 0.60f, 0.0f, 0.75f),
    val level3Shadow: Color = DefaultShadowColor.copy(0.05f),
    val level3Stroke: Color = Color.Transparent,
    val primaryFill: Color = level3Fill,
    val primaryFillPressed: Color = level3FillPressed,
    val primaryIcons: Color = icons,
    val primaryIconsPressed: Color = iconsPressed,
    val primaryStroke: Color = level3Stroke,
    val level2Fill: Color = gray(1.0f, 0.125f),
    val level2FillPressed: Color = gray(0.0f, 0.125f),
    val level2Shadow: Color = DefaultShadowColor.copy(0.05f),
    val level1Fill: Color = gray(1.0f, 0.10f),
    val level1Shadow: Color = DefaultShadowColor.copy(0.10f),
    val level1Stroke: Color = Color.Transparent,
    val level0Fill: Color = gray(1.0f, 0.05f),
    val level0Shadow: Color = DefaultShadowColor.copy(0.10f),
) {
    val foregroundPadding: Dp = 8.dp
    val padding: Dp = 4.dp

    val level3ShadowWidth = 4.dp
    val level2ShadowWidth = 4.dp
    val level1ShadowWidth = 4.dp
    val level0CornerRadius = 0.dp
    val level0ShadowWidth = 2.dp

    /** Width of the optional rims ([level3Stroke], [primaryStroke], [level1Stroke]). Drawing only. */
    val strokeWidth: Dp = 1.5.dp

    fun compositeFill(pressed: Boolean): Color {
        return if (pressed) {
            level2FillPressed
        } else {
            level2Fill
        }
    }

    fun foregroundFill(pressed: Boolean): Color {
        return foregroundFill(pressed, active = false)
    }

    /**
     * Foreground fill of a toggle button (e.g. fast forward). While [active] it uses a strong accent fill which
     * stays recognizable on both dark and bright game scenes; pressing it still shows the regular pressed fill.
     */
    fun foregroundFill(
        pressed: Boolean,
        active: Boolean,
    ): Color {
        return foregroundFill(pressed, active, primary = false)
    }

    fun foregroundFill(
        pressed: Boolean,
        active: Boolean,
        primary: Boolean,
    ): Color {
        return when {
            pressed && primary -> primaryFillPressed
            pressed -> level3FillPressed
            active -> level3FillActive
            primary -> primaryFill
            else -> level3Fill
        }
    }

    fun foregroundStroke(primary: Boolean): Color {
        return if (primary) primaryStroke else level3Stroke
    }

    fun icons(pressed: Boolean): Color {
        return icons(pressed, active = false)
    }

    fun icons(
        pressed: Boolean,
        active: Boolean,
    ): Color {
        return icons(pressed, active, primary = false)
    }

    fun icons(
        pressed: Boolean,
        active: Boolean,
        primary: Boolean,
    ): Color {
        return when {
            pressed && primary -> primaryIconsPressed
            pressed -> iconsPressed
            active -> iconsActive
            primary -> primaryIcons
            else -> icons
        }
    }
}

val LocalLemuroidPadTheme =
    compositionLocalOf<LemuroidPadTheme> {
        error("LemuroidPadTheme is missing")
    }
