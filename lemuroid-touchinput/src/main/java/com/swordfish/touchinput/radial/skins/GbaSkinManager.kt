package com.swordfish.touchinput.radial.skins

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the selected [GbaSkin] and exposes it as a [StateFlow], so the game screen re-renders immediately
 * when the user picks another skin (from the game menu or from the edit touch controls dialog).
 *
 * The game screen and the game menu both live in the `:game` process; the menu only receives the current skin
 * through its intent and returns the new one as a result, so this manager is the single writer.
 */
class GbaSkinManager(private val sharedPreferences: SharedPreferences) {
    private val skinState = MutableStateFlow(readSkin())

    val skin: StateFlow<GbaSkin> = skinState.asStateFlow()

    fun currentSkin(): GbaSkin {
        return skinState.value
    }

    fun setSkin(skin: GbaSkin) {
        skinState.value = skin
        sharedPreferences.edit().putString(PREFERENCE_KEY, skin.id).apply()
    }

    private fun readSkin(): GbaSkin {
        val storedId = runCatching { sharedPreferences.getString(PREFERENCE_KEY, null) }.getOrNull()
        return GbaSkin.fromId(storedId)
    }

    companion object {
        const val PREFERENCE_KEY = "touch_gba_skin"
    }
}
