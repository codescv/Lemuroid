package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferInfo
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferKind
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferRequest
import com.swordfish.lemuroid.app.shared.savetransfer.SaveTransferSlot
import com.swordfish.lemuroid.lib.library.db.entity.Game

private sealed interface SaveTransferStep {
    data object Menu : SaveTransferStep

    data class PickSlot(val kind: SaveTransferKind) : SaveTransferStep
}

@Composable
fun SaveTransferDialog(
    game: Game,
    loadInfo: suspend (Game) -> SaveTransferInfo,
    onDismiss: () -> Unit,
    onRequest: (SaveTransferRequest) -> Unit,
) {
    var step by remember(game) { mutableStateOf<SaveTransferStep>(SaveTransferStep.Menu) }
    val info by produceState<SaveTransferInfo?>(initialValue = null, key1 = game) {
        value = loadInfo(game)
    }

    val currentInfo = info
    val currentStep = step

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = game.displayName())
        },
        text = {
            when {
                currentInfo == null -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                currentInfo.gameRunning -> {
                    Text(
                        text = stringResource(id = R.string.save_transfer_game_running),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                currentInfo.error != null -> {
                    Text(
                        text = currentInfo.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                currentStep is SaveTransferStep.Menu -> {
                    SaveTransferMenuContent(
                        info = currentInfo,
                        onExportSram = {
                            onRequest(SaveTransferRequest(game, SaveTransferKind.EXPORT_SRAM))
                        },
                        onImportSram = {
                            onRequest(SaveTransferRequest(game, SaveTransferKind.IMPORT_SRAM))
                        },
                        onExportState = {
                            step = SaveTransferStep.PickSlot(SaveTransferKind.EXPORT_STATE)
                        },
                        onImportState = {
                            step = SaveTransferStep.PickSlot(SaveTransferKind.IMPORT_STATE)
                        },
                    )
                }

                currentStep is SaveTransferStep.PickSlot -> {
                    SaveTransferSlotContent(
                        kind = currentStep.kind,
                        slots = currentInfo.slots,
                        onSlotSelected = { slotIndex ->
                            onRequest(SaveTransferRequest(game, currentStep.kind, slotIndex))
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        dismissButton =
            if (currentInfo != null &&
                !currentInfo.gameRunning &&
                currentInfo.error == null &&
                currentStep is SaveTransferStep.PickSlot
            ) {
                {
                    TextButton(onClick = { step = SaveTransferStep.Menu }) {
                        Text(text = stringResource(id = R.string.back))
                    }
                }
            } else {
                null
            },
    )
}

@Composable
private fun SaveTransferMenuContent(
    info: SaveTransferInfo,
    onExportSram: () -> Unit,
    onImportSram: () -> Unit,
    onExportState: () -> Unit,
    onImportState: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DialogActionRow(
            icon = Icons.Default.Upload,
            title = stringResource(id = R.string.save_transfer_export_sram),
            subtitle = stringResource(id = R.string.save_transfer_sram_subtitle),
            enabled = info.sramExists,
            onClick = onExportSram,
        )
        DialogActionRow(
            icon = Icons.Default.Download,
            title = stringResource(id = R.string.save_transfer_import_sram),
            subtitle = stringResource(id = R.string.save_transfer_sram_subtitle),
            enabled = true,
            onClick = onImportSram,
        )
        if (info.statesSupported) {
            DialogActionRow(
                icon = Icons.Default.SaveAlt,
                title = stringResource(id = R.string.save_transfer_export_state),
                subtitle = stringResource(id = R.string.save_transfer_state_subtitle),
                enabled = info.slots.any { it.exists },
                onClick = onExportState,
            )
            DialogActionRow(
                icon = Icons.Default.FileOpen,
                title = stringResource(id = R.string.save_transfer_import_state),
                subtitle = stringResource(id = R.string.save_transfer_state_subtitle),
                enabled = true,
                onClick = onImportState,
            )
        }
    }
}

@Composable
private fun SaveTransferSlotContent(
    kind: SaveTransferKind,
    slots: List<SaveTransferSlot>,
    onSlotSelected: (Int) -> Unit,
) {
    val headerRes =
        if (kind == SaveTransferKind.EXPORT_STATE) {
            R.string.save_transfer_choose_slot_export
        } else {
            R.string.save_transfer_choose_slot_import
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(id = headerRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        slots.forEach { slot ->
            val enabled =
                if (kind == SaveTransferKind.EXPORT_STATE) {
                    slot.exists
                } else {
                    true
                }
            DialogSlotRow(
                slot = slot,
                enabled = enabled,
                onClick = { onSlotSelected(slot.index) },
            )
        }
    }
}

@Composable
private fun DialogActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.padding(end = 16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DialogSlotRow(
    slot: SaveTransferSlot,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val subtitleText =
        slot.description.ifBlank {
            stringResource(id = R.string.save_transfer_slot_empty)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(end = 12.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (slot.preview != null) {
                Image(
                    bitmap = slot.preview.asImageBitmap(),
                    contentDescription = slot.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
