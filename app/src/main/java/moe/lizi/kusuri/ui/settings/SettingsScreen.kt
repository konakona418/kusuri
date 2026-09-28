package moe.lizi.kusuri.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ReliabilityChecks
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.data.backup.CsvLabels
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.OnboardingDialog
import moe.lizi.kusuri.ui.components.ReliabilityRow
import moe.lizi.kusuri.ui.components.SettingRow
import moe.lizi.kusuri.ui.components.rememberReliabilityState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val reliability = rememberReliabilityState()
    val gracePeriodHours by viewModel.gracePeriodHours.collectAsStateWithLifecycle()
    val reminderSoundEnabled by viewModel.reminderSoundEnabled.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()

    var showGraceDialog by remember { mutableStateOf(false) }
    var showCsvRangeDialog by remember { mutableStateOf(false) }
    var pendingCsvRange by remember { mutableStateOf<CsvRange?>(null) }
    var showWizard by remember { mutableStateOf(false) }
    var wipeStep by remember { mutableStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 回到前台后 rememberReliabilityState 会重新读取 */ }

    val csvLabels = CsvLabels(
        doseSectionTitle = stringResource(R.string.csv_section_doses),
        doseHeader = listOf(
            stringResource(R.string.csv_header_medication),
            stringResource(R.string.csv_header_dose),
            stringResource(R.string.csv_header_scheduled_at),
            stringResource(R.string.csv_header_actual_at),
            stringResource(R.string.csv_header_status),
            stringResource(R.string.csv_header_source),
        ),
        taken = stringResource(R.string.action_taken),
        skipped = stringResource(R.string.action_skip),
        sourceInApp = stringResource(R.string.csv_source_in_app),
        sourceNotification = stringResource(R.string.csv_source_notification),
        sourceBackfill = stringResource(R.string.csv_source_backfill),
        logSectionTitle = stringResource(R.string.csv_section_logs),
        logHeader = listOf(
            stringResource(R.string.csv_log_header_time),
            stringResource(R.string.csv_log_header_type),
            stringResource(R.string.csv_log_header_symptom),
            stringResource(R.string.csv_log_header_severity),
            stringResource(R.string.csv_log_header_medication),
            stringResource(R.string.csv_log_header_note),
        ),
        logTypeSymptom = stringResource(R.string.log_type_symptom),
        logTypeNote = stringResource(R.string.log_type_note),
        logLinkedNone = stringResource(R.string.log_link_none),
    )

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val range = pendingCsvRange
        pendingCsvRange = null
        if (uri != null && range != null) viewModel.exportCsv(uri, csvLabels, range)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importJson) }

    val statusMessage = backupStatusText(backupStatus)
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            viewModel.consumeBackupStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_section_reliability))
        ReliabilityRow(
            label = stringResource(R.string.reliability_notifications),
            ready = reliability.notificationsEnabled,
            onFix = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    ReliabilityChecks.openAppNotificationSettings(context)
                }
            },
        )
        ReliabilityRow(
            label = stringResource(R.string.reliability_exact_alarm),
            ready = reliability.exactAlarmsAllowed,
            onFix = { ReliabilityChecks.openExactAlarmSettings(context) },
        )
        ReliabilityRow(
            label = stringResource(R.string.reliability_battery),
            ready = reliability.batteryOptimizationIgnored,
            onFix = { ReliabilityChecks.openBatteryOptimizationSettings(context) },
        )
        OutlinedButton(
            onClick = { showWizard = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_rerun_wizard))
        }

        SectionTitle(stringResource(R.string.settings_section_general))
        SwitchRow(
            label = stringResource(R.string.settings_reminder_sound),
            description = stringResource(R.string.settings_reminder_sound_description),
            checked = reminderSoundEnabled,
            onCheckedChange = { viewModel.setReminderSoundEnabled(it) },
        )
        SettingRow(
            label = stringResource(R.string.settings_grace_period),
            value = stringResource(R.string.settings_grace_period_value, gracePeriodHours),
            onClick = { showGraceDialog = true },
        )

        SectionTitle(stringResource(R.string.settings_section_backup))
        Text(
            text = stringResource(R.string.settings_backup_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showCsvRangeDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_export_csv))
        }
        OutlinedButton(
            onClick = { exportJsonLauncher.launch("kusuri-backup.json") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_export_json))
        }
        OutlinedButton(
            onClick = { importJsonLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_import_json))
        }
        Text(
            text = stringResource(R.string.backup_import_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.settings_about_title))
        Text(
            text = stringResource(R.string.settings_about_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        SectionTitle(stringResource(R.string.settings_section_danger))
        Text(
            text = stringResource(R.string.settings_delete_all_data_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { wipeStep = 1 },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_delete_all_data))
        }
    }

    if (showGraceDialog) {
        GracePeriodDialog(
            currentHours = gracePeriodHours,
            onDismiss = { showGraceDialog = false },
            onConfirm = { hours ->
                viewModel.setGracePeriodHours(hours)
                showGraceDialog = false
            },
        )
    }

    if (showCsvRangeDialog) {
        CsvRangeDialog(
            onDismiss = { showCsvRangeDialog = false },
            onConfirm = { range ->
                showCsvRangeDialog = false
                pendingCsvRange = range
                exportCsvLauncher.launch("kusuri-records.csv")
            },
        )
    }

    if (showWizard) {
        OnboardingDialog(
            onDone = { showWizard = false },
            onDismissRequest = { showWizard = false },
        )
    }

    // 删除全部数据:两步确认,第二步写明不可恢复。
    if (wipeStep == 1) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text(stringResource(R.string.wipe_confirm_title)) },
            text = { Text(stringResource(R.string.wipe_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { wipeStep = 2 }) {
                    Text(stringResource(R.string.wipe_confirm_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeStep = 0 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (wipeStep == 2) {
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text(stringResource(R.string.wipe_confirm_final_title)) },
            text = { Text(stringResource(R.string.wipe_confirm_final_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        wipeStep = 0
                        viewModel.deleteAllData()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.wipe_confirm_final_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeStep = 0 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CsvRangeDialog(onDismiss: () -> Unit, onConfirm: (CsvRange) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.csv_range_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvRange.entries.forEach { range ->
                    OutlinedButton(
                        onClick = { onConfirm(range) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(csvRangeLabel(range))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun csvRangeLabel(range: CsvRange): String = when (range) {
    CsvRange.LAST_30_DAYS -> stringResource(R.string.csv_range_30)
    CsvRange.LAST_90_DAYS -> stringResource(R.string.csv_range_90)
    CsvRange.ALL -> stringResource(R.string.csv_range_all)
}

@Composable
private fun GracePeriodDialog(
    currentHours: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentHours.toString()) }
    val hours = text.trim().toIntOrNull()
    val valid = hours != null &&
        hours in SettingsRepository.MIN_GRACE_HOURS..SettingsRepository.MAX_GRACE_HOURS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grace_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.grace_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { hours?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun backupStatusText(status: BackupStatus?): String? = when (status) {
    null -> null
    BackupStatus.CsvExported -> stringResource(R.string.backup_csv_exported)
    BackupStatus.JsonExported -> stringResource(R.string.backup_json_exported)
    is BackupStatus.Imported -> stringResource(R.string.backup_imported, status.medications)
    BackupStatus.Wiped -> stringResource(R.string.wipe_done)
    BackupStatus.Failed -> stringResource(R.string.backup_failed_generic)
}
