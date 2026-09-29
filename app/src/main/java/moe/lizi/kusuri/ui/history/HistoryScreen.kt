package moe.lizi.kusuri.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.history.AdherenceSummary
import moe.lizi.kusuri.domain.history.HistoryDay
import moe.lizi.kusuri.domain.history.HistoryDose
import moe.lizi.kusuri.domain.history.HistoryDoseGroup
import moe.lizi.kusuri.domain.history.HistoryFilter
import moe.lizi.kusuri.domain.history.HistoryRange
import moe.lizi.kusuri.domain.history.HistoryStatus
import moe.lizi.kusuri.domain.history.HistoryType
import moe.lizi.kusuri.domain.history.attentionStatusKind
import moe.lizi.kusuri.domain.history.groupDosesByScheduledTime
import moe.lizi.kusuri.domain.history.startDate
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.DoseRecordDialog
import moe.lizi.kusuri.ui.components.DoseStatusKindText
import moe.lizi.kusuri.ui.components.DoseStatusText
import moe.lizi.kusuri.ui.components.KusuriDatePickerDialog
import moe.lizi.kusuri.ui.components.MedicationPickerDialog
import moe.lizi.kusuri.ui.components.MedicationTitle
import moe.lizi.kusuri.ui.components.SettingRow
import moe.lizi.kusuri.ui.components.TimelineCard
import moe.lizi.kusuri.ui.components.TimelineTitle
import moe.lizi.kusuri.ui.components.dayLabel
import moe.lizi.kusuri.ui.components.severityDots

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HistoryDose?>(null) }
    // 折叠组的展开状态放在页面层:列表项滚出屏幕被回收后回来,不会自己折回去。
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }
    var showFilter by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Column(modifier = Modifier.fillMaxSize()) {
        AdherenceCard(
            sevenDays = state.adherence7,
            thirtyDays = state.adherence30,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )

        HistoryFilterRow(
            filter = state.filter,
            onSelectRange = { range ->
                if (range == HistoryRange.CUSTOM) {
                    // 自定义先落到"当前范围",再打开起止编辑;设过就沿用上次的起止。
                    viewModel.updateFilter { current ->
                        current.copy(
                            range = range,
                            customFrom = current.customFrom ?: current.startDate(state.today),
                            customTo = current.customTo ?: state.today,
                        )
                    }
                    showRange = true
                } else {
                    viewModel.updateFilter { it.copy(range = range) }
                }
            },
            onOpenFilter = { showFilter = true },
        )

        if (state.days.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (state.hasAnyContent) {
                            R.string.history_filter_empty
                        } else {
                            R.string.history_empty
                        },
                    ),
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
                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Text(
                            text = dayLabel(day.date, state.today),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(day.rows(), key = { it.key }) { row ->
                        when (row) {
                            is TimelineRow.Dose -> HistoryDoseRow(
                                dose = row.dose,
                                onClick = { editing = row.dose },
                            )

                            is TimelineRow.DoseGroup -> HistoryDoseGroupRow(
                                group = row.group,
                                expanded = expandedGroups[row.group.scheduledAt.epochSecond] == true,
                                onToggle = {
                                    val key = row.group.scheduledAt.epochSecond
                                    expandedGroups[key] = expandedGroups[key] != true
                                },
                                onOpen = { editing = it },
                            )

                            is TimelineRow.Log -> HistoryLogRow(
                                entry = row.entry,
                                medicationName = row.entry.medicationId?.let { state.medicationNames[it] },
                                zone = zone,
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { dose ->
        val record = dose.record
        val isPast = dose.status == DoseStatus.Missed || dose.status == DoseStatus.Untracked
        DoseRecordDialog(
            title = stringResource(
                R.string.record_dialog_title,
                dose.medication.name,
                formatTime(dose.scheduledAt.atZone(zone).toLocalTime()),
            ),
            confirmLabel = when {
                record != null -> stringResource(R.string.action_save)
                isPast -> stringResource(R.string.action_backfill)
                else -> stringResource(R.string.action_save)
            },
            initialActualAt = record?.actualAt ?: dose.scheduledAt,
            initialAction = record?.action ?: DoseAction.TAKEN,
            // 按状态给动作(与今日页一致):未到点/到点未处理 → 已服用 或 跳过;
            // 已超时 → 补记(默认已服用)也能选跳过。有没有记录不再决定动作集合。
            showActionChoice = true,
            onDismiss = { editing = null },
            onConfirm = { actualAt, action ->
                if (record == null) {
                    viewModel.record(dose, actualAt, action)
                } else {
                    viewModel.saveRecord(dose, actualAt, action)
                }
                editing = null
            },
            onDelete = record?.let {
                {
                    viewModel.deleteRecord(dose)
                    editing = null
                }
            },
        )
    }

    if (showFilter) {
        HistoryFilterDialog(
            filter = state.filter,
            medicationNames = state.medicationNames,
            onUpdate = viewModel::updateFilter,
            onReset = viewModel::resetFilter,
            onDismiss = { showFilter = false },
        )
    }

    if (showRange) {
        HistoryRangeDialog(
            filter = state.filter,
            today = state.today,
            onDismiss = { showRange = false },
            onConfirm = { from, to ->
                viewModel.updateFilter {
                    it.copy(
                        range = HistoryRange.CUSTOM,
                        customFrom = minOf(from, to),
                        customTo = maxOf(from, to),
                    )
                }
                showRange = false
            },
        )
    }
}

@Composable
private fun AdherenceCard(
    sevenDays: AdherenceSummary,
    thirtyDays: AdherenceSummary,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.history_adherence_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                AdherenceFigure(stringResource(R.string.history_adherence_7d), sevenDays)
                AdherenceFigure(stringResource(R.string.history_adherence_30d), thirtyDays)
            }
        }
    }
}

@Composable
private fun AdherenceFigure(label: String, summary: AdherenceSummary) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val rate = summary.rate
        Text(
            text = rate?.let { stringResource(R.string.history_adherence_percent, (it * 100).roundToInt()) }
                ?: stringResource(R.string.history_adherence_none),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** 历史页顶部的筛选行:时间范围直接选,其余维度收进弹窗。 */
@Composable
private fun HistoryFilterRow(
    filter: HistoryFilter,
    onSelectRange: (HistoryRange) -> Unit,
    onOpenFilter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryRange.entries.forEach { range ->
                FilterChip(
                    selected = filter.range == range,
                    onClick = { onSelectRange(range) },
                    label = { Text(historyRangeLabel(range)) },
                )
            }
        }
        TextButton(onClick = onOpenFilter) {
            Text(
                text = stringResource(R.string.history_filter),
                color = if (filter.narrowing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 类型 / 状态 / 药物:收进一个弹窗,免得筛选行本身占满一屏。 */
@Composable
private fun HistoryFilterDialog(
    filter: HistoryFilter,
    medicationNames: Map<Long, String>,
    onUpdate: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingMedication by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_filter)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterSection(title = stringResource(R.string.history_filter_type)) {
                    HistoryType.entries.forEach { type ->
                        FilterChip(
                            selected = type in filter.types,
                            onClick = { onUpdate { it.copy(types = it.types.toggled(type)) } },
                            label = { Text(historyTypeLabel(type)) },
                        )
                    }
                }
                // 状态只作用于服药行:没勾服药就整段不出现,免得对着挑不到的东西选状态。
                if (HistoryType.DOSE in filter.types) {
                    FilterSection(title = stringResource(R.string.history_filter_status)) {
                        HistoryStatus.entries.forEach { status ->
                            FilterChip(
                                selected = status in filter.statuses,
                                onClick = { onUpdate { it.copy(statuses = it.statuses.toggled(status)) } },
                                label = { Text(historyStatusLabel(status)) },
                            )
                        }
                    }
                }
                SettingRow(
                    label = stringResource(R.string.history_filter_medication),
                    value = filter.medicationId?.let { medicationNames[it] }
                        ?: stringResource(R.string.history_medication_all),
                    onClick = { pickingMedication = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onReset, enabled = !filter.isDefault) {
                Text(stringResource(R.string.history_filter_reset))
            }
        },
    )

    if (pickingMedication) {
        MedicationPickerDialog(
            title = stringResource(R.string.history_filter_medication),
            options = medicationNames.entries.sortedBy { it.value }.map { (id, name) -> id to name },
            selectedId = filter.medicationId,
            noneLabel = stringResource(R.string.history_medication_all),
            onSelect = { id ->
                onUpdate { it.copy(medicationId = id) }
                pickingMedication = false
            },
            onDismiss = { pickingMedication = false },
        )
    }
}

@Composable
private fun HistoryRangeDialog(
    filter: HistoryFilter,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    var from by remember { mutableStateOf(filter.customFrom ?: filter.startDate(today)) }
    var to by remember { mutableStateOf(filter.customTo ?: today) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_range_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingRow(
                    label = stringResource(R.string.history_range_start),
                    value = formatDate(from),
                    onClick = { pickingStart = true },
                )
                SettingRow(
                    label = stringResource(R.string.history_range_end),
                    value = formatDate(to),
                    onClick = { pickingEnd = true },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(from, to) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (pickingStart) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.history_range_start),
            initial = from,
            onDismiss = { pickingStart = false },
            onConfirm = { picked ->
                from = picked
                pickingStart = false
            },
        )
    }

    if (pickingEnd) {
        KusuriDatePickerDialog(
            title = stringResource(R.string.history_range_end),
            initial = to,
            onDismiss = { pickingEnd = false },
            onConfirm = { picked ->
                to = picked
                pickingEnd = false
            },
        )
    }
}

@Composable
private fun FilterSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun historyRangeLabel(range: HistoryRange): String = when (range) {
    HistoryRange.TODAY -> stringResource(R.string.history_today)
    HistoryRange.LAST_7_DAYS -> stringResource(R.string.history_range_7d)
    HistoryRange.LAST_30_DAYS -> stringResource(R.string.history_range_30d)
    HistoryRange.CUSTOM -> stringResource(R.string.history_range_custom)
}

@Composable
private fun historyTypeLabel(type: HistoryType): String = when (type) {
    HistoryType.DOSE -> stringResource(R.string.history_type_dose)
    HistoryType.SYMPTOM -> stringResource(R.string.log_type_symptom)
    HistoryType.NOTE -> stringResource(R.string.log_type_note)
}

@Composable
private fun historyStatusLabel(status: HistoryStatus): String = when (status) {
    HistoryStatus.TAKEN -> stringResource(R.string.status_taken)
    HistoryStatus.SKIPPED -> stringResource(R.string.status_skipped)
    HistoryStatus.MISSED -> stringResource(R.string.status_missed)
    HistoryStatus.UNHANDLED -> stringResource(R.string.history_status_unhandled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDoseRow(dose: HistoryDose, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    TimelineCard(
        time = formatTime(dose.scheduledAt.atZone(zone).toLocalTime()),
        onClick = onClick,
    ) {
        TimelineTitle(
            title = { modifier ->
                MedicationTitle(
                    name = dose.medication.name,
                    doseLabel = stringResource(
                        R.string.list_dose,
                        formatAmount(dose.medication.defaultDose),
                        dose.medication.unit,
                    ),
                    modifier = modifier,
                )
            },
            trailing = { DoseStatusText(dose.status) },
        )
    }
}

/**
 * 同一计划时刻的多味药:默认折起,点一下展开成一行一味。
 *
 * 折叠行右侧只显示一个状态词——组内"最需要注意"的那个状态(已错过 / 到时间了 / 待服用…),
 * 这样漏服不会被一个数量词藏起来;各自的真实状态展开后逐条可见。
 */
@Composable
private fun HistoryDoseGroupRow(
    group: HistoryDoseGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (HistoryDose) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val names = group.doses.joinToString(separator = "、") { it.medication.name }
    val folded = group.attentionStatusKind()
    // 展开/折叠用图标,不占文案。
    val toggleIcon = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    val toggleDescription = stringResource(
        if (expanded) R.string.cd_history_group_collapse else R.string.cd_history_group_expand,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimelineCard(
            time = formatTime(group.scheduledAt.atZone(zone).toLocalTime()),
            onClick = onToggle,
        ) {
            TimelineTitle(
                title = { modifier ->
                    Text(
                        text = names,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = modifier,
                    )
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DoseStatusKindText(folded)
                        Icon(
                            imageVector = toggleIcon,
                            contentDescription = toggleDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        }
        if (expanded) {
            group.doses.forEach { dose ->
                HistoryDoseRow(dose = dose, onClick = { onOpen(dose) })
            }
        }
    }
}

@Composable
private fun HistoryLogRow(entry: LogEntry, medicationName: String?, zone: ZoneId) {
    TimelineCard(time = formatTime(entry.at.atZone(zone).toLocalTime())) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        TimelineTitle(
            title = { modifier ->
                Text(
                    text = if (entry.type == LogEntryType.SYMPTOM) {
                        entry.symptom.orEmpty()
                    } else {
                        stringResource(R.string.log_type_note)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = modifier,
                )
            },
            trailing = entry.severity?.let { severity ->
                {
                    Text(
                        text = severityDots(severity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        entry.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        medicationName?.let { name ->
            Text(
                text = stringResource(R.string.log_linked_to, name),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

private sealed interface TimelineRow {
    val at: Instant
    val key: String

    data class Dose(val dose: HistoryDose) : TimelineRow {
        override val at: Instant get() = dose.scheduledAt
        override val key: String get() = "dose:${dose.medication.id}:${dose.scheduledAt.epochSecond}"
    }

    /** 同一计划时刻的多味药(≥2),折叠成一行。 */
    data class DoseGroup(val group: HistoryDoseGroup) : TimelineRow {
        override val at: Instant get() = group.scheduledAt
        override val key: String get() = "dose-group:${group.scheduledAt.epochSecond}"
    }

    data class Log(val entry: LogEntry) : TimelineRow {
        override val at: Instant get() = entry.at
        override val key: String get() = "log:${entry.id}"
    }
}

private fun HistoryDay.rows(): List<TimelineRow> {
    val doseRows = groupDosesByScheduledTime(doses).map { group ->
        if (group.collapsing) TimelineRow.DoseGroup(group) else TimelineRow.Dose(group.doses.single())
    }
    return (doseRows + logs.map { TimelineRow.Log(it) }).sortedByDescending { it.at }
}
