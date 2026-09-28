package moe.lizi.kusuri.ui.medications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.ui.AppViewModelProvider
import moe.lizi.kusuri.ui.components.TagChip

@Composable
fun MedicationListScreen(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: MedicationListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val medications by viewModel.medications.collectAsStateWithLifecycle()
    val active = medications.filter { it.status == MedicationStatus.ACTIVE }
    val completed = medications.filter { it.status == MedicationStatus.COMPLETED }
    val archived = medications.filter { it.status == MedicationStatus.ARCHIVED }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (medications.isEmpty()) {
            EmptyMedications()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(active, key = { it.id }) { medication ->
                    MedicationCard(
                        medication = medication,
                        onClick = { onOpen(medication.id) },
                    )
                }
                if (completed.isNotEmpty()) {
                    item {
                        SectionHeader(text = stringResource(R.string.medications_completed_section))
                    }
                    items(completed, key = { it.id }) { medication ->
                        MedicationCard(
                            medication = medication,
                            onClick = { onOpen(medication.id) },
                        )
                    }
                }
                if (archived.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showArchived = !showArchived }
                                .padding(top = 16.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.medications_archived_section, archived.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (showArchived) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showArchived) {
                        items(archived, key = { it.id }) { medication ->
                            MedicationCard(
                                medication = medication,
                                onClick = { onOpen(medication.id) },
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_medication))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationCard(medication: Medication, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                mealTagLabel(medication.mealTag)?.let { TagChip(it) }
                when (medication.status) {
                    MedicationStatus.ACTIVE -> Unit
                    MedicationStatus.COMPLETED -> TagChip(stringResource(R.string.medication_status_completed))
                    MedicationStatus.ARCHIVED -> TagChip(stringResource(R.string.medication_status_archived))
                }
            }
            Text(
                text = stringResource(
                    R.string.detail_dose,
                    formatAmount(medication.defaultDose),
                    medication.unit,
                ) + " · " + scheduleSummary(medication.schedule),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            medication.courseEnd?.let { courseEnd ->
                Text(
                    text = stringResource(R.string.course_until, formatDate(courseEnd)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StockLine(medication)
        }
    }
}

@Composable
private fun StockLine(medication: Medication) {
    val low = medication.remainingStock <= medication.lowStockThreshold
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.stock_remaining,
                formatAmount(medication.remainingStock),
                medication.unit,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (low) {
            Text(
                text = stringResource(R.string.stock_low),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EmptyMedications() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.medications_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.medications_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
