package moe.lizi.kusuri.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.KusuriDatePickerDialog
import moe.lizi.kusuri.ui.components.TagChip

@Composable
fun MedicationDetailScreen(
    onEdit: () -> Unit,
    onBack: () -> Unit,
    viewModel: MedicationDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val medication by viewModel.medication.collectAsStateWithLifecycle()
    val closed by viewModel.closed.collectAsStateWithLifecycle()
    var showRefillDialog by rememberSaveable { mutableStateOf(false) }
    var showAdjustDialog by rememberSaveable { mutableStateOf(false) }
    var showExtendPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    val current = medication
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(current.name, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                mealTagLabel(current.mealTag)?.let { TagChip(it) }
                if (current.status == MedicationStatus.COMPLETED) {
                    TagChip(stringResource(R.string.medication_status_completed))
                }
            }
            Text(
                text = stringResource(R.string.detail_dose, formatAmount(current.defaultDose), current.unit),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        DetailSection(title = stringResource(R.string.detail_section_schedule)) {
            Text(scheduleSummary(current.schedule), style = MaterialTheme.typography.bodyMedium)
            val interval = current.schedule as? Schedule.Interval
            if (interval != null) {
                Text(
                    text = stringResource(
                        R.string.detail_interval_anchor,
                        formatDate(interval.anchor.toLocalDate()) + " " + formatTime(interval.anchor.toLocalTime()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val prn = current.schedule as? Schedule.Prn
            if (prn?.minIntervalMinutes != null) {
                Text(
                    text = stringResource(R.string.detail_prn_min_interval, formatMinuteSpan(prn.minIntervalMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (prn?.maxPerDay != null) {
                Text(
                    text = stringResource(R.string.detail_prn_max_per_day, prn.maxPerDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DetailSection(title = stringResource(R.string.detail_section_course)) {
            Text(
                text = current.courseEnd?.let { courseEnd ->
                    stringResource(R.string.course_range, formatDate(current.courseStart), formatDate(courseEnd))
                } ?: stringResource(R.string.course_long_term),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current.status != MedicationStatus.ARCHIVED) {
                OutlinedButton(onClick = { showExtendPicker = true }) {
                    Text(stringResource(R.string.action_extend_course))
                }
            }
        }

        DetailSection(title = stringResource(R.string.detail_section_stock)) {
            Text(
                text = stringResource(R.string.stock_remaining, formatAmount(current.remainingStock), current.unit),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.detail_threshold,
                    formatAmount(current.lowStockThreshold),
                    current.unit,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showRefillDialog = true }) {
                    Text(stringResource(R.string.action_refill))
                }
                OutlinedButton(onClick = { showAdjustDialog = true }) {
                    Text(stringResource(R.string.action_adjust))
                }
            }
        }

        current.notes?.let { notes ->
            DetailSection(title = stringResource(R.string.detail_section_notes)) {
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onEdit) {
                Text(stringResource(R.string.action_edit))
            }
            if (current.status == MedicationStatus.ARCHIVED) {
                OutlinedButton(onClick = viewModel::unarchive) {
                    Text(stringResource(R.string.action_unarchive))
                }
            } else {
                OutlinedButton(onClick = viewModel::archive) {
                    Text(stringResource(R.string.action_archive))
                }
            }
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }

    if (showRefillDialog) {
        StockAmountDialog(
            title = stringResource(R.string.refill_dialog_title),
            label = stringResource(R.string.refill_amount_label),
            confirmLabel = stringResource(R.string.refill_confirm),
            initialText = "",
            onDismiss = { showRefillDialog = false },
            onConfirm = { amount ->
                viewModel.refill(amount)
                showRefillDialog = false
            },
        )
    }

    if (showAdjustDialog) {
        StockAmountDialog(
            title = stringResource(R.string.adjust_dialog_title),
            label = stringResource(R.string.adjust_target_label),
            confirmLabel = stringResource(R.string.adjust_confirm),
            initialText = formatAmount(current.remainingStock),
            onDismiss = { showAdjustDialog = false },
            onConfirm = { target ->
                viewModel.adjustStock(target)
                showAdjustDialog = false
            },
        )
    }

    if (showExtendPicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.action_extend_course),
            initial = current.courseEnd ?: LocalDate.now(),
            onDismiss = { showExtendPicker = false },
            onConfirm = { picked ->
                viewModel.extendCourse(picked)
                showExtendPicker = false
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_dialog_title, current.name)) },
            text = { Text(stringResource(R.string.delete_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun StockAmountDialog(
    title: String,
    label: String,
    confirmLabel: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    val amount = text.trim().toDoubleOrNull()
    val valid = amount != null && amount >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = text.isNotBlank() && !valid,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
