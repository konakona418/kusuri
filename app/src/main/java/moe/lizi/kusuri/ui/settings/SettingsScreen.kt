package moe.lizi.kusuri.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ReliabilityChecks
import moe.lizi.kusuri.data.backup.CsvLabels
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.ReliabilityRow
import moe.lizi.kusuri.ui.components.SettingRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gracePeriodHours by viewModel.gracePeriodHours.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()

    var notificationsEnabled by remember { mutableStateOf(ReliabilityChecks.notificationsEnabled(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(ReliabilityChecks.exactAlarmsAllowed(context)) }
    var batteryIgnored by remember { mutableStateOf(ReliabilityChecks.batteryOptimizationIgnored(context)) }
    var showGraceDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = ReliabilityChecks.notificationsEnabled(context)
                exactAlarmsAllowed = ReliabilityChecks.exactAlarmsAllowed(context)
                batteryIgnored = ReliabilityChecks.batteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsEnabled = granted }

    val csvLabels = CsvLabels(
        header = listOf(
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
    )

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let { viewModel.exportCsv(it, csvLabels) } }

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
            ready = notificationsEnabled,
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
            ready = exactAlarmsAllowed,
            onFix = { ReliabilityChecks.openExactAlarmSettings(context) },
        )
        ReliabilityRow(
            label = stringResource(R.string.reliability_battery),
            ready = batteryIgnored,
            onFix = { ReliabilityChecks.openBatteryOptimizationSettings(context) },
        )

        SectionTitle(stringResource(R.string.settings_section_general))
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
            onClick = { exportCsvLauncher.launch("kusuri-records.csv") },
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
    BackupStatus.Failed -> stringResource(R.string.backup_failed_generic)
}
