package com.swordfish.lemuroid.app.mobile.feature.gbaskin

import androidx.annotation.StringRes
import com.swordfish.lemuroid.R
import com.swordfish.touchinput.radial.skins.GbaSkin

/** Localized name of the skin, used for the gallery cards, the chips and the menu subtitle. */
@get:StringRes
val GbaSkin.titleRes: Int
    get() =
        when (this) {
            GbaSkin.CLASSIC_GLASS -> R.string.gba_skin_classic_glass
            GbaSkin.INDIGO -> R.string.gba_skin_indigo
            GbaSkin.GLACIER -> R.string.gba_skin_glacier
            GbaSkin.PLATINUM_SP -> R.string.gba_skin_platinum_sp
            GbaSkin.FAMICOM -> R.string.gba_skin_famicom
            GbaSkin.EMERALD -> R.string.gba_skin_emerald
            GbaSkin.ATOMIC_PURPLE -> R.string.gba_skin_atomic_purple
            GbaSkin.MIDNIGHT_OLED -> R.string.gba_skin_midnight_oled
        }

/** Localized one line description of the skin. */
@get:StringRes
val GbaSkin.descriptionRes: Int
    get() =
        when (this) {
            GbaSkin.CLASSIC_GLASS -> R.string.gba_skin_classic_glass_description
            GbaSkin.INDIGO -> R.string.gba_skin_indigo_description
            GbaSkin.GLACIER -> R.string.gba_skin_glacier_description
            GbaSkin.PLATINUM_SP -> R.string.gba_skin_platinum_sp_description
            GbaSkin.FAMICOM -> R.string.gba_skin_famicom_description
            GbaSkin.EMERALD -> R.string.gba_skin_emerald_description
            GbaSkin.ATOMIC_PURPLE -> R.string.gba_skin_atomic_purple_description
            GbaSkin.MIDNIGHT_OLED -> R.string.gba_skin_midnight_oled_description
        }
