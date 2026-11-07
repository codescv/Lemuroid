package com.swordfish.lemuroid.app.mobile.feature.gbaskin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.touchinput.radial.skins.GbaSkin
import com.swordfish.touchinput.radial.skins.GbaSkinPalette
import com.swordfish.touchinput.radial.skins.GbaSpeakerStyle
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * GBA handheld deck drawn behind the virtual pads in portrait mode (the bottom container).
 *
 * This is pure drawing: it has no pointer input, so it never intercepts touches, and everything is clipped to its
 * own bounds, so the lens trim, the POWER LED halo and the speaker vents can never bleed into the game viewport.
 * It does not take part in the pads measurement either, so switching skin never moves or resizes the game.
 */
@Composable
fun GbaConsoleDeck(
    skin: GbaSkin,
    modifier: Modifier = Modifier,
) {
    val palette = skin.palette
    val textMeasurer = rememberTextMeasurer()
    val badge = if (skin == GbaSkin.PLATINUM_SP) "GBA SP" else "GBA"

    Spacer(
        modifier =
            modifier
                .clipToBounds()
                .drawWithCache {
                    val badgeLayout =
                        textMeasurer.measure(
                            text = badge,
                            style =
                                TextStyle(
                                    color = palette.bezelText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 2.sp,
                                ),
                        )
                    val powerLayout =
                        textMeasurer.measure(
                            text = "POWER",
                            style =
                                TextStyle(
                                    color = palette.bezelText.copy(alpha = palette.bezelText.alpha * 0.75f),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                ),
                        )
                    onDrawBehind {
                        drawGbaDeck(palette, badgeLayout, powerLayout)
                    }
                },
    )
}

private fun DrawScope.drawGbaDeck(
    palette: GbaSkinPalette,
    badgeLayout: TextLayoutResult,
    powerLayout: TextLayoutResult,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    // Shell body.
    drawRect(brush = Brush.verticalGradient(listOf(palette.shellTop, palette.shellBottom)))
    if (palette.translucentShell) drawTranslucentDepth(palette)
    if (palette.brushedMetal) drawBrushedMetal()
    drawRect(
        brush =
            Brush.linearGradient(
                colors = listOf(palette.shellHighlight.copy(alpha = 0.22f), Color.Transparent),
                start = Offset.Zero,
                end = Offset(width * 0.6f, height * 0.6f),
            ),
    )

    drawGripContours(palette)
    drawSpeaker(palette, Offset(width / 2f, height - 16.dp.toPx()), scale = 1f)

    val barHeight = drawLensTrim(palette)
    drawTrimAccent(palette, barHeight)
    drawLensBadges(palette, barHeight, badgeLayout, powerLayout)
}

/** Faint internal silhouettes, like the board seen through a translucent shell. */
private fun DrawScope.drawTranslucentDepth(palette: GbaSkinPalette) {
    val width = size.width
    val height = size.height
    val board = palette.shellShadow.copy(alpha = 0.10f)
    val trace = palette.shellShadow.copy(alpha = 0.08f)
    val boardRect = Rect(width * 0.33f, height * 0.38f, width * 0.67f, height * 0.78f)
    drawRoundRect(
        color = board,
        topLeft = boardRect.topLeft,
        size = boardRect.size,
        cornerRadius = CornerRadius(10.dp.toPx()),
    )
    val traceWidth = 1.dp.toPx()
    for (i in 0 until 4) {
        val y = boardRect.top + boardRect.height * (0.2f + 0.2f * i)
        drawLine(trace, Offset(width * 0.12f, y), Offset(boardRect.left, y), traceWidth)
        drawLine(trace, Offset(boardRect.right, y), Offset(width * 0.88f, y), traceWidth)
    }
    drawCircle(board, radius = min(width, height) * 0.05f, center = Offset(width * 0.5f, height * 0.58f))
}

/** Thin alternating lines giving a brushed aluminium finish. */
private fun DrawScope.drawBrushedMetal() {
    val step = 2.dp.toPx()
    val light = Color.White.copy(alpha = 0.07f)
    val dark = Color.Black.copy(alpha = 0.035f)
    var y = 0f
    var index = 0
    while (y < size.height) {
        drawLine(if (index % 2 == 0) light else dark, Offset(0f, y), Offset(size.width, y), 1f)
        y += step
        index++
    }
}

/** Molded grip contours in the bottom corners, mirrored left and right. */
private fun DrawScope.drawGripContours(palette: GbaSkinPalette) {
    val width = size.width
    val height = size.height
    val radius = width * 0.42f
    val strokeWidth = 1.5.dp.toPx()
    val offset = 1.dp.toPx()
    val shadow = palette.shellShadow.copy(alpha = 0.30f)
    val highlight = palette.shellHighlight.copy(alpha = 0.30f)

    listOf(-width * 0.12f, width * 1.12f).forEach { centerX ->
        val center = Offset(centerX, height * 1.08f)
        drawCircle(highlight, radius, center + Offset(0f, offset), style = Stroke(strokeWidth))
        drawCircle(shadow, radius, center, style = Stroke(strokeWidth))
    }
}

private fun DrawScope.drawSpeaker(
    palette: GbaSkinPalette,
    bottomCenter: Offset,
    scale: Float,
) {
    when (palette.speakerStyle) {
        GbaSpeakerStyle.DIAGONAL_SLOTS ->
            drawDiagonalSlots(
                bottomCenter,
                scale,
                slot = palette.shellShadow.copy(alpha = 0.55f),
                highlight = palette.shellHighlight.copy(alpha = 0.35f),
            )
        GbaSpeakerStyle.SUBTLE ->
            drawDiagonalSlots(
                bottomCenter,
                scale,
                slot = palette.shellHighlight.copy(alpha = 0.55f),
                highlight = Color.Transparent,
            )
        GbaSpeakerStyle.DOT_MATRIX -> drawDotMatrix(palette, bottomCenter, scale)
    }
}

private fun DrawScope.drawDiagonalSlots(
    bottomCenter: Offset,
    scale: Float,
    slot: Color,
    highlight: Color,
) {
    val length = min(24.dp.toPx(), size.height * 0.12f) * scale
    val spacing = 7.dp.toPx() * scale
    val strokeWidth = 3.dp.toPx() * scale
    val angle = Math.toRadians(60.0)
    val dx = (length * cos(angle)).toFloat()
    val dy = (length * sin(angle)).toFloat()
    val highlightOffset = Offset(0.8f, 0.8f) * (1.dp.toPx() * scale)
    for (i in 0 until SPEAKER_SLOTS) {
        val x = bottomCenter.x + (i - (SPEAKER_SLOTS - 1) / 2f) * spacing
        val start = Offset(x - dx / 2f, bottomCenter.y)
        val end = Offset(x + dx / 2f, bottomCenter.y - dy)
        if (highlight.alpha > 0f) {
            drawLine(highlight, start + highlightOffset, end + highlightOffset, strokeWidth, StrokeCap.Round)
        }
        drawLine(slot, start, end, strokeWidth, StrokeCap.Round)
    }
}

private fun DrawScope.drawDotMatrix(
    palette: GbaSkinPalette,
    bottomCenter: Offset,
    scale: Float,
) {
    val spacing = 6.dp.toPx() * scale
    val radius = 1.7.dp.toPx() * scale
    val dot = palette.shellShadow.copy(alpha = 0.75f)
    val highlight = palette.shellHighlight.copy(alpha = 0.6f)
    val highlightOffset = Offset(0f, 0.8.dp.toPx() * scale)
    val top = bottomCenter.y - spacing * (DOT_MATRIX_SIZE - 1)
    for (row in 0 until DOT_MATRIX_SIZE) {
        for (column in 0 until DOT_MATRIX_SIZE) {
            val center =
                Offset(
                    bottomCenter.x + (column - (DOT_MATRIX_SIZE - 1) / 2f) * spacing,
                    top + row * spacing,
                )
            drawCircle(highlight, radius, center + highlightOffset)
            drawCircle(dot, radius, center)
        }
    }
}

/** Bottom of the screen lens, along the top edge of the deck. Returns its height. */
private fun DrawScope.drawLensTrim(palette: GbaSkinPalette): Float {
    val width = size.width
    val barHeight = min(26.dp.toPx(), size.height * 0.12f)
    val inset = min(14.dp.toPx(), width * 0.04f)
    val radius = barHeight * 0.6f

    val path =
        Path().apply {
            moveTo(inset, 0f)
            lineTo(width - inset, 0f)
            lineTo(width - inset, barHeight - radius)
            quadraticTo(width - inset, barHeight, width - inset - radius, barHeight)
            lineTo(inset + radius, barHeight)
            quadraticTo(inset, barHeight, inset, barHeight - radius)
            close()
        }
    drawPath(
        path = path,
        brush =
            Brush.verticalGradient(
                colors = listOf(palette.bezel, lerp(palette.bezel, Color.White, 0.08f)),
                startY = 0f,
                endY = barHeight,
            ),
    )
    drawLine(
        color = Color.White.copy(alpha = 0.08f),
        start = Offset(inset + radius, barHeight - 1.dp.toPx()),
        end = Offset(width - inset - radius, barHeight - 1.dp.toPx()),
        strokeWidth = 1f,
    )
    return barHeight
}

/** Accent line under the lens; the Famicom skin gets a crimson band with a gold pinstripe instead. */
private fun DrawScope.drawTrimAccent(
    palette: GbaSkinPalette,
    barHeight: Float,
) {
    val width = size.width
    val gap = 5.dp.toPx()
    if (palette == GbaSkin.FAMICOM.palette) {
        val bandHeight = 5.dp.toPx()
        drawRect(palette.accent, Offset(0f, barHeight + gap), Size(width, bandHeight))
        drawRect(
            palette.accentSecondary,
            Offset(0f, barHeight + gap + bandHeight + 2.dp.toPx()),
            Size(width, 1.dp.toPx()),
        )
    } else {
        val inset = width * 0.18f
        drawLine(
            brush =
                Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, palette.accent.copy(alpha = 0.7f), Color.Transparent),
                    startX = inset,
                    endX = width - inset,
                ),
            start = Offset(inset, barHeight + gap),
            end = Offset(width - inset, barHeight + gap),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

/**
 * Badge and POWER LED on the lens trim. The top center and the outer corners of the deck are covered by the L/R,
 * Select and Start dials, so the badge sits above the D-pad (25% of the width) and the LED group above the A/B
 * buttons (75%), where the GBA pads leave their top slot empty, like the right side LED of the real console.
 */
private fun DrawScope.drawLensBadges(
    palette: GbaSkinPalette,
    barHeight: Float,
    badgeLayout: TextLayoutResult,
    powerLayout: TextLayoutResult,
) {
    val centerY = barHeight / 2f
    val haloRadius = min(8.dp.toPx(), barHeight * 0.45f)
    val showPower = powerLayout.size.height <= barHeight
    val powerWidth = if (showPower) powerLayout.size.width.toFloat() else 0f

    // POWER LED with a soft radial halo, centered as a group with its label.
    val groupWidth = haloRadius * 2f + powerWidth
    val ledCenter = Offset(size.width * LED_GROUP_POSITION - groupWidth / 2f + haloRadius, centerY)
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(palette.led.copy(alpha = 0.55f), Color.Transparent),
                center = ledCenter,
                radius = haloRadius,
            ),
        radius = haloRadius,
        center = ledCenter,
    )
    drawCircle(palette.led, radius = min(2.6.dp.toPx(), barHeight * 0.15f), center = ledCenter)

    if (showPower) {
        drawText(
            textLayoutResult = powerLayout,
            topLeft = Offset(ledCenter.x + haloRadius, centerY - powerLayout.size.height / 2f),
        )
    }
    if (badgeLayout.size.height <= barHeight && badgeLayout.size.width < size.width * 0.4f) {
        drawText(
            textLayoutResult = badgeLayout,
            topLeft =
                Offset(
                    size.width * BADGE_POSITION - badgeLayout.size.width / 2f,
                    centerY - badgeLayout.size.height / 2f,
                ),
        )
    }
}

/**
 * Miniature GBA console drawn with the colors of [skin]: shell gradient, lens bezel with a tiny green LED, D-pad
 * cross, tilted A/B buttons, speaker vents and accent trim.
 */
@Composable
fun GbaSkinPreview(
    skin: GbaSkin,
    modifier: Modifier = Modifier,
) {
    val palette = skin.palette
    val isSp = skin == GbaSkin.PLATINUM_SP
    Canvas(modifier = modifier) {
        drawPreview(palette, isSp)
    }
}

private fun DrawScope.drawPreview(
    palette: GbaSkinPalette,
    isSp: Boolean,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    drawRoundRect(
        brush = Brush.verticalGradient(listOf(PREVIEW_BACKDROP_TOP, PREVIEW_BACKDROP_BOTTOM)),
        cornerRadius = CornerRadius(10.dp.toPx()),
    )

    val shellSize = if (isSp) Size(width * 0.62f, height * 0.80f) else Size(width * 0.90f, height * 0.72f)
    val shellTopLeft = Offset((width - shellSize.width) / 2f, (height - shellSize.height) / 2f)
    val shellRadius = if (isSp) shellSize.minDimension * 0.16f else shellSize.height * 0.32f
    val shellRect = Rect(shellTopLeft, shellSize)
    val shellPath =
        Path().apply {
            addRoundRect(RoundRect(shellRect, CornerRadius(shellRadius)))
        }

    drawPath(
        path = shellPath,
        brush = Brush.verticalGradient(listOf(palette.shellTop, palette.shellBottom), shellRect.top, shellRect.bottom),
    )
    clipPath(shellPath) {
        if (palette.brushedMetal) drawBrushedMetal()
        drawRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(palette.shellHighlight.copy(alpha = 0.30f), Color.Transparent),
                    start = shellRect.topLeft,
                    end = shellRect.center,
                ),
        )
        if (palette == GbaSkin.FAMICOM.palette) {
            val bandHeight = shellRect.height * 0.12f
            val bandTop = shellRect.bottom - bandHeight * 1.6f
            drawRect(palette.accent, Offset(shellRect.left, bandTop), Size(shellRect.width, bandHeight))
            drawRect(
                palette.accentSecondary,
                Offset(shellRect.left, bandTop - 2.dp.toPx()),
                Size(shellRect.width, 0.8.dp.toPx()),
            )
        }
    }
    drawPath(shellPath, palette.accent.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx()))

    val centerY = shellRect.center.y
    if (isSp) {
        // Bottom half of the SP: lens trim along the hinge, no screen.
        val trim =
            Rect(
                left = shellRect.left + shellRect.width * 0.12f,
                top = shellRect.top + 3.dp.toPx(),
                right = shellRect.right - shellRect.width * 0.12f,
                bottom = shellRect.top + 9.dp.toPx(),
            )
        drawRoundRect(palette.bezel, trim.topLeft, trim.size, CornerRadius(3.dp.toPx()))
        drawPreviewLed(palette, Offset(trim.left + 4.dp.toPx(), trim.center.y))
    } else {
        val bezel =
            Rect(
                offset = Offset(shellRect.center.x - shellRect.width * 0.21f, shellRect.top + shellRect.height * 0.16f),
                size = Size(shellRect.width * 0.42f, shellRect.height * 0.62f),
            )
        drawRoundRect(palette.bezel, bezel.topLeft, bezel.size, CornerRadius(4.dp.toPx()))
        val inset = 3.5.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(listOf(PREVIEW_LCD_DARK, PREVIEW_LCD_LIGHT), bezel.topLeft, bezel.bottomRight),
            topLeft = bezel.topLeft + Offset(inset * 1.6f, inset),
            size = Size(bezel.width - inset * 3.2f, bezel.height - inset * 2f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawPreviewLed(palette, Offset(bezel.left + inset * 0.8f, bezel.top + inset * 1.4f))
    }

    // D-pad cross.
    val controlsY = centerY + if (isSp) 3.dp.toPx() else 0f
    val dpadCenter = Offset(shellRect.left + shellRect.width * (if (isSp) 0.27f else 0.15f), controlsY)
    val arm = shellRect.height * (if (isSp) 0.20f else 0.17f)
    val thickness = arm * 0.72f
    listOf(Size(arm * 2f, thickness), Size(thickness, arm * 2f)).forEach { armSize ->
        drawRoundRect(
            color = palette.cap,
            topLeft = dpadCenter - Offset(armSize.width / 2f, armSize.height / 2f),
            size = armSize,
            cornerRadius = CornerRadius(thickness * 0.25f),
        )
    }
    drawCircle(palette.capPressed.copy(alpha = 0.6f), thickness * 0.22f, dpadCenter)

    // Tilted A/B buttons.
    val faceRadius = shellRect.height * (if (isSp) 0.095f else 0.085f)
    val faceCenterX = shellRect.right - shellRect.width * (if (isSp) 0.27f else 0.15f)
    val buttonA = Offset(faceCenterX + faceRadius * 1.15f, controlsY - faceRadius * 0.55f)
    val buttonB = Offset(faceCenterX - faceRadius * 1.15f, controlsY + faceRadius * 0.55f)
    listOf(buttonA, buttonB).forEach { center ->
        drawCircle(palette.face, faceRadius, center)
        if (palette.faceStroke.alpha > 0f) {
            drawCircle(palette.faceStroke, faceRadius, center, style = Stroke(1.dp.toPx()))
        }
    }

    // Speaker vents.
    val speakerScale = 0.32f
    if (isSp) {
        drawSpeaker(palette, Offset(shellRect.center.x, shellRect.bottom - 6.dp.toPx()), speakerScale * 1.4f)
    } else {
        drawSpeaker(palette, Offset(faceCenterX, shellRect.bottom - shellRect.height * 0.10f), speakerScale)
    }
}

private fun DrawScope.drawPreviewLed(
    palette: GbaSkinPalette,
    center: Offset,
) {
    val halo = 3.5.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(listOf(palette.led.copy(alpha = 0.6f), Color.Transparent), center, halo),
        radius = halo,
        center = center,
    )
    drawCircle(palette.led, 1.3.dp.toPx(), center)
}

private const val SPEAKER_SLOTS = 6
private const val BADGE_POSITION = 0.25f
private const val LED_GROUP_POSITION = 0.75f
private const val DOT_MATRIX_SIZE = 4

private val PREVIEW_BACKDROP_TOP = Color(0xFF3A3F4B)
private val PREVIEW_BACKDROP_BOTTOM = Color(0xFF1C1F26)
private val PREVIEW_LCD_DARK = Color(0xFF1E2B26)
private val PREVIEW_LCD_LIGHT = Color(0xFF46685A)
