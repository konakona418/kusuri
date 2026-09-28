package moe.lizi.kusuri.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.history.AdherenceSummary
import moe.lizi.kusuri.domain.history.adherenceRate
import moe.lizi.kusuri.domain.model.DOSE_GRACE_PERIOD
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.doseStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

data class HistoryDose(
    val medication: Medication,
    val scheduledAt: Instant,
    val record: DoseRecord?,
    val status: DoseStatus,
)

data class HistoryDay(val date: LocalDate, val doses: List<HistoryDose>)

data class HistoryUiState(
    val days: List<HistoryDay>,
    val adherence7: AdherenceSummary,
    val adherence30: AdherenceSummary,
) {
    companion object {
        val Empty = HistoryUiState(
            days = emptyList(),
            adherence7 = AdherenceSummary(taken = 0, resolved = 0),
            adherence30 = AdherenceSummary(taken = 0, resolved = 0),
        )
    }
}

class HistoryViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val engine: ScheduleEngine,
    private val recordDose: RecordDoseUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val windowStart = engine.today().minusDays(WINDOW_DAYS - 1L)

    private val nowFlow: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(STATUS_REFRESH_MILLIS)
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        medicationRepository.observeMedications(),
        doseRecordRepository.observeScheduledBetween(
            engine.dayStart(windowStart),
            engine.dayStart(engine.today().plusDays(1)),
        ),
        nowFlow,
    ) { medications, records, now -> buildState(medications, records, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Empty)

    fun backfill(dose: HistoryDose, actualAt: Instant) {
        viewModelScope.launch {
            recordDose.backfill(dose.medication.id, dose.scheduledAt, actualAt)
        }
    }

    fun saveRecord(dose: HistoryDose, actualAt: Instant, action: DoseAction) {
        val record = dose.record ?: return
        viewModelScope.launch {
            doseRecordRepository.update(record.id, actualAt, action)
        }
    }

    fun deleteRecord(dose: HistoryDose) {
        val record = dose.record ?: return
        viewModelScope.launch {
            doseRecordRepository.delete(record.id)
        }
    }

    private fun buildState(
        medications: List<Medication>,
        records: List<DoseRecord>,
        now: Instant,
    ): HistoryUiState {
        val today = engine.today()
        val recordsByDose = records
            .filter { it.scheduledAt != null }
            .associateBy { it.medicationId to it.scheduledAt }

        val days = mutableListOf<HistoryDay>()
        var date = today
        var scanned = 0
        while (scanned < WINDOW_DAYS) {
            val doses = medications
                .flatMap { medication ->
                    engine.plannedDosesOn(medication, date).map { scheduledAt ->
                        val record = recordsByDose[medication.id to scheduledAt]
                        HistoryDose(
                            medication = medication,
                            scheduledAt = scheduledAt,
                            record = record,
                            status = doseStatus(record, scheduledAt, now, DOSE_GRACE_PERIOD),
                        )
                    }
                }
                .sortedBy { it.scheduledAt }
            if (doses.isNotEmpty()) days += HistoryDay(date = date, doses = doses)
            date = date.minusDays(1)
            scanned++
        }

        val last7 = days
            .filter { !it.date.isBefore(today.minusDays(6)) }
            .flatMap { it.doses }
            .map { it.status }
        val last30 = days.flatMap { it.doses }.map { it.status }

        return HistoryUiState(
            days = days,
            adherence7 = adherenceRate(last7),
            adherence30 = adherenceRate(last30),
        )
    }

    private companion object {
        const val WINDOW_DAYS = 30
        const val STATUS_REFRESH_MILLIS = 30_000L
    }
}
