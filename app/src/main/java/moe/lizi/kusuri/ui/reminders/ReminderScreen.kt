package moe.lizi.kusuri.ui.reminders

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.model.ReminderRepeatKind
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.CardAction
import moe.lizi.kusuri.ui.components.KusuriDatePickerDialog
import moe.lizi.kusuri.ui.components.KusuriTimePickerDialog
import moe.lizi.kusuri.ui.components.LongPressDeleteLabel
import moe.lizi.kusuri.ui.components.SettingRow
import moe.lizi.kusuri.ui.components.TimelineCard
import moe.lizi.kusuri.ui.components.TimelineTitle
import moe.lizi.kusuri.ui.components.dayLabel
import moe.lizi.kusuri.ui.components.longPressDeleteReminderLabel

/** 通用提醒列表(第 5 个标签):即将到来 / 已过期 / 已完成(docs/plan.md §14)。 */
@Composable
fun ReminderScreen(
    viewModel: ReminderViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    // LazyColumn 的 scope 不是 @Composable,文案得在这里先取好。
    val upcomingTitle = stringResource(R.string.reminder_section_upcoming)
    val overdueTitle = stringResource(R.string.reminder_section_overdue)
    val doneTitle = stringResource(R.string.reminder_section_done)

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reminders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reminderSection(
                    title = upcomingTitle,
                    rows = state.upcoming,
                    today = state.today,
                    onClick = viewModel::startEdit,
                    onAcknowledge = viewModel::acknowledge,
                )
                reminderSection(
                    title = overdueTitle,
                    rows = state.overdue,
                    today = state.today,
                    onClick = viewModel::startEdit,
                    onAcknowledge = viewModel::acknowledge,
                )
                reminderSection(
                    title = doneTitle,
                    rows = state.done,
                    today = state.today,
                    onClick = viewModel::startEdit,
                    onAcknowledge = viewModel::acknowledge,
                )
            }
        }

        FloatingActionButton(
            onClick = viewModel::startCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.title_reminder_new))
        }
    }

    editor?.let { current ->
        ReminderEditorDialog(
            editor = current,
            onUpdate = viewModel::updateEditor,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::saveEditor,
            onDelete = if (current.isNew) null else viewModel::deleteEditing,
        )
    }
}

private fun LazyListScope.reminderSection(
    title: String,
    rows: List<ReminderRow>,
    today: LocalDate,
    onClick: (ReminderRow) -> Unit,
    onAcknowledge: (ReminderRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "reminder-section-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(rows, key = { "reminder-${it.reminder.id}" }) { row ->
        ReminderRowCard(
            row = row,
            today = today,
            onClick = { onClick(row) },
            onAcknowledge = { onAcknowledge(row) },
        )
    }
}

@Composable
private fun ReminderRowCard(
    row: ReminderRow,
    today: LocalDate,
    onClick: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val at = row.at.atZone(zone)
    val repeat = reminderRepeatLabel(row.reminder)
    val whenText = if (repeat == null) {
        dayLabel(at.toLocalDate(), today)
    } else {
        stringResource(R.string.reminder_when, dayLabel(at.toLocalDate(), today), repeat)
    }

    TimelineCard(time = formatTime(at.toLocalTime()), onClick = onClick) {
        TimelineTitle(
            title = { modifier ->
                Text(
                    text = row.reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = modifier,
                )
            },
            trailing = {
                when (row.status) {
                    ReminderStatus.UPCOMING -> Unit

                    ReminderStatus.OVERDUE -> Text(
                        text = stringResource(R.string.reminder_status_overdue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    ReminderStatus.DONE -> Text(
                        text = stringResource(R.string.reminder_status_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        Text(
            text = whenText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.reminder.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.status == ReminderStatus.OVERDUE) {
            CardAction(
                text = stringResource(R.string.action_mark_done),
                onClick = onAcknowledge,
            )
        }
    }
}

@Composable
private fun ReminderEditorDialog(
    editor: ReminderEditor,
    onUpdate: ((ReminderEditor) -> ReminderEditor) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val custom = editor.repeatKind.isCustom

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.isNew) R.string.title_reminder_new else R.string.title_reminder_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = { value -> onUpdate { it.copy(title = value) } },
                    label = { Text(stringResource(R.string.reminder_field_title)) },
                    placeholder = { Text(stringResource(R.string.reminder_title_hint)) },
                    isError = editor.title.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow(
                    label = stringResource(R.string.reminder_field_date),
                    value = formatDate(editor.at.atZone(zone).toLocalDate()),
                    onClick = { showDatePicker = true },
                )
                SettingRow(
                    label = stringResource(R.string.reminder_field_time),
                    value = formatTime(editor.at.atZone(zone).toLocalTime()),
                    onClick = { showTimePicker = true },
                )

                Text(
                    text = stringResource(R.string.reminder_field_repeat),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RepeatChip(
                        selected = editor.repeatKind == ReminderRepeatKind.ONCE,
                        labelRes = R.string.reminder_repeat_once,
                        onClick = { onUpdate { it.copy(repeatKind = ReminderRepeatKind.ONCE) } },
                    )
                    RepeatChip(
                        selected = editor.repeatKind == ReminderRepeatKind.DAILY,
                        labelRes = R.string.reminder_repeat_daily,
                        onClick = { onUpdate { it.copy(repeatKind = ReminderRepeatKind.DAILY) } },
                    )
                    RepeatChip(
                        selected = editor.repeatKind == ReminderRepeatKind.WEEKLY,
                        labelRes = R.string.reminder_repeat_weekly,
                        onClick = { onUpdate { it.copy(repeatKind = ReminderRepeatKind.WEEKLY) } },
                    )
                    RepeatChip(
                        selected = editor.repeatKind == ReminderRepeatKind.MONTHLY,
                        labelRes = R.string.reminder_repeat_monthly,
                        onClick = { onUpdate { it.copy(repeatKind = ReminderRepeatKind.MONTHLY) } },
                    )
                    RepeatChip(
                        selected = custom,
                        labelRes = R.string.reminder_repeat_custom,
                        onClick = {
                            onUpdate {
                                if (it.repeatKind.isCustom) {
                                    it
                                } else {
                                    it.copy(repeatKind = ReminderRepeatKind.EVERY_N_DAYS)
                                }
                            }
                        },
                    )
                }

                if (custom) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RepeatChip(
                            selected = editor.repeatKind == ReminderRepeatKind.EVERY_N_DAYS,
                            labelRes = R.string.reminder_unit_days,
                            onClick = {
                                onUpdate { it.copy(repeatKind = ReminderRepeatKind.EVERY_N_DAYS) }
                            },
                        )
                        RepeatChip(
                            selected = editor.repeatKind == ReminderRepeatKind.EVERY_N_MONTHS,
                            labelRes = R.string.reminder_unit_months,
                            onClick = {
                                onUpdate { it.copy(repeatKind = ReminderRepeatKind.EVERY_N_MONTHS) }
                            },
                        )
                        OutlinedTextField(
                            value = editor.interval.toString(),
                            onValueChange = { value ->
                                val digits = value.filter { it.isDigit() }.take(3).toIntOrNull()
                                onUpdate { it.copy(interval = (digits ?: 1).coerceAtLeast(1)) }
                            },
                            label = { Text(stringResource(R.string.reminder_field_interval)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = editor.note,
                    onValueChange = { value -> onUpdate { it.copy(note = value) } },
                    label = { Text(stringResource(R.string.reminder_field_note)) },
                    placeholder = { Text(stringResource(R.string.reminder_note_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onDelete != null) {
                    LongPressDeleteLabel(
                        text = longPressDeleteReminderLabel(),
                        onLongPress = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = editor.valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.reminder_field_date),
            initial = editor.at.atZone(zone).toLocalDate(),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                val time = editor.at.atZone(zone).toLocalTime()
                onUpdate { it.copy(at = picked.atTime(time).atZone(zone).toInstant()) }
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        KusuriTimePickerDialog(
            title = stringResource(R.string.reminder_field_time),
            initial = editor.at.atZone(zone).toLocalTime(),
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                val date = editor.at.atZone(zone).toLocalDate()
                onUpdate { it.copy(at = date.atTime(picked).atZone(zone).toInstant()) }
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun RepeatChip(selected: Boolean, labelRes: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
    )
}

private val ReminderRepeatKind.isCustom: Boolean
    get() = this == ReminderRepeatKind.EVERY_N_DAYS || this == ReminderRepeatKind.EVERY_N_MONTHS

/** 重复规则 → 副标题里的短语;一次性提醒不显示(它没有"重复"可言)。 */
@Composable
private fun reminderRepeatLabel(reminder: Reminder): String? = when (reminder.repeatKind) {
    ReminderRepeatKind.ONCE -> null
    ReminderRepeatKind.DAILY -> stringResource(R.string.reminder_repeat_daily)
    ReminderRepeatKind.WEEKLY -> stringResource(R.string.reminder_repeat_weekly)
    ReminderRepeatKind.MONTHLY -> stringResource(R.string.reminder_repeat_monthly)
    ReminderRepeatKind.EVERY_N_DAYS ->
        stringResource(R.string.reminder_repeat_every_days, reminder.safeInterval)

    ReminderRepeatKind.EVERY_N_MONTHS ->
        stringResource(R.string.reminder_repeat_every_months, reminder.safeInterval)
}
