package moe.lizi.kusuri.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.DoseRecordDialog
import moe.lizi.kusuri.ui.components.DoseStatusText
import moe.lizi.kusuri.ui.components.dayLabel
import moe.lizi.kusuri.ui.components.severityDots

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HistoryDose?>(null) }
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
        DoseRecordDialog(
            title = stringResource(
                R.string.record_dialog_title,
                dose.medication.name,
                formatTime(dose.scheduledAt.atZone(zone).toLocalTime()),
            ),
            confirmLabel = if (dose.record == null) {
                stringResource(R.string.action_backfill)
            } else {
                stringResource(R.string.action_save)
            },
            initialActualAt = dose.record?.actualAt ?: dose.scheduledAt,
            initialAction = dose.record?.action ?: DoseAction.TAKEN,
            showActionChoice = dose.record != null,
            onDismiss = { editing = null },
            onConfirm = { actualAt, action ->
                if (dose.record == null) {
                    viewModel.backfill(dose, actualAt)
                } else {
                    viewModel.saveRecord(dose, actualAt, action)
                }
                editing = null
            },
            onDelete = dose.record?.let {
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
        Text(
            text = summary.rate?.let { rate ->
                stringResource(R.string.history_adherence_percent, (rate * 100).roundToInt())
            } ?: stringResource(R.string.history_adherence_none),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDoseRow(dose: HistoryDose, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatTime(dose.scheduledAt.atZone(zone).toLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(dose.medication.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(
                        R.string.detail_dose,
                        formatAmount(dose.medication.defaultDose),
                        dose.medication.unit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DoseStatusText(dose.status)
        }
    }
}

@Composable
private fun HistoryLogRow(entry: LogEntry, medicationName: String?, zone: ZoneId) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatTime(entry.at.atZone(zone).toLocalTime()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(entry.symptom.orEmpty(), style = MaterialTheme.typography.bodyMedium)
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                entry.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
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

private sealed interface TimelineRow {
    val at: Instant
    val key: String

    data class Dose(val dose: HistoryDose) : TimelineRow {
        override val at: Instant get() = dose.scheduledAt
        override val key: String get() = "dose:${dose.medication.id}:${dose.scheduledAt.epochSecond}"
    }

    data class Log(val entry: LogEntry) : TimelineRow {
        override val at: Instant get() = entry.at
        override val key: String get() = "log:${entry.id}"
    }
}

private fun HistoryDay.rows(): List<TimelineRow> =
    (doses.map { TimelineRow.Dose(it) } + logs.map { TimelineRow.Log(it) })
        .sortedByDescending { it.at }
