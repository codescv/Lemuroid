package com.swordfish.lemuroid.app.shared.savetransfer

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.io.Serializable

enum class SaveTransferKind {
    EXPORT_SRAM,
    IMPORT_SRAM,
    EXPORT_STATE,
    IMPORT_STATE,
}

data class SaveTransferRequest(
    val game: Game,
    val kind: SaveTransferKind,
    val slot: Int = 0,
) : Serializable {
    fun defaultFileName(): String =
        when (kind) {
            SaveTransferKind.EXPORT_SRAM -> {
                val base = game.fileName.substringBeforeLast('.').ifBlank { game.fileName }
                "$base.srm"
            }
            SaveTransferKind.EXPORT_STATE -> "${game.fileName}.slot${slot + 1}.lemuroid-state.zip"
            SaveTransferKind.IMPORT_SRAM,
            SaveTransferKind.IMPORT_STATE,
            -> ""
        }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class SaveTransferSlot(
    val index: Int,
    val title: String,
    val description: String,
    val exists: Boolean,
    val preview: Bitmap?,
)

data class SaveTransferInfo(
    val gameRunning: Boolean,
    val statesSupported: Boolean,
    val sramExists: Boolean,
    val slots: List<SaveTransferSlot>,
    val error: String? = null,
)

class SaveTransferException(
    @StringRes val messageRes: Int,
    vararg val args: Any,
) : Exception() {
    fun formatMessage(context: Context): String =
        if (args.isEmpty()) {
            context.getString(messageRes)
        } else {
            context.getString(messageRes, *args)
        }
}
