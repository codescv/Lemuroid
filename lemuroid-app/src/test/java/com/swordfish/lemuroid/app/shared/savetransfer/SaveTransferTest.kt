package com.swordfish.lemuroid.app.shared.savetransfer

import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SaveStateBundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class SaveTransferTest {
    private fun sampleGame(
        fileName: String = "Pokemon Emerald (U).gba",
        systemId: String = "gba",
    ) = Game(
        id = 7,
        fileName = fileName,
        fileUri = "content://roms/$fileName",
        title = "Pokemon Emerald",
        systemId = systemId,
        developer = "Game Freak",
        coverFrontUrl = null,
        lastIndexedAt = 1000L,
    )

    @Test
    fun defaultFileNameFormatsSramAndStateNames() {
        val game = sampleGame("Chrono.Trigger.sfc", "snes")
        assertEquals(
            "Chrono.Trigger.srm",
            SaveTransferRequest(game, SaveTransferKind.EXPORT_SRAM).defaultFileName(),
        )
        assertEquals(
            "Chrono.Trigger.sfc.slot1.lemuroid-state.zip",
            SaveTransferRequest(game, SaveTransferKind.EXPORT_STATE, slot = 0).defaultFileName(),
        )
        assertEquals(
            "Chrono.Trigger.sfc.slot4.lemuroid-state.zip",
            SaveTransferRequest(game, SaveTransferKind.EXPORT_STATE, slot = 3).defaultFileName(),
        )
        assertEquals(
            "",
            SaveTransferRequest(game, SaveTransferKind.IMPORT_SRAM).defaultFileName(),
        )
        assertEquals(
            "",
            SaveTransferRequest(game, SaveTransferKind.IMPORT_STATE, slot = 2).defaultFileName(),
        )
    }

    @Test
    fun defaultFileNameHandlesDotfileOrExtensionlessRom() {
        val noExt = sampleGame("romfile", "gba")
        assertEquals(
            "romfile.srm",
            SaveTransferRequest(noExt, SaveTransferKind.EXPORT_SRAM).defaultFileName(),
        )
        val dotFile = sampleGame(".hiddenrom", "gba")
        assertEquals(
            ".hiddenrom.srm",
            SaveTransferRequest(dotFile, SaveTransferKind.EXPORT_SRAM).defaultFileName(),
        )
    }

    @Test
    fun saveTransferRequestSurvivesJavaSerialization() {
        val original = SaveTransferRequest(sampleGame(), SaveTransferKind.IMPORT_STATE, slot = 2)
        val serialized =
            ByteArrayOutputStream().use { bos ->
                ObjectOutputStream(bos).use { oos ->
                    oos.writeObject(original)
                }
                bos.toByteArray()
            }
        val restored =
            ByteArrayInputStream(serialized).use { bis ->
                ObjectInputStream(bis).use { ois ->
                    ois.readObject() as SaveTransferRequest
                }
            }
        assertEquals(original, restored)
    }

    @Test
    fun hasZipMagicDetectsZipArchiveAndAllowsRawSram() {
        val zipBytes =
            ByteArrayOutputStream().let { bos ->
                SaveStateBundle.write(
                    bos,
                    SaveStateBundle.Manifest(
                        coreName = "mgba",
                        systemId = "gba",
                        gameFileName = "test.gba",
                        slot = 1,
                    ),
                    SaveState(byteArrayOf(1, 2, 3, 4), SaveState.Metadata(0, 1)),
                )
                bos.toByteArray()
            }
        assertTrue(SaveTransferManager.hasZipMagic(zipBytes))
        assertFalse(SaveTransferManager.hasZipMagic(byteArrayOf(0x00, 0x01, 0x02, 0x03)))
        assertFalse(SaveTransferManager.hasZipMagic(byteArrayOf(0x50, 0x4B)))
        assertFalse(SaveTransferManager.hasZipMagic(ByteArray(0)))
    }

    @Test
    fun readBoundedSramHandlesEmptyExactLimitAndOversizeStreams() {
        val empty = SaveTransferManager.readBoundedSram(ByteArrayInputStream(ByteArray(0)), maxBytes = 64)
        assertEquals(0L, empty.totalSize)
        assertEquals(0, empty.bytes.size)

        val exactPayload = ByteArray(64) { (it + 1).toByte() }
        val exact = SaveTransferManager.readBoundedSram(ByteArrayInputStream(exactPayload), maxBytes = 64)
        assertEquals(64L, exact.totalSize)
        assertArrayEquals(exactPayload, exact.bytes)

        val oversizePayload = ByteArray(200) { 0x5A }
        val oversize = SaveTransferManager.readBoundedSram(ByteArrayInputStream(oversizePayload), maxBytes = 64)
        assertTrue(oversize.totalSize > 64L)
        assertEquals(64, oversize.bytes.size)
    }
}
