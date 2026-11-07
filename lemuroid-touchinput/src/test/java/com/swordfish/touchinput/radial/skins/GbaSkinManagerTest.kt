package com.swordfish.touchinput.radial.skins

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GbaSkinManagerTest {
    @Test
    fun defaultsToClassicGlassWhenNothingIsStored() {
        val manager = GbaSkinManager(FakeSharedPreferences())
        assertSame(GbaSkin.CLASSIC_GLASS, manager.currentSkin())
        assertSame(GbaSkin.CLASSIC_GLASS, manager.skin.value)
    }

    @Test
    fun setSkinUpdatesStateFlowAndPersists() {
        val preferences = FakeSharedPreferences()
        val manager = GbaSkinManager(preferences)

        manager.setSkin(GbaSkin.FAMICOM)
        assertSame(GbaSkin.FAMICOM, manager.skin.value)
        assertEquals("famicom", preferences.getString(GbaSkinManager.PREFERENCE_KEY, null))

        manager.setSkin(GbaSkin.MIDNIGHT_OLED)
        assertSame(GbaSkin.MIDNIGHT_OLED, manager.currentSkin())
        assertEquals("midnight_oled", preferences.getString(GbaSkinManager.PREFERENCE_KEY, null))
    }

    @Test
    fun restoresThePersistedSkin() {
        val preferences = FakeSharedPreferences()
        GbaSkinManager(preferences).setSkin(GbaSkin.EMERALD)
        assertSame(GbaSkin.EMERALD, GbaSkinManager(preferences).skin.value)
    }

    @Test
    fun everySkinSurvivesARestart() {
        val preferences = FakeSharedPreferences()
        GbaSkin.entries.forEach {
            GbaSkinManager(preferences).setSkin(it)
            assertSame(it, GbaSkinManager(preferences).currentSkin())
        }
    }

    @Test
    fun unknownOrCorruptedValuesFallBackToClassicGlass() {
        val unknown = FakeSharedPreferences().apply { values[GbaSkinManager.PREFERENCE_KEY] = "gameboy_pink" }
        assertSame(GbaSkin.CLASSIC_GLASS, GbaSkinManager(unknown).currentSkin())

        val empty = FakeSharedPreferences().apply { values[GbaSkinManager.PREFERENCE_KEY] = "" }
        assertSame(GbaSkin.CLASSIC_GLASS, GbaSkinManager(empty).currentSkin())

        val wrongType = FakeSharedPreferences().apply { values[GbaSkinManager.PREFERENCE_KEY] = 42 }
        assertSame(GbaSkin.CLASSIC_GLASS, GbaSkinManager(wrongType).currentSkin())
    }

    /** Minimal in-memory [SharedPreferences]; unit tests run without the Android framework. */
    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String,
            defValue: String?,
        ): String? {
            if (!values.containsKey(key)) return defValue
            return values[key] as String?
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (values[key] as MutableSet<String>?) ?: defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as Int? ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as Long? ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = values[key] as Float? ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = values[key] as Boolean? ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(
                key: String,
                value: String?,
            ) = apply { pending[key] = value }

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?,
            ) = apply { pending[key] = values }

            override fun putInt(
                key: String,
                value: Int,
            ) = apply { pending[key] = value }

            override fun putLong(
                key: String,
                value: Long,
            ) = apply { pending[key] = value }

            override fun putFloat(
                key: String,
                value: Float,
            ) = apply { pending[key] = value }

            override fun putBoolean(
                key: String,
                value: Boolean,
            ) = apply { pending[key] = value }

            override fun remove(key: String) = apply { removed += key }

            override fun clear() = apply { clear = true }

            override fun commit(): Boolean {
                if (clear) values.clear()
                removed.forEach { values.remove(it) }
                values.putAll(pending)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
