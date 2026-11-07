package com.swordfish.lemuroid.app.mobile.feature.gbaskin

import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.touchinput.radial.skins.GbaSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GbaSkinResourcesTest {
    @Test
    fun everySkinHasDistinctTitleAndDescription() {
        val titles = GbaSkin.entries.map { it.titleRes }
        val descriptions = GbaSkin.entries.map { it.descriptionRes }
        assertEquals(GbaSkin.entries.size, titles.toSet().size)
        assertEquals(GbaSkin.entries.size, descriptions.toSet().size)
        assertTrue(titles.intersect(descriptions.toSet()).isEmpty())
    }

    @Test
    fun skinStringsAreTranslated() {
        val expectedKeys =
            GbaSkin.entries.flatMap { listOf("gba_skin_${it.id}", "gba_skin_${it.id}_description") } +
                listOf("game_menu_gba_skin", "gba_skin_selected")

        val default = readStrings("values")
        expectedKeys.forEach { assertTrue("Missing default string $it", default.containsKey(it)) }

        for (locale in listOf("values-en-rUS", "values-zh-rCN", "values-zh-rTW")) {
            val strings = readStrings(locale)
            expectedKeys.forEach { key ->
                val value = strings[key]
                assertTrue("$locale is missing $key", !value.isNullOrBlank())
                // An unescaped apostrophe breaks aapt.
                assertFalse("$locale $key has an unescaped apostrophe", Regex("""(?<!\\)'""").containsMatchIn(value!!))
            }
        }
        assertNotEquals(readStrings("values-zh-rCN")["gba_skin_indigo"], default["gba_skin_indigo"])
        assertEquals("GBA 皮肤", readStrings("values-zh-rCN")["game_menu_gba_skin"])
    }

    @Test
    fun menuContractKeysAreUnique() {
        val keys =
            listOf(
                GameMenuContract.EXTRA_GBA_SKIN,
                GameMenuContract.RESULT_CHANGE_GBA_SKIN,
                GameMenuContract.EXTRA_GAME,
                GameMenuContract.EXTRA_FAST_FORWARD,
                GameMenuContract.EXTRA_TILT_ALL_CONFIGS,
                GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS,
                GameMenuContract.RESULT_CHANGE_TILT_CONFIG,
                GameMenuContract.RESULT_ENABLE_FAST_FORWARD,
            )
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun menuRoundTripPreservesTheSkin() {
        GbaSkin.entries.forEach { skin ->
            // GameMenuActivity receives the id as an extra and returns the selected id as a result.
            assertEquals(skin, GbaSkin.fromId(skin.id))
        }
        // Callers that never pass the extra (e.g. TV) fall back to the classic glass.
        assertEquals(GbaSkin.CLASSIC_GLASS, GbaSkin.fromId(null))
    }

    private fun readStrings(directory: String): Map<String, String> {
        val file = File("src/main/res/$directory/strings.xml")
        assertTrue("Missing ${file.path}", file.exists())
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
}
