package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.common.graphics.takeScreenshot
import com.swordfish.lemuroid.common.longAnimationDuration
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.IncompatibleStateException
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SaveStateBundle
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

class GameViewModelSaves(
    private val appContext: Context,
    private val system: GameSystem,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val settingsManager: SettingsManager,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val sideEffects: GameViewModelSideEffects,
) {
    private var currentQuickSave: SaveState? = null

    @Volatile
    private var skipSaveOnFinish: Boolean = false

    data class SaveSnapshot(
        val sram: ByteArray,
        val autoSave: SaveState?,
    )

    suspend fun saveSlot(index: Int) {
        getCurrentSaveState()?.let {
            statesManager.setSlotSave(game, it, systemCoreConfig.coreID, index)
            runCatching {
                takeScreenshotPreview(index)
            }
        }
    }

    suspend fun loadSlot(index: Int) {
        try {
            statesManager.getSlotSave(game, systemCoreConfig.coreID, index)?.let {
                val loaded =
                    withContext(Dispatchers.IO) {
                        loadSaveState(it)
                    }

                if (!loaded) {
                    sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
                }
            }
        } catch (e: Throwable) {
            val errorMessageId =
                when (e) {
                    is IncompatibleStateException -> R.string.error_message_incompatible_state
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(errorMessageId))
        }
    }

    suspend fun captureSaveSnapshot(useEmulationThread: Boolean): SaveSnapshot? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val sramState = retroGameView.serializeSRAM(useEmulationThread)
        val autoSaveState = if (isAutoSaveEnabled()) getCurrentSaveState(useEmulationThread) else null
        return SaveSnapshot(sramState, autoSaveState)
    }

    suspend fun writeSaveSnapshot(snapshot: SaveSnapshot?) {
        if (snapshot == null || skipSaveOnFinish) return
        Timber.i(
            "GameViewModelSaves.write game=%s core=%s writingSram=%s writingAutoSave=%s",
            game.id,
            systemCoreConfig.coreID,
            true,
            snapshot.autoSave != null,
        )
        savesManager.setSaveRAM(game, snapshot.sram)
        snapshot.autoSave?.let { statesManager.setAutoSave(game, systemCoreConfig.coreID, it) }
    }

    // On some cores unserialize fails with no reason. So we need to try multiple times.
    suspend fun restoreAutoSaveAsync(saveState: SaveState) {
        // PPSSPP and Mupen64 initialize some state while rendering the first frame, so we have to wait before restoring
        // the autosave. Do not change thread here. Stick to the GL one to avoid issues with PPSSPP.
        if (!isAutoSaveEnabled()) return

        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            restoreQuickSave(saveState)
        } catch (e: Throwable) {
            Timber.e(e, "Error while loading auto-save")
        }
    }

    private fun getCurrentSaveState(useEmulationThread: Boolean = true): SaveState? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val currentDisk =
            if (system.hasMultiDiskSupport) {
                retroGameView.getCurrentDisk(useEmulationThread)
            } else {
                0
            }
        return SaveState(
            retroGameView.serializeState(useEmulationThread),
            SaveState.Metadata(currentDisk, systemCoreConfig.statesVersion),
        )
    }

    private suspend fun isAutoSaveEnabled(): Boolean {
        return systemCoreConfig.statesSupported && settingsManager.autoSave()
    }

    private suspend fun takeScreenshotPreview(index: Int) {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt()
        val preview = retroGameView.retroGameView?.takeScreenshot(previewSize, 3)
        if (preview != null) {
            statesPreviewManager.setPreviewForSlot(game, preview, systemCoreConfig.coreID, index)
        }
    }

    // Now that we wait for the first rendered frame this is probably no longer needed, but we'll keep it just to be sure
    private suspend fun restoreQuickSave(saveState: SaveState) {
        var times = 10

        while (!loadSaveState(saveState) && times > 0) {
            delay(200)
            times--
        }
    }

    private fun loadSaveState(saveState: SaveState): Boolean {
        val retroGameView = retroGameView.retroGameView ?: return false

        if (systemCoreConfig.statesVersion != saveState.metadata.version) {
            throw IncompatibleStateException()
        }

        if (system.hasMultiDiskSupport &&
            retroGameView.getAvailableDisks() > 1 &&
            retroGameView.getCurrentDisk() != saveState.metadata.diskIndex
        ) {
            retroGameView.changeDisk(saveState.metadata.diskIndex)
        }

        return retroGameView.unserializeState(saveState.state)
    }

    fun saveQuickSave() {
        currentQuickSave = getCurrentSaveState()
        sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_saved))
    }

    fun loadQuickSave() {
        loadSaveState(currentQuickSave ?: return)
        sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_loaded))
    }

    suspend fun exportSaveRam(uri: Uri) {
        val retroView = retroGameView.retroGameView ?: return
        try {
            withContext(Dispatchers.IO) {
                val bytes = retroView.serializeSRAM(false)
                if (bytes.isEmpty()) {
                    sideEffects.showToast(appContext.getString(R.string.game_toast_save_export_no_data))
                    return@withContext
                }

                val outputStream =
                    openTruncatingOutputStream(uri)
                        ?: throw IOException("Unable to open output stream")
                outputStream.use { out ->
                    out.write(bytes)
                    out.flush()
                }

                savesManager.setSaveRAM(game, bytes)
                sideEffects.showToast(appContext.getString(R.string.game_toast_save_exported))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Error while exporting SRAM")
            sideEffects.showToast(
                appContext.getString(
                    R.string.game_toast_save_transfer_failed,
                    e.localizedMessage ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun importSaveRam(uri: Uri) {
        val retroView = retroGameView.retroGameView ?: return
        try {
            withContext(Dispatchers.IO) {
                val expected = retroView.serializeSRAM(false).size
                if (expected == 0) {
                    sideEffects.showToast(appContext.getString(R.string.game_toast_save_import_unsupported))
                    return@withContext
                }

                val readResult =
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        readSramInput(input, expected)
                    } ?: throw IOException("Unable to open input stream")

                if (readResult.totalSize == 0L) {
                    sideEffects.showToast(
                        appContext.getString(
                            R.string.game_toast_save_transfer_failed,
                            "Empty file",
                        ),
                    )
                    return@withContext
                }

                if (readResult.totalSize > expected) {
                    val reportedSize = readResult.totalSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    sideEffects.showToast(
                        appContext.getString(
                            R.string.game_toast_save_import_size_mismatch,
                            expected,
                            reportedSize,
                        ),
                    )
                    return@withContext
                }

                val bytes = readResult.bytes
                val ok = retroView.unserializeSRAM(bytes, false)
                if (!ok) {
                    sideEffects.showToast(
                        appContext.getString(
                            R.string.game_toast_save_transfer_failed,
                            "Failed to apply save data",
                        ),
                    )
                    return@withContext
                }

                savesManager.setSaveRAM(game, bytes)
                statesManager.deleteAutoSave(game, systemCoreConfig.coreID)

                delay(appContext.longAnimationDuration().toLong())
                retroView.reset(true)

                val postResetSram = retroView.serializeSRAM(false)
                val hadNonZero = bytes.any { it != 0.toByte() }
                val allZeroAfter =
                    postResetSram.size >= bytes.size &&
                        (0 until bytes.size).all { postResetSram[it] == 0.toByte() }

                if (hadNonZero && allZeroAfter) {
                    Timber.w("Core zeroed SRAM on reset; finishing without overwriting imported SRAM on disk")
                    skipSaveOnFinish = true
                    sideEffects.showToast(appContext.getString(R.string.game_toast_save_imported_restart))
                    sideEffects.requestSuccessfulFinish()
                    return@withContext
                }

                sideEffects.showToast(appContext.getString(R.string.game_toast_save_imported_restart))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Error while importing SRAM")
            sideEffects.showToast(
                appContext.getString(
                    R.string.game_toast_save_transfer_failed,
                    e.localizedMessage ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun exportState(
        slot: Int,
        uri: Uri,
    ) {
        try {
            withContext(Dispatchers.IO) {
                val saveState = statesManager.getSlotSave(game, systemCoreConfig.coreID, slot)
                if (saveState == null) {
                    sideEffects.showToast(
                        appContext.getString(
                            R.string.game_toast_save_transfer_failed,
                            "Slot ${slot + 1} is empty",
                        ),
                    )
                    return@withContext
                }

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
                                systemCoreConfig.coreID,
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
                        coreName = systemCoreConfig.coreID.coreName,
                        systemId = game.systemId,
                        gameFileName = game.fileName,
                        slot = slot + 1,
                        exportedAt = System.currentTimeMillis(),
                    )

                val outputStream =
                    openTruncatingOutputStream(uri)
                        ?: throw IOException("Unable to open output stream")
                outputStream.use { out ->
                    SaveStateBundle.write(out, manifest, saveState, previewBytes)
                }

                sideEffects.showToast(
                    appContext.getString(R.string.game_toast_state_exported, (slot + 1).toString()),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Error while exporting save state")
            sideEffects.showToast(
                appContext.getString(
                    R.string.game_toast_save_transfer_failed,
                    e.localizedMessage ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun importState(
        slot: Int,
        uri: Uri,
    ) {
        try {
            withContext(Dispatchers.IO) {
                val inputStream =
                    appContext.contentResolver.openInputStream(uri)
                        ?: throw IOException("Unable to open input stream")

                val bundleResult = inputStream.use { SaveStateBundle.read(it) }
                val (manifest, saveState, previewJpeg) =
                    bundleResult.getOrElse { error ->
                        Timber.w(error, "Invalid save state bundle")
                        sideEffects.showToast(appContext.getString(R.string.game_toast_state_import_invalid))
                        return@withContext
                    }

                if (manifest.coreName != systemCoreConfig.coreID.coreName) {
                    sideEffects.showToast(
                        appContext.getString(
                            R.string.game_toast_state_import_wrong_core,
                            manifest.coreName,
                        ),
                    )
                    return@withContext
                }

                if (saveState.metadata.version != systemCoreConfig.statesVersion) {
                    sideEffects.showToast(appContext.getString(R.string.error_message_incompatible_state))
                    return@withContext
                }

                statesManager.setSlotSave(game, saveState, systemCoreConfig.coreID, slot)

                if (previewJpeg != null && previewJpeg.isNotEmpty()) {
                    runCatching {
                        val bitmap = BitmapFactory.decodeByteArray(previewJpeg, 0, previewJpeg.size)
                        if (bitmap != null) {
                            statesPreviewManager.setPreviewForSlot(game, bitmap, systemCoreConfig.coreID, slot)
                        }
                    }
                }

                sideEffects.showToast(
                    appContext.getString(R.string.game_toast_state_imported, (slot + 1).toString()),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Error while importing save state")
            sideEffects.showToast(
                appContext.getString(
                    R.string.game_toast_save_transfer_failed,
                    e.localizedMessage ?: e.javaClass.simpleName,
                ),
            )
        }
    }

    private fun openTruncatingOutputStream(uri: Uri): OutputStream? {
        val truncated =
            runCatching {
                appContext.contentResolver.openOutputStream(uri, "wt")
            }.getOrNull()
        return truncated ?: appContext.contentResolver.openOutputStream(uri, "w")
    }

    private data class SramReadResult(
        val bytes: ByteArray,
        val totalSize: Long,
    )

    private fun readSramInput(
        input: InputStream,
        keepLimit: Int,
    ): SramReadResult {
        val maxKeep = minOf(keepLimit.toLong(), MAX_SRAM_BYTES)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(IO_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (total <= maxKeep) {
                val toKeep = minOf(read.toLong(), maxKeep - total).toInt()
                if (toKeep > 0) {
                    output.write(buffer, 0, toKeep)
                }
            }
            total += read
            if (total > MAX_SRAM_BYTES) {
                break
            }
        }
        return SramReadResult(output.toByteArray(), total)
    }

    companion object {
        private const val MAX_SRAM_BYTES = 64L * 1024L * 1024L
        private const val IO_BUFFER_SIZE = 8 * 1024
    }
}
