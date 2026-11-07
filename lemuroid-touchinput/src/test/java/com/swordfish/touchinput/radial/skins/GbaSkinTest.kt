package com.swordfish.touchinput.radial.skins

import androidx.compose.ui.graphics.Color
import com.swordfish.touchinput.radial.LemuroidPadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class GbaSkinTest {
    private val customSkins = GbaSkin.entries.filter { it != GbaSkin.CLASSIC_GLASS }

    @Test
    fun catalogContainsTheEightSkinsWithUniqueIds() {
        assertEquals(8, GbaSkin.entries.size)
        assertEquals(
            listOf(
                GbaSkin.CLASSIC_GLASS,
                GbaSkin.INDIGO,
                GbaSkin.GLACIER,
                GbaSkin.PLATINUM_SP,
                GbaSkin.FAMICOM,
                GbaSkin.EMERALD,
                GbaSkin.ATOMIC_PURPLE,
                GbaSkin.MIDNIGHT_OLED,
            ),
            GbaSkin.entries.toList(),
        )
        val ids = GbaSkin.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("Invalid id $it", it.matches(Regex("[a-z_]+"))) }
    }

    @Test
    fun defaultIsClassicGlass() {
        assertSame(GbaSkin.CLASSIC_GLASS, GbaSkin.DEFAULT)
        assertTrue(GbaSkin.CLASSIC_GLASS.isDefault)
        customSkins.forEach { assertTrue(!it.isDefault) }
    }

    @Test
    fun idsRoundTrip() {
        GbaSkin.entries.forEach { assertSame(it, GbaSkin.fromId(it.id)) }
        assertSame(GbaSkin.PLATINUM_SP, GbaSkin.fromId(" PLATINUM_SP "))
    }

    @Test
    fun unknownIdsFallBackToClassicGlass() {
        listOf(null, "", "   ", "unknown", "INDIGO_XL", "classic glass").forEach {
            assertSame("id '$it'", GbaSkin.CLASSIC_GLASS, GbaSkin.fromId(it))
        }
    }

    @Test
    fun classicGlassThemeIsIdenticalToTheOriginalTheme() {
        val reference = LemuroidPadTheme()
        listOf(false, true).forEach { overlay ->
            val theme = GbaSkin.CLASSIC_GLASS.padTheme(overlay)
            assertThemeColorsEqual(reference, theme)
        }
        // The original glass theme has no rims and no distinct primary buttons.
        assertEquals(Color.Transparent, reference.level3Stroke)
        assertEquals(Color.Transparent, reference.level1Stroke)
        assertEquals(reference.level3Fill, reference.primaryFill)
        assertEquals(reference.icons, reference.primaryIcons)
        assertEquals(Color(1.0f, 0.60f, 0.0f, 0.75f), reference.level3FillActive)
        assertEquals(Color(1.0f, 1.0f, 1.0f, 0.10f), reference.level1Fill)
        assertEquals(Color(0.0f, 0.0f, 0.0f, 0.50f), reference.icons)
    }

    @Test
    fun originalThemeStateMappingIsUnchanged() {
        val theme = LemuroidPadTheme()
        assertEquals(theme.level3Fill, theme.foregroundFill(pressed = false))
        assertEquals(theme.level3FillPressed, theme.foregroundFill(pressed = true))
        assertEquals(theme.level3FillActive, theme.foregroundFill(pressed = false, active = true))
        assertEquals(theme.level3FillPressed, theme.foregroundFill(pressed = true, active = true))
        assertEquals(theme.icons, theme.icons(pressed = false))
        assertEquals(theme.iconsPressed, theme.icons(pressed = true))
        assertEquals(theme.iconsActive, theme.icons(pressed = false, active = true))
        assertEquals(theme.level2Fill, theme.compositeFill(false))
        assertEquals(theme.level2FillPressed, theme.compositeFill(true))
    }

    @Test
    fun skinsNeverChangeThePadGeometry() {
        val reference = LemuroidPadTheme()
        GbaSkin.entries.forEach { skin ->
            listOf(false, true).forEach { overlay ->
                val theme = skin.padTheme(overlay)
                assertEquals(reference.padding, theme.padding)
                assertEquals(reference.foregroundPadding, theme.foregroundPadding)
                assertEquals(reference.level0CornerRadius, theme.level0CornerRadius)
                assertEquals(reference.level1ShadowWidth, theme.level1ShadowWidth)
                assertEquals(reference.level2ShadowWidth, theme.level2ShadowWidth)
                assertEquals(reference.level3ShadowWidth, theme.level3ShadowWidth)
            }
        }
    }

    @Test
    fun themesAreCached() {
        GbaSkin.entries.forEach { skin ->
            assertSame(skin.padTheme(false), skin.padTheme(false))
            assertSame(skin.padTheme(true), skin.padTheme(true))
        }
    }

    @Test
    fun portraitControlsAreSolidAndLabelsReadable() {
        customSkins.forEach { skin ->
            val theme = skin.padTheme(overlay = false)
            val shell = skin.palette.shellBottom
            for (primary in listOf(false, true)) {
                for (pressed in listOf(false, true)) {
                    val fill = theme.foregroundFill(pressed, active = false, primary = primary)
                    assertEquals("$skin cap must be opaque", 1f, fill.alpha, 0.001f)
                    val icon = theme.icons(pressed, active = false, primary = primary)
                    val ratio = contrast(over(icon, fill), fill)
                    assertTrue(
                        "$skin primary=$primary pressed=$pressed label contrast $ratio",
                        ratio >= MIN_PORTRAIT_LABEL_CONTRAST,
                    )
                }
            }
            val activeRatio = contrast(over(theme.iconsActive, theme.level3FillActive), theme.level3FillActive)
            assertTrue("$skin active label contrast $activeRatio", activeRatio >= MIN_PORTRAIT_LABEL_CONTRAST)
            assertTrue("$skin shell must be opaque", shell.alpha == 1f)
        }
    }

    @Test
    fun fastForwardActiveStateStandsOutOnEverySkin() {
        GbaSkin.entries.forEach { skin ->
            listOf(false, true).forEach { overlay ->
                val theme = skin.padTheme(overlay)
                val idle = theme.foregroundFill(pressed = false, active = false)
                val pressed = theme.foregroundFill(pressed = true, active = false)
                val active = theme.foregroundFill(pressed = false, active = true)
                assertNotEquals(idle, active)
                assertNotEquals(pressed, active)
                for (background in BACKGROUNDS) {
                    val activeSeen = over(active, background)
                    val idleDistance = distance(activeSeen, over(idle, background))
                    val pressedDistance = distance(activeSeen, over(pressed, background))
                    assertTrue(
                        "$skin overlay=$overlay active vs idle distance $idleDistance",
                        idleDistance >= MIN_ACTIVE_DISTANCE,
                    )
                    assertTrue(
                        "$skin overlay=$overlay active vs pressed distance $pressedDistance",
                        pressedDistance >= MIN_ACTIVE_DISTANCE,
                    )
                }
                // Pressing an active toggle still shows the regular pressed fill.
                assertEquals(pressed, theme.foregroundFill(pressed = true, active = true))
            }
        }
    }

    @Test
    fun pressedStateIsVisibleOnEverySkin() {
        customSkins.forEach { skin ->
            listOf(false, true).forEach { overlay ->
                val theme = skin.padTheme(overlay)
                for (primary in listOf(false, true)) {
                    val idle = theme.foregroundFill(pressed = false, active = false, primary = primary)
                    val pressed = theme.foregroundFill(pressed = true, active = false, primary = primary)
                    assertTrue(
                        "$skin overlay=$overlay primary=$primary pressed fill too similar",
                        distance(over(idle, Color.Gray), over(pressed, Color.Gray)) >= 0.08f,
                    )
                }
            }
        }
    }

    @Test
    fun landscapeOverlayThemesAreTranslucent() {
        customSkins.forEach { skin ->
            val theme = skin.padTheme(overlay = true)
            assertTrue("$skin ring alpha ${theme.level1Fill.alpha}", theme.level1Fill.alpha <= 0.35f)
            assertTrue("$skin track alpha", theme.compositeFill(false).alpha <= 0.5f)
            assertTrue("$skin track pressed alpha", theme.compositeFill(true).alpha <= 0.6f)
            for (primary in listOf(false, true)) {
                for (pressed in listOf(false, true)) {
                    val fill = theme.foregroundFill(pressed, active = false, primary = primary)
                    assertTrue("$skin cap alpha ${fill.alpha}", fill.alpha < 0.85f)
                }
            }
            assertTrue(theme.foregroundFill(pressed = false, active = true).alpha < 1f)
            assertTrue(theme.level0Fill.alpha < 0.5f)
        }
    }

    @Test
    fun landscapeOverlayLabelsStayReadableOverTheGame() {
        customSkins.forEach { skin ->
            val theme = skin.padTheme(overlay = true)
            for (background in BACKGROUNDS) {
                for (primary in listOf(false, true)) {
                    for (pressed in listOf(false, true)) {
                        val fill = over(theme.foregroundFill(pressed, active = false, primary = primary), background)
                        val icon = over(theme.icons(pressed, active = false, primary = primary), fill)
                        val ratio = contrast(icon, fill)
                        assertTrue(
                            "$skin primary=$primary pressed=$pressed on $background label contrast $ratio",
                            ratio >= MIN_OVERLAY_LABEL_CONTRAST,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun skinsExpressTheirPalette() {
        // Each custom skin should look different from the classic glass and from each other.
        val reference = LemuroidPadTheme()
        customSkins.forEach { skin ->
            val theme = skin.padTheme(false)
            assertNotEquals(reference.level3Fill, theme.level3Fill)
            assertNotEquals(reference.level1Fill, theme.level1Fill)
            assertNotEquals(Color.Transparent, theme.level1Stroke)
        }
        val shells = customSkins.map { it.palette.shellTop }
        assertEquals(shells.size, shells.toSet().size)
        assertTrue(GbaSkin.MIDNIGHT_OLED.palette.shellBottom == Color.Black)
        assertTrue(luminance(GbaSkin.MIDNIGHT_OLED.palette.shellTop) < 0.005f)
        assertEquals(GbaSpeakerStyle.DOT_MATRIX, GbaSkin.PLATINUM_SP.palette.speakerStyle)
        assertEquals(GbaSpeakerStyle.SUBTLE, GbaSkin.MIDNIGHT_OLED.palette.speakerStyle)
        assertEquals(Color(0xFF5B4B9E), GbaSkin.INDIGO.palette.shellTop)
        assertEquals(Color(0xFF41337A), GbaSkin.INDIGO.palette.shellBottom)
        assertEquals(Color(0xFFDCE0EA), GbaSkin.INDIGO.palette.cap)
    }

    private fun assertThemeColorsEqual(
        expected: LemuroidPadTheme,
        actual: LemuroidPadTheme,
    ) {
        for (pressed in listOf(false, true)) {
            for (active in listOf(false, true)) {
                for (primary in listOf(false, true)) {
                    assertEquals(
                        expected.foregroundFill(pressed, active, primary),
                        actual.foregroundFill(pressed, active, primary),
                    )
                    assertEquals(expected.icons(pressed, active, primary), actual.icons(pressed, active, primary))
                }
            }
            assertEquals(expected.compositeFill(pressed), actual.compositeFill(pressed))
        }
        assertEquals(expected.foregroundStroke(false), actual.foregroundStroke(false))
        assertEquals(expected.foregroundStroke(true), actual.foregroundStroke(true))
        assertEquals(expected.level0Fill, actual.level0Fill)
        assertEquals(expected.level0Shadow, actual.level0Shadow)
        assertEquals(expected.level1Fill, actual.level1Fill)
        assertEquals(expected.level1Shadow, actual.level1Shadow)
        assertEquals(expected.level1Stroke, actual.level1Stroke)
        assertEquals(expected.level2Shadow, actual.level2Shadow)
        assertEquals(expected.level3Shadow, actual.level3Shadow)
    }

    companion object {
        private const val MIN_PORTRAIT_LABEL_CONTRAST = 4.0f
        private const val MIN_OVERLAY_LABEL_CONTRAST = 2.5f
        private const val MIN_ACTIVE_DISTANCE = 0.25f

        private val BACKGROUNDS = listOf(Color.Black, Color(0.5f, 0.5f, 0.5f), Color.White, Color(0.2f, 0.45f, 0.9f))

        /** Alpha-composites [top] over [bottom], returning an opaque color. */
        fun over(
            top: Color,
            bottom: Color,
        ): Color {
            val a = top.alpha
            val base = if (bottom.alpha < 1f) over(bottom, Color.Gray) else bottom
            return Color(
                red = top.red * a + base.red * (1 - a),
                green = top.green * a + base.green * (1 - a),
                blue = top.blue * a + base.blue * (1 - a),
                alpha = 1f,
            )
        }

        fun luminance(color: Color): Float {
            fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return (0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue))
                .toFloat()
        }

        /** WCAG contrast ratio between two opaque colors. */
        fun contrast(
            a: Color,
            b: Color,
        ): Float {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
        }

        fun distance(
            a: Color,
            b: Color,
        ): Float {
            val dr = a.red - b.red
            val dg = a.green - b.green
            val db = a.blue - b.blue
            return sqrt(dr * dr + dg * dg + db * db)
        }
    }
}
