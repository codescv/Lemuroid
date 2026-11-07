package com.swordfish.touchinput.radial.skins

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.swordfish.touchinput.radial.LemuroidPadTheme

/** Speaker vents drawn on the portrait GBA deck and on the skin previews. */
enum class GbaSpeakerStyle {
    /** The six diagonal slots of the original AGB-001. */
    DIAGONAL_SLOTS,

    /** The 4x4 dot-matrix grille of the AGS-001 (GBA SP). */
    DOT_MATRIX,

    /** Almost invisible slots, for low glare skins. */
    SUBTLE,
}

/**
 * Colors of a GBA skin. Shell colors are used by the portrait console deck backdrop and by the previews, button
 * colors are turned into a [LemuroidPadTheme] by [GbaSkin.padTheme].
 *
 * @param cap Fill of the D-pad arms and of the secondary buttons (L, R, Select, Start, Menu, Fast-Forward).
 * @param face Fill of the primary A/B face buttons.
 * @param active Fill of an active toggle (Fast-Forward), always clearly different from [cap] and [capPressed].
 * @param ring Background disc behind every control (dial ring).
 * @param track Fill of the D-pad composite (diagonal) area.
 */
@Immutable
data class GbaSkinPalette(
    val shellTop: Color,
    val shellBottom: Color,
    val shellHighlight: Color,
    val shellShadow: Color,
    val bezel: Color,
    val bezelText: Color,
    val accent: Color,
    val accentSecondary: Color,
    val led: Color,
    val speakerStyle: GbaSpeakerStyle,
    val translucentShell: Boolean,
    val brushedMetal: Boolean,
    val cap: Color,
    val capPressed: Color,
    val capIcon: Color,
    val capIconPressed: Color,
    val face: Color,
    val facePressed: Color,
    val faceIcon: Color,
    val faceIconPressed: Color,
    val faceStroke: Color,
    val active: Color,
    val activeIcon: Color,
    val ring: Color,
    val track: Color,
    val trackPressed: Color,
)

/**
 * Selectable GBA skins. [CLASSIC_GLASS] is the default and renders exactly like the original Lemuroid pad.
 *
 * @param id Stable identifier persisted in the preferences and exchanged with the game menu. Never rename it.
 */
enum class GbaSkin(
    val id: String,
    val palette: GbaSkinPalette,
) {
    CLASSIC_GLASS(
        id = "classic_glass",
        palette =
            GbaSkinPalette(
                shellTop = Color(0x40FFFFFF),
                shellBottom = Color(0x26FFFFFF),
                shellHighlight = Color(0x59FFFFFF),
                shellShadow = Color(0x33000000),
                bezel = Color(0x66000000),
                bezelText = Color(0xB3FFFFFF),
                accent = Color(0x80FFFFFF),
                accentSecondary = Color(0x4DFFFFFF),
                led = Color(0xFF39FF6A),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = true,
                brushedMetal = false,
                cap = Color(0x80FFFFFF),
                capPressed = Color(0x80000000),
                capIcon = Color(0x80000000),
                capIconPressed = Color(0x80FFFFFF),
                face = Color(0x80FFFFFF),
                facePressed = Color(0x80000000),
                faceIcon = Color(0x80000000),
                faceIconPressed = Color(0x80FFFFFF),
                faceStroke = Color.Transparent,
                active = Color(0xBFFF9900),
                activeIcon = Color(0xE6FFFFFF),
                ring = Color(0x1AFFFFFF),
                track = Color(0x20FFFFFF),
                trackPressed = Color(0x20000000),
            ),
    ),
    INDIGO(
        id = "indigo",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFF5B4B9E),
                shellBottom = Color(0xFF41337A),
                shellHighlight = Color(0xFF7D6EC4),
                shellShadow = Color(0xFF261D52),
                bezel = Color(0xFF1C1A2B),
                bezelText = Color(0xFFB9B3DE),
                accent = Color(0xFF9A8FE0),
                accentSecondary = Color(0xFFCBC5F0),
                led = Color(0xFF3DFF6E),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = false,
                brushedMetal = false,
                cap = Color(0xFFDCE0EA),
                capPressed = Color(0xFFA3A9BD),
                capIcon = Color(0xFF2A2C35),
                capIconPressed = Color(0xFF0E0F14),
                face = Color(0xFFDCE0EA),
                facePressed = Color(0xFFA3A9BD),
                faceIcon = Color(0xFF2A2C35),
                faceIconPressed = Color(0xFF0E0F14),
                faceStroke = Color(0x66261D52),
                active = Color(0xFFFFB238),
                activeIcon = Color(0xFF2A1700),
                ring = Color(0xFF34286C),
                track = Color(0xFFC4C9D8),
                trackPressed = Color(0xFF9399AE),
            ),
    ),
    GLACIER(
        id = "glacier",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFFAEE0F2),
                shellBottom = Color(0xFF6FB6D6),
                shellHighlight = Color(0xFFE8F8FF),
                shellShadow = Color(0xFF3A86A8),
                bezel = Color(0xFF1C3340),
                bezelText = Color(0xFFBDEEFF),
                accent = Color(0xFF22B8C2),
                accentSecondary = Color(0xFFE6FBFF),
                led = Color(0xFF3DFF6E),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = true,
                brushedMetal = false,
                cap = Color(0xFFF0F9FD),
                capPressed = Color(0xFFB4D6E5),
                capIcon = Color(0xFF1D4A5E),
                capIconPressed = Color(0xFF0C2733),
                face = Color(0xFFF0F9FD),
                facePressed = Color(0xFFB4D6E5),
                faceIcon = Color(0xFF0B5560),
                faceIconPressed = Color(0xFF0C2733),
                faceStroke = Color(0xCC22B8C2),
                active = Color(0xFFFF7A45),
                activeIcon = Color(0xFF2B0E00),
                ring = Color(0xFF4F9EC2),
                track = Color(0xFFD3ECF6),
                trackPressed = Color(0xFF9FCADD),
            ),
    ),
    PLATINUM_SP(
        id = "platinum_sp",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFFE4E7EB),
                shellBottom = Color(0xFFA6ABB4),
                shellHighlight = Color(0xFFF8F9FB),
                shellShadow = Color(0xFF6E747E),
                bezel = Color(0xFF141619),
                bezelText = Color(0xFFC9CDD3),
                accent = Color(0xFF5A626E),
                accentSecondary = Color(0xFFFFFFFF),
                led = Color(0xFF3DFF6E),
                speakerStyle = GbaSpeakerStyle.DOT_MATRIX,
                translucentShell = false,
                brushedMetal = true,
                cap = Color(0xFF3A3F47),
                capPressed = Color(0xFF1E2126),
                capIcon = Color(0xFFE9ECF0),
                capIconPressed = Color(0xFFFFFFFF),
                face = Color(0xFF3A3F47),
                facePressed = Color(0xFF1E2126),
                faceIcon = Color(0xFFF2F4F7),
                faceIconPressed = Color(0xFFFFFFFF),
                faceStroke = Color(0x99FFFFFF),
                active = Color(0xFF3FA9F5),
                activeIcon = Color(0xFF041320),
                ring = Color(0xFF8A9099),
                track = Color(0xFF4A5059),
                trackPressed = Color(0xFF2B2F35),
            ),
    ),
    FAMICOM(
        id = "famicom",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFFF3EAD3),
                shellBottom = Color(0xFFE0D0A8),
                shellHighlight = Color(0xFFFFF9E8),
                shellShadow = Color(0xFFAF9B6C),
                bezel = Color(0xFF2A1D1A),
                bezelText = Color(0xFFE6D29A),
                accent = Color(0xFF8E1B26),
                accentSecondary = Color(0xFFC9A548),
                led = Color(0xFF3DFF6E),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = false,
                brushedMetal = false,
                cap = Color(0xFF34312F),
                capPressed = Color(0xFF181615),
                capIcon = Color(0xFFF3EAD3),
                capIconPressed = Color(0xFFFFFFFF),
                face = Color(0xFF9E1C2A),
                facePressed = Color(0xFF620F18),
                faceIcon = Color(0xFFF8EACB),
                faceIconPressed = Color(0xFFFFFFFF),
                faceStroke = Color(0xFFC9A548),
                active = Color(0xFFFFB300),
                activeIcon = Color(0xFF2A1A00),
                ring = Color(0xFFCDBA8A),
                track = Color(0xFF4A4542),
                trackPressed = Color(0xFF262321),
            ),
    ),
    EMERALD(
        id = "emerald",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFF1F8E63),
                shellBottom = Color(0xFF0D573C),
                shellHighlight = Color(0xFF3FBD88),
                shellShadow = Color(0xFF063524),
                bezel = Color(0xFF0A1E16),
                bezelText = Color(0xFFF2C94C),
                accent = Color(0xFFF2C230),
                accentSecondary = Color(0xFFFFE48A),
                led = Color(0xFF7CFF9B),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = false,
                brushedMetal = false,
                cap = Color(0xFF1F2B26),
                capPressed = Color(0xFF0E1512),
                capIcon = Color(0xFFF2D46A),
                capIconPressed = Color(0xFFFFFFFF),
                face = Color(0xFF26362F),
                facePressed = Color(0xFF111A16),
                faceIcon = Color(0xFFFFD54F),
                faceIconPressed = Color(0xFFFFFFFF),
                faceStroke = Color(0xFFF2C230),
                active = Color(0xFF3DE0FF),
                activeIcon = Color(0xFF002A33),
                ring = Color(0xFF0A4631),
                track = Color(0xFF2A3832),
                trackPressed = Color(0xFF141C18),
            ),
    ),
    ATOMIC_PURPLE(
        id = "atomic_purple",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFF8C6DBA),
                shellBottom = Color(0xFF573C84),
                shellHighlight = Color(0xFFBBA0E4),
                shellShadow = Color(0xFF34225A),
                bezel = Color(0xFF1A1426),
                bezelText = Color(0xFFD7C6F5),
                accent = Color(0xFFB69CFF),
                accentSecondary = Color(0xFF4FE3D0),
                led = Color(0xFF3DFF6E),
                speakerStyle = GbaSpeakerStyle.DIAGONAL_SLOTS,
                translucentShell = true,
                brushedMetal = false,
                cap = Color(0xFF2E2B33),
                capPressed = Color(0xFF151318),
                capIcon = Color(0xFFDCD0F7),
                capIconPressed = Color(0xFFFFFFFF),
                face = Color(0xFFA48FD0),
                facePressed = Color(0xFF6A5696),
                faceIcon = Color(0xFF1E1430),
                faceIconPressed = Color(0xFFFFFFFF),
                faceStroke = Color(0xCC4FE3D0),
                active = Color(0xFF4FE3D0),
                activeIcon = Color(0xFF062A26),
                ring = Color(0xFF47306B),
                track = Color(0xFF3A3542),
                trackPressed = Color(0xFF1E1B23),
            ),
    ),
    MIDNIGHT_OLED(
        id = "midnight_oled",
        palette =
            GbaSkinPalette(
                shellTop = Color(0xFF050508),
                shellBottom = Color(0xFF000000),
                shellHighlight = Color(0xFF15151E),
                shellShadow = Color(0xFF000000),
                bezel = Color(0xFF000000),
                bezelText = Color(0xFF3E4C7C),
                accent = Color(0xFF2B3F8F),
                accentSecondary = Color(0xFF1FA3B8),
                led = Color(0xFF1E9A4A),
                speakerStyle = GbaSpeakerStyle.SUBTLE,
                translucentShell = false,
                brushedMetal = false,
                cap = Color(0xFF121218),
                capPressed = Color(0xFF24367A),
                capIcon = Color(0xFF8490BD),
                capIconPressed = Color(0xFFCDD4F2),
                face = Color(0xFF121218),
                facePressed = Color(0xFF24367A),
                faceIcon = Color(0xFF8C9AD0),
                faceIconPressed = Color(0xFFCDD4F2),
                faceStroke = Color(0x992B3F8F),
                active = Color(0xFF1FA3B8),
                activeIcon = Color(0xFF000000),
                ring = Color(0xFF07070B),
                track = Color(0xFF0E0E14),
                trackPressed = Color(0xFF1C1C25),
            ),
    ),
    ;

    /** Whether this skin is the original glass look (no console deck backdrop). */
    val isDefault: Boolean
        get() = this == DEFAULT

    private val portraitTheme: LemuroidPadTheme by lazy { buildTheme(overlay = false) }
    private val overlayTheme: LemuroidPadTheme by lazy { buildTheme(overlay = true) }

    /**
     * Pad theme of this skin. With [overlay] (landscape, pads floating over the game video) every fill is
     * translucent so the game stays visible; otherwise the controls are solid, like real GBA buttons.
     */
    fun padTheme(overlay: Boolean): LemuroidPadTheme {
        return if (overlay) overlayTheme else portraitTheme
    }

    private fun buildTheme(overlay: Boolean): LemuroidPadTheme {
        if (this == CLASSIC_GLASS) return LemuroidPadTheme()
        val p = palette
        return if (overlay) {
            LemuroidPadTheme(
                icons = overlayLabel(p.capIcon, p.cap),
                iconsPressed = p.capIconPressed.copy(alpha = 0.95f),
                iconsActive = p.activeIcon,
                level3Fill = p.cap.copy(alpha = OVERLAY_CAP_ALPHA),
                level3FillPressed = p.capPressed.copy(alpha = 0.78f),
                level3FillActive = p.active.copy(alpha = 0.90f),
                level3Shadow = DefaultShadowColor.copy(0.08f),
                level3Stroke = p.accent.copy(alpha = 0.35f),
                primaryFill = p.face.copy(alpha = OVERLAY_FACE_ALPHA),
                primaryFillPressed = p.facePressed.copy(alpha = 0.80f),
                primaryIcons = overlayLabel(p.faceIcon, p.face),
                primaryIconsPressed = p.faceIconPressed.copy(alpha = 0.95f),
                primaryStroke = p.faceStroke.copy(alpha = p.faceStroke.alpha * 0.6f),
                level2Fill = p.track.copy(alpha = 0.30f),
                level2FillPressed = p.trackPressed.copy(alpha = 0.45f),
                level2Shadow = DefaultShadowColor.copy(0.05f),
                level1Fill = p.ring.copy(alpha = OVERLAY_RING_ALPHA),
                level1Shadow = DefaultShadowColor.copy(0.08f),
                level1Stroke = p.accent.copy(alpha = 0.45f),
                level0Fill = p.shellBottom.copy(alpha = 0.35f),
                level0Shadow = DefaultShadowColor.copy(0.10f),
            )
        } else {
            LemuroidPadTheme(
                icons = p.capIcon,
                iconsPressed = p.capIconPressed,
                iconsActive = p.activeIcon,
                level3Fill = p.cap,
                level3FillPressed = p.capPressed,
                level3FillActive = p.active,
                level3Shadow = DefaultShadowColor.copy(0.35f),
                level3Stroke = p.shellShadow.copy(alpha = 0.35f),
                primaryFill = p.face,
                primaryFillPressed = p.facePressed,
                primaryIcons = p.faceIcon,
                primaryIconsPressed = p.faceIconPressed,
                primaryStroke = p.faceStroke,
                level2Fill = p.track,
                level2FillPressed = p.trackPressed,
                level2Shadow = DefaultShadowColor.copy(0.25f),
                level1Fill = p.ring.copy(alpha = 0.92f),
                level1Shadow = DefaultShadowColor.copy(0.30f),
                level1Stroke = p.accent.copy(alpha = 0.55f),
                level0Fill = p.shellBottom,
                level0Shadow = DefaultShadowColor.copy(0.20f),
            )
        }
    }

    companion object {
        val DEFAULT = CLASSIC_GLASS

        const val OVERLAY_RING_ALPHA = 0.22f
        const val OVERLAY_CAP_ALPHA = 0.64f
        const val OVERLAY_FACE_ALPHA = 0.68f

        /**
         * Label color of a translucent overlay cap. Since the game video shows through the cap, the label is pushed
         * further away from the cap color (darker on light caps, lighter on dark caps) to stay readable on both dark
         * and bright scenes.
         */
        private fun overlayLabel(
            label: Color,
            cap: Color,
        ): Color {
            val target = if (label.luminance() < cap.luminance()) Color.Black else Color.White
            return lerp(label, target, OVERLAY_LABEL_PUSH).copy(alpha = 0.95f)
        }

        private const val OVERLAY_LABEL_PUSH = 0.35f

        /** Returns the skin with the given [id], falling back to [DEFAULT] for null, empty or unknown ids. */
        fun fromId(id: String?): GbaSkin {
            if (id.isNullOrBlank()) return DEFAULT
            val normalized = id.trim()
            return entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: DEFAULT
        }
    }
}
