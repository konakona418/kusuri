package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 记录编辑/补记对话框:
 * - 没有记录时用于"补记"(只选实际时间,动作固定为已服用);
 * - 已有记录时用于修改(可改实际时间与 已服用/跳过,可删除记录)。
 */
@Composable
fun DoseRecordDialog(
    title: String,
    confirmLabel: String,
    initialActualAt: Instant,
    initialAction: DoseAction,
    showActionChoice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (actualAt: Instant, action: DoseAction) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    var date by remember { mutableStateOf(initialActualAt.atZone(zone).toLocalDate()) }
    var time by remember {
        mutableStateOf(initialActualAt.atZone(zone).toLocalTime().withSecond(0).withNano(0))
    }
    var action by remember { mutableStateOf(initialAction) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showActionChoice) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = action == DoseAction.TAKEN,
                            onClick = { action = DoseAction.TAKEN },
                            label = { Text(stringResource(R.string.action_taken)) },
                        )
                        FilterChip(
                            selected = action == DoseAction.SKIPPED,
                            onClick = { action = DoseAction.SKIPPED },
                            label = { Text(stringResource(R.string.action_skip)) },
                        )
                    }
                }
                SettingRow(
                    label = stringResource(R.string.record_dialog_actual_date),
                    value = formatDate(date),
                    onClick = { showDatePicker = true },
                )
                SettingRow(
                    label = stringResource(R.string.record_dialog_actual_time),
                    value = formatTime(time),
                    onClick = { showTimePicker = true },
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.record_dialog_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date.atTime(time).atZone(zone).toInstant(), action) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )

    if (showDatePicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.record_dialog_actual_date),
            initial = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                date = picked
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        KusuriTimePickerDialog(
            title = stringResource(R.string.record_dialog_actual_time),
            initial = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                time = picked
                showTimePicker = false
            },
        )
    }
}
