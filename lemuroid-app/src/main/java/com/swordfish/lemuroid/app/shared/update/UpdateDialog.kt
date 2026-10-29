package com.swordfish.lemuroid.app.shared.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.R
import java.util.Locale

@Composable
fun UpdateDialog(
    manager: UpdateManager,
    onDismiss: () -> Unit,
) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    val isDownloading = state is UpdateState.Downloading

    val release =
        when (val s = state) {
            is UpdateState.Available -> s.latest
            is UpdateState.Downloading -> s.latest
            is UpdateState.Downloaded -> s.latest
            is UpdateState.Error -> s.latest
            is UpdateState.UpToDate -> s.latest
            UpdateState.Idle, UpdateState.Checking -> null
        }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                dismissOnBackPress = !isDownloading,
                dismissOnClickOutside = !isDownloading,
            ),
        title = {
            val titleText =
                if (release != null) {
                    stringResource(R.string.update_available_title, release.tagName)
                } else {
                    stringResource(R.string.update_check_title)
                }
            Text(text = titleText)
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (release != null) {
                    Text(
                        text = stringResource(R.string.update_current_latest, manager.currentVersion, release.version),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                when (val s = state) {
                    is UpdateState.Available -> {
                        if (s.latest.body.isNotBlank()) {
                            Text(
                                text = s.latest.body.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (s.latest.apkAsset == null) {
                            Text(
                                text = stringResource(R.string.update_error_no_apk),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        val fraction = s.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        val progressLabel =
                            if (s.totalBytes > 0) {
                                stringResource(
                                    R.string.update_progress,
                                    formatMegabytes(s.downloadedBytes),
                                    formatMegabytes(s.totalBytes),
                                )
                            } else {
                                formatMegabytes(s.downloadedBytes)
                            }
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is UpdateState.Downloaded -> {
                        if (s.latest.body.isNotBlank()) {
                            Text(
                                text = s.latest.body.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.update_ready_to_install),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    is UpdateState.Error -> {
                        if (s.latest != null && s.latest.body.isNotBlank()) {
                            Text(
                                text = s.latest.body.trim(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = stringResource(UpdateManager.errorStringRes(s.code)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    UpdateState.Checking -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(R.string.update_status_checking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is UpdateState.UpToDate -> {
                        Text(
                            text = stringResource(R.string.update_status_up_to_date, s.latest.version),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    UpdateState.Idle -> {
                        Text(
                            text = stringResource(R.string.update_status_idle, manager.currentVersion),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available -> {
                    Button(
                        enabled = s.latest.apkAsset != null && activity != null,
                        onClick = { activity?.let { manager.downloadAndInstall(it) } },
                    ) {
                        Text(text = stringResource(R.string.update_action_update))
                    }
                }

                is UpdateState.Downloading -> {
                    TextButton(onClick = { manager.cancel() }) {
                        Text(text = stringResource(R.string.update_action_cancel))
                    }
                }

                is UpdateState.Downloaded -> {
                    Button(
                        enabled = activity != null,
                        onClick = { activity?.let { manager.installApk(it) } },
                    ) {
                        Text(text = stringResource(R.string.update_action_install))
                    }
                }

                is UpdateState.Error -> {
                    Button(
                        onClick = {
                            if (s.latest?.apkAsset != null && activity != null) {
                                manager.downloadAndInstall(activity)
                            } else {
                                manager.checkForUpdates(silent = false)
                            }
                        },
                    ) {
                        Text(text = stringResource(R.string.update_action_update))
                    }
                }

                UpdateState.Checking -> {
                    TextButton(
                        onClick = {
                            manager.cancel()
                            onDismiss()
                        },
                    ) {
                        Text(text = stringResource(R.string.update_action_cancel))
                    }
                }

                is UpdateState.UpToDate, UpdateState.Idle -> {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.ok))
                    }
                }
            }
        },
        dismissButton =
            when (state) {
                is UpdateState.Available,
                is UpdateState.Downloaded,
                is UpdateState.Error,
                -> {
                    {
                        TextButton(onClick = onDismiss) {
                            Text(text = stringResource(R.string.update_action_later))
                        }
                    }
                }

                is UpdateState.Downloading,
                UpdateState.Checking,
                is UpdateState.UpToDate,
                UpdateState.Idle,
                -> null
            },
    )
}

private fun formatMegabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
