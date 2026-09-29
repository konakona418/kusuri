package moe.lizi.kusuri.ui.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import java.time.ZoneId
import kotlin.math.roundToInt
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.history.AdherenceSummary
import moe.lizi.kusuri.domain.history.HistoryDay
import moe.lizi.kusuri.domain.history.HistoryDose
import moe.lizi.kusuri.domain.history.HistoryDoseGroup
import moe.lizi.kusuri.domain.history.groupDosesByScheduledTime
import moe.lizi.kusuri.domain.history.sharedStatusKind
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.DoseRecordDialog
import moe.lizi.kusuri.ui.components.DoseStatusKindText
import moe.lizi.kusuri.ui.components.DoseStatusText
import moe.lizi.kusuri.ui.components.MedicationTitle
import moe.lizi.kusuri.ui.components.TimelineCard
import moe.lizi.kusuri.ui.components.TimelineTitle
import moe.lizi.kusuri.ui.components.dayLabel
import moe.lizi.kusuri.ui.components.severityDots

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HistoryDose?>(null) }
    // 折叠组的展开状态放在页面层:列表项滚出屏幕被回收后回来,不会自己折回去。
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }
    val zone = ZoneId.systemDefault()

    Column(modifier = Modifier.fillMaxSize()) {
        AdherenceCard(
            sevenDays = timeline.adherence7,
            thirtyDays = timeline.adherence30,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )

        if (timeline.days.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
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
                timeline.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Text(
                            text = dayLabel(day.date, timeline.today),
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
                                medicationName = row.entry.medicationId?.let { timeline.medicationNames[it] },
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
 * 折叠行只在状态一致时显示状态,混合状态退化为数量——避免"一行里三种状态"的歧义。
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
    val shared = group.sharedStatusKind()
    // 展开/折叠用图标,不占文案(状态/数量已经在右侧)。
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
                        if (shared != null) {
                            DoseStatusKindText(shared)
                        } else {
                            Text(
                                text = stringResource(R.string.history_group_count, group.doses.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
