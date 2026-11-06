package com.swordfish.touchinput.radial

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class LemuroidPadTheme {
    private fun gray(
        luminosity: Float,
        opacity: Float,
    ): Color {
        return Color(luminosity, luminosity, luminosity, opacity)
    }

    val foregroundPadding: Dp = 8.dp
    val padding: Dp = 4.dp

    private val icons = gray(0.0f, 0.50f)
    private val iconsPressed = gray(1.0f, 0.50f)
    private val iconsActive = gray(1.0f, 0.90f)

    private val level3Fill = gray(1.0f, 0.50f)
    private val level3FillPressed = gray(0.0f, 0.50f)
    private val level3FillActive = Color(1.0f, 0.60f, 0.0f, 0.75f)
    val level3Shadow = DefaultShadowColor.copy(0.05f)
    val level3ShadowWidth = 4.dp

    private val level2Fill = gray(1.0f, 0.125f)
    private val level2FillPressed = gray(0.0f, 0.125f)
    val level2Shadow = DefaultShadowColor.copy(0.05f)
    val level2ShadowWidth = 4.dp

    val level1Fill = gray(1.0f, 0.10f)
    val level1Shadow = DefaultShadowColor.copy(0.10f)
    val level1ShadowWidth = 4.dp

    val level0CornerRadius = 0.dp
    val level0Fill = gray(1.0f, 0.05f)
    val level0Shadow = DefaultShadowColor.copy(0.10f)
    val level0ShadowWidth = 2.dp

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
        return when {
            pressed -> level3FillPressed
            active -> level3FillActive
            else -> level3Fill
        }
    }

    fun icons(pressed: Boolean): Color {
        return icons(pressed, active = false)
    }

    fun icons(
        pressed: Boolean,
        active: Boolean,
    ): Color {
        return when {
            pressed -> iconsPressed
            active -> iconsActive
            else -> icons
        }
    }
}

val LocalLemuroidPadTheme =
    compositionLocalOf<LemuroidPadTheme> {
        error("LemuroidPadTheme is missing")
    }
