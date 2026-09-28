package moe.lizi.kusuri.ui.log

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.log.LogEntryErrors
import moe.lizi.kusuri.domain.log.LogEntryFormState
import moe.lizi.kusuri.domain.log.LogFormError
import moe.lizi.kusuri.domain.log.LogFormField
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.KusuriDatePickerDialog
import moe.lizi.kusuri.ui.components.KusuriTimePickerDialog
import moe.lizi.kusuri.ui.components.SettingRow
import moe.lizi.kusuri.ui.components.dayLabel

@Composable
fun LogScreen(
    viewModel: LogViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val medicationNames = remember(state.medications) {
        state.medications.associate { it.id to it.name }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.log_empty),
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
                state.days.forEach { day ->
                    item(key = "log-day-${day.date}") {
                        Text(
                            text = dayLabel(day.date, state.today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(day.entries, key = { "log-${it.id}" }) { entry ->
                        LogEntryCard(
                            entry = entry,
                            medicationName = entry.medicationId?.let { medicationNames[it] },
                            onClick = { viewModel.startEdit(entry) },
                            zone = zone,
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = viewModel::startCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.log_add))
        }
    }

    editor?.let { editorState ->
        LogEditorDialog(
            state = editorState,
            medications = state.medications,
            recentSymptoms = state.recentSymptoms,
            onDismiss = viewModel::dismissEditor,
            onUpdate = viewModel::updateForm,
            onSave = viewModel::saveEditor,
            onDelete = if (editorState.entryId != null) viewModel::deleteEditing else null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEntryCard(
    entry: LogEntry,
    medicationName: String?,
    onClick: () -> Unit,
    zone: ZoneId,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatTime(entry.at.atZone(zone).toLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when (entry.type) {
                    LogEntryType.SYMPTOM -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.symptom.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        entry.severity?.let { severity ->
                            Text(
                                text = severityDots(severity),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    LogEntryType.NOTE -> Text(
                        text = stringResource(R.string.log_type_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                entry.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                medicationName?.let { name ->
                    Text(
                        text = stringResource(R.string.log_linked_to, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEditorDialog(
    state: LogEditorState,
    medications: List<Medication>,
    recentSymptoms: List<String>,
    onDismiss: () -> Unit,
    onUpdate: ((LogEntryFormState) -> LogEntryFormState) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val form = state.form
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showMedicationPicker by remember { mutableStateOf(false) }
    val presets = stringArrayResource(R.array.symptom_presets).toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.entryId == null) R.string.log_editor_title_new else R.string.log_editor_title_edit,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LogEntryType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = form.type == type,
                            onClick = { onUpdate { it.copy(type = type) } },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LogEntryType.entries.size,
                            ),
                            label = { Text(logTypeLabel(type)) },
                        )
                    }
                }

                if (form.type == LogEntryType.SYMPTOM) {
                    OutlinedTextField(
                        value = form.symptom,
                        onValueChange = { value -> onUpdate { it.copy(symptom = value) } },
                        label = { Text(stringResource(R.string.log_symptom_label)) },
                        placeholder = { Text(stringResource(R.string.log_symptom_hint)) },
                        isError = state.errors?.get(LogFormField.SYMPTOM) != null,
                        supportingText = supportingLogError(state.errors, LogFormField.SYMPTOM),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (recentSymptoms + presets).distinct().take(SUGGESTION_LIMIT).forEach { suggestion ->
                            FilterChip(
                                selected = form.symptom == suggestion,
                                onClick = { onUpdate { it.copy(symptom = suggestion) } },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.log_severity) + " · " + stringResource(R.string.log_severity_hint),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (level in MIN_LEVEL..MAX_LEVEL) {
                            FilterChip(
                                selected = form.severity == level,
                                onClick = { onUpdate { it.copy(severity = level) } },
                                label = { Text(level.toString()) },
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { value -> onUpdate { it.copy(note = value) } },
                        label = { Text(stringResource(R.string.log_note)) },
                        placeholder = { Text(stringResource(R.string.log_note_hint)) },
                        isError = state.errors?.get(LogFormField.NOTE) != null,
                        supportingText = supportingLogError(state.errors, LogFormField.NOTE),
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingRow(
                    label = stringResource(R.string.log_date),
                    value = formatDate(form.at.atZone(zone).toLocalDate()),
                    onClick = { showDatePicker = true },
                )
                SettingRow(
                    label = stringResource(R.string.log_time),
                    value = formatTime(form.at.atZone(zone).toLocalTime()),
                    onClick = { showTimePicker = true },
                )
                SettingRow(
                    label = stringResource(R.string.log_link_medication),
                    value = medications.firstOrNull { it.id == form.medicationId }?.name
                        ?: stringResource(R.string.log_link_none),
                    onClick = { showMedicationPicker = true },
                )

                if (form.type == LogEntryType.SYMPTOM) {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { value -> onUpdate { it.copy(note = value) } },
                        label = { Text(stringResource(R.string.log_note)) },
                        placeholder = { Text(stringResource(R.string.log_note_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.log_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showDatePicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.log_date),
            initial = form.at.atZone(zone).toLocalDate(),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                val time = form.at.atZone(zone).toLocalTime()
                onUpdate { it.copy(at = picked.atTime(time).atZone(zone).toInstant()) }
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        KusuriTimePickerDialog(
            title = stringResource(R.string.log_time),
            initial = form.at.atZone(zone).toLocalTime(),
            onDismiss = { showTimePicker = false },
            onConfirm = { picked: LocalTime ->
                val date = form.at.atZone(zone).toLocalDate()
                onUpdate { it.copy(at = date.atTime(picked).atZone(zone).toInstant()) }
                showTimePicker = false
            },
        )
    }

    if (showMedicationPicker) {
        MedicationPickerDialog(
            medications = medications,
            selectedId = form.medicationId,
            onSelect = { selectedId ->
                onUpdate { it.copy(medicationId = selectedId) }
                showMedicationPicker = false
            },
            onDismiss = { showMedicationPicker = false },
        )
    }
}

@Composable
private fun MedicationPickerDialog(
    medications: List<Medication>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_link_medication)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = { onSelect(null) },
                    enabled = selectedId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.log_link_none))
                }
                medications.forEach { medication ->
                    TextButton(
                        onClick = { onSelect(medication.id) },
                        enabled = selectedId != medication.id,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(medication.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun logTypeLabel(type: LogEntryType): String = when (type) {
    LogEntryType.SYMPTOM -> stringResource(R.string.log_type_symptom)
    LogEntryType.NOTE -> stringResource(R.string.log_type_note)
}

@Composable
private fun supportingLogError(
    errors: LogEntryErrors?,
    field: LogFormField,
): (@Composable () -> Unit)? {
    val error = errors?.get(field) ?: return null
    val message = when (error) {
        LogFormError.REQUIRED -> stringResource(R.string.form_error_required)
        LogFormError.OUT_OF_RANGE -> stringResource(R.string.log_error_out_of_range)
    }
    return { Text(message) }
}

/** 严重程度用点表示:●●●○○(1–5)。 */
private fun severityDots(severity: Int): String {
    val filled = severity.coerceIn(0, MAX_LEVEL)
    val empty = (MAX_LEVEL - filled).coerceAtLeast(0)
    return "●".repeat(filled) + "○".repeat(empty)
}

private const val MIN_LEVEL = 1
private const val MAX_LEVEL = 5
private const val SUGGESTION_LIMIT = 12
