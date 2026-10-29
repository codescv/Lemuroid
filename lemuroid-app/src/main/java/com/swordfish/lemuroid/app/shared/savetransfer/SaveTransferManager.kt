package com.swordfish.lemuroid.app.shared.savetransfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.app.shared.gamemenu.GameMenuHelper
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SaveStateBundle
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class SaveTransferManager(
    private val appContext: Context,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val coresSelection: CoresSelection,
) {
    private suspend fun coreConfig(game: Game): SystemCoreConfig {
        val system = GameSystem.findById(game.systemId)
        return coresSelection.getCoreConfigForSystem(system)
    }

    suspend fun loadInfo(game: Game): SaveTransferInfo =
        withContext(Dispatchers.IO) {
            val running =
                runCatching {
                    GameProcessLock.isHeldByAnotherProcess(appContext)
                }.getOrDefault(false)
            if (running) {
                return@withContext SaveTransferInfo(
                    gameRunning = true,
                    statesSupported = false,
                    sramExists = false,
                    slots = emptyList(),
                )
            }
            runCatching {
                val cfg = coreConfig(game)
                val sramExists = savesManager.getSaveRAMInfo(game).exists
                val slots =
                    if (cfg.statesSupported) {
                        statesManager.getSavedSlotsInfo(game, cfg.coreID).mapIndexed { index, info ->
                            val preview =
                                runCatching {
                                    GameMenuHelper.getSaveStateBitmap(
                                        appContext,
                                        statesPreviewManager,
                                        info,
                                        game,
                                        cfg.coreID,
                                        index,
                                    )
                                }.getOrNull()
                            SaveTransferSlot(
                                index = index,
                                title = appContext.getString(R.string.game_menu_state, (index + 1).toString()),
                                description = GameMenuHelper.getSaveStateDescription(info),
                                exists = info.exists,
                                preview = preview,
                            )
                        }
                    } else {
                        emptyList()
                    }
                SaveTransferInfo(
                    gameRunning = false,
                    statesSupported = cfg.statesSupported,
                    sramExists = sramExists,
                    slots = slots,
                )
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Timber.e(error, "Error loading save transfer info for game=%s", game.id)
                SaveTransferInfo(
                    gameRunning = running,
                    statesSupported = false,
                    sramExists = false,
                    slots = emptyList(),
                    error =
                        appContext.getString(
                            R.string.save_transfer_failed,
                            error.localizedMessage ?: error.javaClass.simpleName,
                        ),
                )
            }
        }

    suspend fun exportSaveRam(
        game: Game,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            val cfg = coreConfig(game)
            val bytes = savesManager.getSaveRAM(game, cfg)
            if (bytes == null || bytes.isEmpty()) {
                throw SaveTransferException(R.string.save_transfer_sram_empty)
            }
            val outputStream =
                openTruncatingOutputStream(uri)
                    ?: throw IOException("Unable to open output stream")
            outputStream.use { out ->
                out.write(bytes)
                out.flush()
            }
            appContext.getString(R.string.save_transfer_sram_exported)
        }

    suspend fun importSaveRam(
        game: Game,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            val readResult =
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    readBoundedSram(input, MAX_SRAM_BYTES)
                } ?: throw IOException("Unable to open input stream")

            if (readResult.totalSize == 0L) {
                throw SaveTransferException(R.string.save_transfer_file_empty)
            }
            if (readResult.totalSize > MAX_SRAM_BYTES) {
                throw SaveTransferException(R.string.save_transfer_file_too_large, MAX_SRAM_MIB)
            }
            val bytes = readResult.bytes
            if (hasZipMagic(bytes)) {
                throw SaveTransferException(R.string.save_transfer_not_sram)
            }

            val system = GameSystem.findById(game.systemId)
            val cfg = coresSelection.getCoreConfigForSystem(system)
            savesManager.setSaveRAM(game, bytes)

            val written =
                savesManager.getSaveRAMInfo(game).exists &&
                    savesManager.getSaveRAM(game, cfg)?.contentEquals(bytes) == true
            if (!written) {
                throw SaveTransferException(R.string.save_transfer_failed, "write failed")
            }

            system.systemCoreConfigs.forEach { coreCfg ->
                statesManager.deleteAutoSave(game, coreCfg.coreID)
            }

            appContext.getString(R.string.save_transfer_sram_imported)
        }

    suspend fun exportState(
        game: Game,
        slot: Int,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            if (slot !in 0 until StatesManager.MAX_STATES) {
                throw SaveTransferException(R.string.save_transfer_failed, "invalid slot")
            }
            val cfg = coreConfig(game)
            if (!cfg.statesSupported) {
                throw SaveTransferException(R.string.save_transfer_states_unsupported)
            }
            val saveState =
                statesManager.getSlotSave(game, cfg.coreID, slot)
                    ?: throw SaveTransferException(R.string.save_transfer_state_empty, slot + 1)

            val previewBytes =
                runCatching {
                    val previewSize =
                        GraphicsUtils.convertDpToPixel(
                            StatesPreviewManager.PREVIEW_SIZE_DP,
                            appContext,
                        ).roundToInt()
                    val bitmap =
                        statesPreviewManager.getPreviewForSlot(
                            game,
                            cfg.coreID,
                            slot,
                            previewSize,
                        )
                    bitmap?.let { bmp ->
                        ByteArrayOutputStream().use { bos ->
                            if (bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)) {
                                bos.toByteArray()
                            } else {
                                null
                            }
                        }
                    }
                }.getOrNull()

            val manifest =
                SaveStateBundle.Manifest(
                    coreName = cfg.coreID.coreName,
                    systemId = game.systemId,
                    gameFileName = game.fileName,
                    slot = slot + 1,
                )

            val outputStream =
                openTruncatingOutputStream(uri)
                    ?: throw IOException("Unable to open output stream")
            outputStream.use { out ->
                SaveStateBundle.write(out, manifest, saveState, previewBytes)
            }

            appContext.getString(R.string.save_transfer_state_exported, slot + 1)
        }

    suspend fun importState(
        game: Game,
        slot: Int,
        uri: Uri,
    ): String =
        withContext(Dispatchers.IO) {
            if (slot !in 0 until StatesManager.MAX_STATES) {
                throw SaveTransferException(R.string.save_transfer_failed, "invalid slot")
            }
            val cfg = coreConfig(game)
            if (!cfg.statesSupported) {
                throw SaveTransferException(R.string.save_transfer_states_unsupported)
            }

            val inputStream =
                appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open input stream")

            val (manifest, saveState, previewJpeg) =
                inputStream.use { SaveStateBundle.read(it) }
                    .getOrElse { error ->
                        Timber.w(error, "Invalid save state bundle")
                        throw SaveTransferException(R.string.save_transfer_state_invalid)
                    }

            if (manifest.systemId != game.systemId) {
                throw SaveTransferException(R.string.save_transfer_state_wrong_system, manifest.systemId)
            }

            if (manifest.coreName != cfg.coreID.coreName) {
                throw SaveTransferException(R.string.save_transfer_state_wrong_core, manifest.coreName)
            }

            if (saveState.metadata.version != cfg.statesVersion) {
                throw SaveTransferException(R.string.error_message_incompatible_state)
            }

            statesManager.setSlotSave(game, saveState, cfg.coreID, slot)

            val slotExists = statesManager.getSavedSlotsInfo(game, cfg.coreID).getOrNull(slot)?.exists == true
            val onDisk = if (slotExists) statesManager.getSlotSave(game, cfg.coreID, slot) else null
            if (!slotExists ||
                onDisk == null ||
                !onDisk.state.contentEquals(saveState.state) ||
                onDisk.metadata != saveState.metadata
            ) {
                throw SaveTransferException(R.string.save_transfer_failed, "write failed")
            }

            if (previewJpeg != null && previewJpeg.isNotEmpty()) {
                runCatching {
                    val bitmap = BitmapFactory.decodeByteArray(previewJpeg, 0, previewJpeg.size)
                    if (bitmap != null) {
                        statesPreviewManager.setPreviewForSlot(game, bitmap, cfg.coreID, slot)
                    }
                }
            }

            appContext.getString(R.string.save_transfer_state_imported, slot + 1)
        }

    private fun openTruncatingOutputStream(uri: Uri): OutputStream? {
        val truncated =
            runCatching {
                appContext.contentResolver.openOutputStream(uri, "wt")
            }.getOrNull()
        return truncated ?: appContext.contentResolver.openOutputStream(uri, "w")
    }

    internal data class SramReadResult(
        val bytes: ByteArray,
        val totalSize: Long,
    )

    companion object {
        internal const val MAX_SRAM_MIB = 16
        internal const val MAX_SRAM_BYTES = MAX_SRAM_MIB * 1024L * 1024L
        private const val IO_BUFFER_SIZE = 8 * 1024

        internal fun hasZipMagic(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()

        internal fun readBoundedSram(
            input: InputStream,
            maxBytes: Long = MAX_SRAM_BYTES,
        ): SramReadResult {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(IO_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (total < maxBytes) {
                    val toKeep = minOf(read.toLong(), maxBytes - total).toInt()
                    if (toKeep > 0) {
                        output.write(buffer, 0, toKeep)
                    }
                }
                total += read
                if (total > maxBytes) {
                    break
                }
            }
            return SramReadResult(output.toByteArray(), total)
        }
    }
}
