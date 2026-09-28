package moe.lizi.kusuri.ui.medications

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val completed = medications.filter { it.status != MedicationStatus.ACTIVE }

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
                        Text(
                            text = stringResource(R.string.medications_completed_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    items(completed, key = { it.id }) { medication ->
                        MedicationCard(
                            medication = medication,
                            onClick = { onOpen(medication.id) },
                        )
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
                if (medication.status == MedicationStatus.COMPLETED) {
                    TagChip(stringResource(R.string.medication_status_completed))
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
    val low = medication.remainingStock > 0 && medication.remainingStock <= medication.lowStockThreshold
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
