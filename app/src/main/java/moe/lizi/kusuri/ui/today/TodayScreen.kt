package moe.lizi.kusuri.ui.today

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ExactAlarmPermissions
import moe.lizi.kusuri.alarm.ReliabilityChecks
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.DoseRecordDialog
import moe.lizi.kusuri.ui.components.DoseStatusText
import moe.lizi.kusuri.ui.medications.formatMinuteSpan

@Composable
fun TodayScreen(
    onOpenMedication: (Long) -> Unit,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prnTarget by viewModel.prnTarget.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationsEnabled by remember { mutableStateOf(ReliabilityChecks.notificationsEnabled(context)) }
    var exactAlarmsAllowed by remember {
        mutableStateOf(ExactAlarmPermissions.canScheduleExactAlarms(context))
    }
    var backfillTarget by remember { mutableStateOf<TodayDoseItem?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = ReliabilityChecks.notificationsEnabled(context)
                exactAlarmsAllowed = ExactAlarmPermissions.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsEnabled = granted }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!notificationsEnabled) {
            PermissionBanner(
                text = stringResource(R.string.today_notifications_denied),
                actionLabel = stringResource(R.string.action_grant),
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                },
            )
        }
        if (!exactAlarmsAllowed) {
            PermissionBanner(
                text = stringResource(R.string.today_exact_alarm_denied),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = {
                    runCatching { context.startActivity(ExactAlarmPermissions.settingsIntent(context)) }
                        .onFailure { context.startActivity(ExactAlarmPermissions.appDetailsIntent(context)) }
                },
            )
        }
        if (state.lowStockMedications.isNotEmpty()) {
            LowStockBanner(medications = state.lowStockMedications)
        }

        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.today_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.prnInfos.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.prn_section_title)) }
                    items(state.prnInfos, key = { "prn-${it.medication.id}" }) { info ->
                        PrnRow(
                            info = info,
                            now = state.now,
                            onRecord = { viewModel.startPrnRecord(info.medication) },
                        )
                    }
                }
                if (state.doses.isNotEmpty()) {
                    if (state.prnInfos.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.today_section_title)) }
                    }
                    items(state.doses, key = { "${it.medication.id}:${it.scheduledAt.epochSecond}" }) { item ->
                        DoseRow(
                            item = item,
                            onClick = { onOpenMedication(item.medication.id) },
                            onTaken = { viewModel.markTaken(item) },
                            onSkip = { viewModel.markSkipped(item) },
                            onBackfill = { backfillTarget = item },
                        )
                    }
                }
            }
        }
    }

    backfillTarget?.let { item ->
        DoseRecordDialog(
            title = stringResource(
                R.string.record_dialog_title,
                item.medication.name,
                formatTime(item.scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime()),
            ),
            confirmLabel = stringResource(R.string.action_backfill),
            initialActualAt = item.scheduledAt,
            initialAction = DoseAction.TAKEN,
            showActionChoice = false,
            onDismiss = { backfillTarget = null },
            onConfirm = { actualAt, _ ->
                viewModel.backfill(item, actualAt)
                backfillTarget = null
            },
        )
    }

    prnTarget?.let { target ->
        PrnRecordDialog(
            state = target,
            onDismiss = { viewModel.cancelPrnRecord() },
            onConfirm = { amount -> viewModel.confirmPrnRecord(amount) },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LowStockBanner(medications: List<Medication>) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (medications.size == 1) {
                stringResource(R.string.stock_banner_single, medications.single().name)
            } else {
                stringResource(R.string.stock_banner_multiple, medications.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PrnRow(info: PrnInfo, now: Instant, onRecord: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(info.medication.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        R.string.detail_dose,
                        formatAmount(info.medication.defaultDose),
                        info.medication.unit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = prnStatusText(info, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRecord) {
                Text(stringResource(R.string.prn_record_action))
            }
        }
    }
}

@Composable
private fun prnStatusText(info: PrnInfo, now: Instant): String {
    val lastTakenAt = info.lastTakenAt
    return when {
        lastTakenAt != null -> stringResource(
            R.string.prn_row_last_today,
            formatMinuteSpan(Duration.between(lastTakenAt, now).toMinutes().coerceAtLeast(0L).toInt()),
            info.takenTodayCount,
        )

        info.takenTodayCount > 0 -> stringResource(R.string.prn_row_today, info.takenTodayCount)
        else -> stringResource(R.string.prn_row_none)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseRow(
    item: TodayDoseItem,
    onClick: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onBackfill: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val time = formatTime(item.scheduledAt.atZone(zone).toLocalTime())

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(56.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.medication.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(
                            R.string.detail_dose,
                            formatAmount(item.medication.defaultDose),
                            item.medication.unit,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (val status = item.status) {
                DoseStatus.Pending -> Row {
                    TextButton(onClick = onTaken) { Text(stringResource(R.string.action_taken)) }
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
                }

                DoseStatus.Overdue -> Row(verticalAlignment = Alignment.CenterVertically) {
                    DoseStatusText(status, modifier = Modifier.weight(1f))
                    TextButton(onClick = onTaken) { Text(stringResource(R.string.action_taken)) }
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.action_skip)) }
                }

                is DoseStatus.Taken, DoseStatus.Skipped -> DoseStatusText(status)

                DoseStatus.Missed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    DoseStatusText(status, modifier = Modifier.weight(1f))
                    TextButton(onClick = onBackfill) { Text(stringResource(R.string.action_backfill)) }
                }

                DoseStatus.Untracked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    DoseStatusText(status, modifier = Modifier.weight(1f))
                    TextButton(onClick = onBackfill) { Text(stringResource(R.string.action_backfill)) }
                }
            }
        }
    }
}

@Composable
private fun PrnRecordDialog(
    state: PrnRecordState,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(formatAmount(state.medication.defaultDose)) }
    val amount = amountText.trim().toDoubleOrNull()
    val valid = amount != null && amount > 0
    val minutesSinceLastDose = state.safety.minutesSinceLastDose
    val minIntervalMinutes = state.safety.minIntervalMinutes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prn_record_title, state.medication.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (minutesSinceLastDose != null && minIntervalMinutes != null) {
                    Text(
                        text = stringResource(
                            R.string.prn_dialog_last,
                            formatMinuteSpan(minutesSinceLastDose.toInt()),
                            formatMinuteSpan(minIntervalMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.safety.violatesMinInterval) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.prn_dialog_today_count, state.safety.takenTodayCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.safety.maxPerDay?.let { maxPerDay ->
                    if (state.safety.violatesMaxPerDay) {
                        Text(
                            text = stringResource(R.string.prn_safety_max, maxPerDay),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(QUICK_AMOUNT_ONE, QUICK_AMOUNT_TWO).forEach { quick ->
                        FilterChip(
                            selected = amountText == quick,
                            onClick = { amountText = quick },
                            label = { Text(quick) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.prn_record_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountText.isNotBlank() && !valid,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.prn_record_action))
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
private fun PermissionBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private const val QUICK_AMOUNT_ONE = "1"
private const val QUICK_AMOUNT_TWO = "2"

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
