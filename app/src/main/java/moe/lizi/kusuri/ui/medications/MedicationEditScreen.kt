package moe.lizi.kusuri.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.form.FormError
import moe.lizi.kusuri.domain.form.FormField
import moe.lizi.kusuri.domain.form.MedicationFormErrors
import moe.lizi.kusuri.domain.form.MedicationFormState
import moe.lizi.kusuri.domain.form.ScheduleMode
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.ui.AppViewModelProvider

private val MEAL_TAG_OPTIONS = listOf(
    MealTag.NONE to R.string.form_meal_none,
    MealTag.BEFORE to R.string.meal_before,
    MealTag.AFTER to R.string.meal_after,
    MealTag.WITH to R.string.meal_with,
)

@Composable
fun MedicationEditScreen(
    onSaved: () -> Unit,
    viewModel: MedicationEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    val defaultUnit = stringResource(R.string.form_unit_default)
    LaunchedEffect(viewModel.isNew) {
        if (viewModel.isNew) {
            viewModel.update { state -> if (state.unit.isBlank()) state.copy(unit = defaultUnit) else state }
        }
    }

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    val state = form
    if (state == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var timePickerIndex by remember { mutableStateOf<Int?>(null) }
    var showAnchorTimePicker by remember { mutableStateOf(false) }
    var showAnchorDatePicker by remember { mutableStateOf(false) }
    var showCourseStartPicker by remember { mutableStateOf(false) }
    var showCourseEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(stringResource(R.string.form_section_basic))
        OutlinedTextField(
            value = state.name,
            onValueChange = { value -> viewModel.update { it.copy(name = value) } },
            label = { Text(stringResource(R.string.form_name)) },
            isError = errors?.get(FormField.NAME) != null,
            supportingText = supportingError(errors, FormField.NAME),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.unit,
                onValueChange = { value -> viewModel.update { it.copy(unit = value) } },
                label = { Text(stringResource(R.string.form_unit)) },
                placeholder = { Text(stringResource(R.string.form_unit_hint)) },
                isError = errors?.get(FormField.UNIT) != null,
                supportingText = supportingError(errors, FormField.UNIT),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.doseText,
                onValueChange = { value -> viewModel.update { it.copy(doseText = value) } },
                label = { Text(stringResource(R.string.form_dose)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errors?.get(FormField.DOSE) != null,
                supportingText = supportingError(errors, FormField.DOSE),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.form_meal_tag),
            style = MaterialTheme.typography.labelLarge,
        )
        MealTagChips(
            selected = state.mealTag,
            onSelect = { tag -> viewModel.update { it.copy(mealTag = tag) } },
        )

        SectionTitle(stringResource(R.string.form_section_schedule))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ScheduleMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { viewModel.update { it.copy(mode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ScheduleMode.entries.size),
                    label = { Text(scheduleModeLabel(mode)) },
                )
            }
        }

        when (state.mode) {
            ScheduleMode.DAILY_TIMES -> {
                DailyTimesEditor(
                    times = state.dailyTimes,
                    onEdit = { index -> timePickerIndex = index },
                    onRemove = { time -> viewModel.update { it.copy(dailyTimes = it.dailyTimes - time) } },
                    onAdd = { timePickerIndex = state.dailyTimes.size },
                )
                errors?.get(FormField.TIMES)?.let { error ->
                    Text(
                        text = stringResource(error.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            ScheduleMode.INTERVAL -> IntervalEditor(
                state = state,
                everyError = errors?.get(FormField.INTERVAL_EVERY),
                onEveryChange = { value -> viewModel.update { it.copy(intervalEveryText = value) } },
                onUnitChange = { unit -> viewModel.update { it.copy(intervalUnit = unit) } },
                onPickAnchorTime = { showAnchorTimePicker = true },
                onPickAnchorDate = { showAnchorDatePicker = true },
            )

            ScheduleMode.PRN -> PrnEditor(
                state = state,
                minError = errors?.get(FormField.PRN_MIN_INTERVAL),
                maxError = errors?.get(FormField.PRN_MAX_PER_DAY),
                onMinChange = { value -> viewModel.update { it.copy(prnMinIntervalText = value) } },
                onMaxChange = { value -> viewModel.update { it.copy(prnMaxPerDayText = value) } },
            )
        }

        SectionTitle(stringResource(R.string.form_section_course))
        SettingRow(
            label = stringResource(R.string.form_course_start),
            value = formatDate(state.courseStart),
            onClick = { showCourseStartPicker = true },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.form_course_has_end),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.hasCourseEnd,
                onCheckedChange = { checked -> viewModel.update { it.copy(hasCourseEnd = checked) } },
            )
        }
        if (state.hasCourseEnd) {
            SettingRow(
                label = stringResource(R.string.form_course_end),
                value = state.courseEnd?.let(::formatDate).orEmpty(),
                onClick = { showCourseEndPicker = true },
            )
            errors?.get(FormField.COURSE_END)?.let { error ->
                Text(
                    text = stringResource(error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionTitle(stringResource(R.string.form_section_stock))
        if (viewModel.isNew) {
            OutlinedTextField(
                value = state.initialStockText,
                onValueChange = { value -> viewModel.update { it.copy(initialStockText = value) } },
                label = { Text(stringResource(R.string.form_initial_stock)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errors?.get(FormField.INITIAL_STOCK) != null,
                supportingText = supportingError(errors, FormField.INITIAL_STOCK),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.lowStockThresholdText,
            onValueChange = { value -> viewModel.update { it.copy(lowStockThresholdText = value) } },
            label = { Text(stringResource(R.string.form_threshold)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = errors?.get(FormField.THRESHOLD) != null,
            supportingText = supportingError(errors, FormField.THRESHOLD),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.form_section_notes))
        OutlinedTextField(
            value = state.notes,
            onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
            placeholder = { Text(stringResource(R.string.form_notes_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
        Spacer(Modifier.height(24.dp))
    }

    timePickerIndex?.let { index ->
        val initial = state.dailyTimes.getOrNull(index) ?: LocalTime.of(12, 0)
        KusuriTimePickerDialog(
            title = stringResource(R.string.form_times),
            initial = initial,
            onDismiss = { timePickerIndex = null },
            onConfirm = { picked ->
                viewModel.update { s ->
                    val updated = if (index < s.dailyTimes.size) {
                        s.dailyTimes.toMutableList().also { it[index] = picked }
                    } else {
                        s.dailyTimes + picked
                    }
                    s.copy(dailyTimes = updated.sorted())
                }
                timePickerIndex = null
            },
        )
    }

    if (showAnchorTimePicker) {
        KusuriTimePickerDialog(
            title = stringResource(R.string.form_interval_anchor_time),
            initial = state.intervalAnchorTime,
            onDismiss = { showAnchorTimePicker = false },
            onConfirm = { picked ->
                viewModel.update { it.copy(intervalAnchorTime = picked) }
                showAnchorTimePicker = false
            },
        )
    }

    if (showAnchorDatePicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.form_interval_anchor_date),
            initial = state.intervalAnchorDate,
            onDismiss = { showAnchorDatePicker = false },
            onConfirm = { picked ->
                viewModel.update { it.copy(intervalAnchorDate = picked) }
                showAnchorDatePicker = false
            },
        )
    }

    if (showCourseStartPicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.form_course_start),
            initial = state.courseStart,
            onDismiss = { showCourseStartPicker = false },
            onConfirm = { picked ->
                viewModel.update { it.copy(courseStart = picked) }
                showCourseStartPicker = false
            },
        )
    }

    if (showCourseEndPicker) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.form_course_end),
            initial = state.courseEnd ?: state.courseStart,
            onDismiss = { showCourseEndPicker = false },
            onConfirm = { picked ->
                viewModel.update { it.copy(courseEnd = picked) }
                showCourseEndPicker = false
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MealTagChips(selected: MealTag, onSelect: (MealTag) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MEAL_TAG_OPTIONS.forEach { (tag, labelRes) ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(tag) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun DailyTimesEditor(
    times: List<LocalTime>,
    onEdit: (Int) -> Unit,
    onRemove: (LocalTime) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        times.forEachIndexed { index, time ->
            Surface(
                onClick = { onEdit(index) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(time),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(time) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_remove_time),
                        )
                    }
                }
            }
        }
        TextButton(onClick = onAdd) {
            Text(stringResource(R.string.form_add_time))
        }
    }
}

@Composable
private fun IntervalEditor(
    state: MedicationFormState,
    everyError: FormError?,
    onEveryChange: (String) -> Unit,
    onUnitChange: (IntervalUnit) -> Unit,
    onPickAnchorTime: () -> Unit,
    onPickAnchorDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.intervalEveryText,
                onValueChange = onEveryChange,
                label = { Text(stringResource(R.string.form_interval_every)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = everyError != null,
                supportingText = supportingError(everyError),
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
            FilterChip(
                selected = state.intervalUnit == IntervalUnit.HOURS,
                onClick = { onUnitChange(IntervalUnit.HOURS) },
                label = { Text(stringResource(R.string.form_interval_unit_hours)) },
            )
            FilterChip(
                selected = state.intervalUnit == IntervalUnit.DAYS,
                onClick = { onUnitChange(IntervalUnit.DAYS) },
                label = { Text(stringResource(R.string.form_interval_unit_days)) },
            )
        }
        if (state.intervalUnit == IntervalUnit.DAYS) {
            SettingRow(
                label = stringResource(R.string.form_interval_anchor_date),
                value = formatDate(state.intervalAnchorDate),
                onClick = onPickAnchorDate,
            )
        }
        SettingRow(
            label = stringResource(R.string.form_interval_anchor_time),
            value = formatTime(state.intervalAnchorTime),
            onClick = onPickAnchorTime,
        )
    }
}

@Composable
private fun PrnEditor(
    state: MedicationFormState,
    minError: FormError?,
    maxError: FormError?,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.prnMinIntervalText,
                onValueChange = onMinChange,
                label = { Text(stringResource(R.string.form_prn_min_interval)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = minError != null,
                supportingText = supportingError(minError),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.prnMaxPerDayText,
                onValueChange = onMaxChange,
                label = { Text(stringResource(R.string.form_prn_max_per_day)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = maxError != null,
                supportingText = supportingError(maxError),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.form_prn_optional),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun supportingError(
    errors: MedicationFormErrors?,
    field: FormField,
): (@Composable () -> Unit)? = supportingError(errors?.get(field))

@Composable
private fun supportingError(error: FormError?): (@Composable () -> Unit)? {
    error ?: return null
    val message = stringResource(error.messageRes())
    return { Text(message) }
}

private fun FormError.messageRes(): Int = when (this) {
    FormError.REQUIRED -> R.string.form_error_required
    FormError.INVALID_NUMBER -> R.string.form_error_invalid_number
    FormError.MUST_BE_POSITIVE -> R.string.form_error_positive
    FormError.MUST_BE_NON_NEGATIVE -> R.string.form_error_non_negative
    FormError.NEEDS_AT_LEAST_ONE_TIME -> R.string.form_error_no_times
    FormError.END_BEFORE_START -> R.string.form_error_end_before_start
}

@Composable
private fun scheduleModeLabel(mode: ScheduleMode): String = when (mode) {
    ScheduleMode.DAILY_TIMES -> stringResource(R.string.form_mode_daily)
    ScheduleMode.INTERVAL -> stringResource(R.string.form_mode_interval)
    ScheduleMode.PRN -> stringResource(R.string.form_mode_prn)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KusuriTimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KusuriDatePickerDialog(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { DatePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                    }
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
