package com.swordfish.lemuroid.app.shared

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.game.GameProcessLock
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferException
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferInfo
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferKind
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferManager
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferRequest
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(DelicateCoroutinesApi::class)
class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val customCoverManager: CustomCoverManager,
    savesManager: SavesManager,
    statesManager: StatesManager,
    statesPreviewManager: StatesPreviewManager,
    coresSelection: CoresSelection,
) {
    private val saveTransferManager =
        SaveTransferManager(
            appContext = activity.activity().applicationContext,
            savesManager = savesManager,
            statesManager = statesManager,
            statesPreviewManager = statesPreviewManager,
            coresSelection = coresSelection,
        )

    fun onGamePlay(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
    }

    fun onGameRestart(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(isFavorite = isFavorite))
        }
    }

    fun onCreateShortcut(game: Game) {
        GlobalScope.launch {
            shortcutsGenerator.pinShortcutForGame(game)
        }
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }

    fun onSetCustomName(
        game: Game,
        customName: String,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(customName = customName))
        }
    }

    fun onClearCustomName(game: Game) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(customName = null))
        }
    }

    /**
     * Copies the image at [sourceUri] into app-private storage, then stores
     * the local file URI in the database.  This ensures the cover survives
     * app restarts regardless of where the user picked the file from.
     */
    fun onSetCustomCoverUri(
        game: Game,
        sourceUri: String,
    ) {
        GlobalScope.launch {
            val localUri = customCoverManager.importCover(game.id, Uri.parse(sourceUri))
            retrogradeDb.gameDao().update(game.copy(customCoverUri = localUri))
        }
    }

    /**
     * Removes the custom cover: deletes the file from private storage and
     * clears the database field.
     */
    fun onClearCustomCoverUri(game: Game) {
        GlobalScope.launch {
            customCoverManager.deleteCover(game.id)
            retrogradeDb.gameDao().update(game.copy(customCoverUri = null))
        }
    }

    suspend fun getSaveTransferInfo(game: Game): SaveTransferInfo = saveTransferManager.loadInfo(game)

    fun canStartSaveTransfer(): Boolean {
        if (!ensureNotBusy()) {
            return false
        }
        val appContext = activity.activity().applicationContext
        if (GameProcessLock.isHeldByAnotherProcess(appContext)) {
            Toast.makeText(appContext, R.string.save_transfer_game_running, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    fun onSaveTransfer(
        request: SaveTransferRequest,
        uri: Uri,
    ) {
        val appContext = activity.activity().applicationContext
        if (activity.isBusy()) {
            Toast.makeText(appContext, R.string.game_interactory_busy, Toast.LENGTH_LONG).show()
            return
        }
        val manager = saveTransferManager
        GlobalScope.launch(Dispatchers.IO) {
            val message =
                try {
                    if (GameProcessLock.isHeldByAnotherProcess(appContext)) {
                        throw SaveTransferException(R.string.save_transfer_game_running)
                    }
                    when (request.kind) {
                        SaveTransferKind.EXPORT_SRAM -> manager.exportSaveRam(request.game, uri)
                        SaveTransferKind.IMPORT_SRAM -> manager.importSaveRam(request.game, uri)
                        SaveTransferKind.EXPORT_STATE ->
                            manager.exportState(request.game, request.slot, uri)
                        SaveTransferKind.IMPORT_STATE ->
                            manager.importState(request.game, request.slot, uri)
                    }
                } catch (e: SaveTransferException) {
                    e.formatMessage(appContext)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Timber.e(e, "Save transfer failed")
                    appContext.getString(
                        R.string.save_transfer_failed,
                        e.localizedMessage ?: e.javaClass.simpleName,
                    )
                }
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun ensureNotificationsPermissionAvailable(): Boolean {
        if (useLeanback || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                activity.activity(),
                Manifest.permission.POST_NOTIFICATIONS,
            )

        if (permissionResult == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        activity.activity().displayToast(R.string.game_interactor_notification_permission_required)
        return false
    }

    private fun ensureNotBusy(): Boolean {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return false
        }
        return true
    }
}
