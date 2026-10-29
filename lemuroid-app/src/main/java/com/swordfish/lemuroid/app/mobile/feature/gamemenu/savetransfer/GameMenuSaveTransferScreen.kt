package com.swordfish.lemuroid.app.mobile.feature.gamemenu.savetransfer

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.GameMenuRoute
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.navigateToRoute
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink

@Composable
fun GameMenuSaveTransferScreen(
    navController: NavController,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    onResult: (Intent.() -> Unit) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.game_menu_export_sram)) },
            subtitle = { Text(text = stringResource(id = R.string.game_menu_save_transfer_sram_subtitle)) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = stringResource(id = R.string.game_menu_export_sram),
                )
            },
            onClick = {
                onResult { putExtra(GameMenuContract.RESULT_EXPORT_SRAM, true) }
            },
        )

        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.game_menu_import_sram)) },
            subtitle = { Text(text = stringResource(id = R.string.game_menu_save_transfer_sram_subtitle)) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(id = R.string.game_menu_import_sram),
                )
            },
            onClick = {
                onResult { putExtra(GameMenuContract.RESULT_IMPORT_SRAM, true) }
            },
        )

        if (gameMenuRequest.coreConfig.statesSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.game_menu_export_state)) },
                subtitle = { Text(text = stringResource(id = R.string.game_menu_save_transfer_state_subtitle)) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = stringResource(id = R.string.game_menu_export_state),
                    )
                },
                onClick = { navController.navigateToRoute(GameMenuRoute.EXPORT_STATE) },
            )

            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.game_menu_import_state)) },
                subtitle = { Text(text = stringResource(id = R.string.game_menu_save_transfer_state_subtitle)) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = stringResource(id = R.string.game_menu_import_state),
                    )
                },
                onClick = { navController.navigateToRoute(GameMenuRoute.IMPORT_STATE) },
            )
        }
    }
}
