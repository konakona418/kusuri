package moe.lizi.kusuri.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.model.DOSE_GRACE_PERIOD
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.doseStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

data class TodayDoseItem(
    val medication: Medication,
    val scheduledAt: Instant,
    val status: DoseStatus,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val engine: ScheduleEngine,
    private val recordDose: RecordDoseUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** 状态随时间流逝而变(待服用 → 到时间了 → 错过),界面打开时定期重算。 */
    private val nowFlow: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(STATUS_REFRESH_MILLIS)
        }
    }

    val items: StateFlow<List<TodayDoseItem>> =
        combine(
            medicationRepository.observeMedications(),
            nowFlow,
        ) { medications, now -> medications to now }
            .flatMapLatest { (medications, now) ->
                val today = engine.today()
                doseRecordRepository
                    .observeScheduledBetween(engine.dayStart(today), engine.dayStart(today.plusDays(1)))
                    .map { records -> buildItems(medications, records, now) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markTaken(item: TodayDoseItem) = record(item, DoseAction.TAKEN)

    fun markSkipped(item: TodayDoseItem) = record(item, DoseAction.SKIPPED)

    fun backfill(item: TodayDoseItem, actualAt: Instant) {
        viewModelScope.launch {
            recordDose.backfill(item.medication.id, item.scheduledAt, actualAt)
        }
    }

    private fun record(item: TodayDoseItem, action: DoseAction) {
        viewModelScope.launch {
            recordDose.record(item.medication.id, item.scheduledAt, action, DoseSource.IN_APP)
        }
    }

    private fun buildItems(
        medications: List<Medication>,
        records: List<DoseRecord>,
        now: Instant,
    ): List<TodayDoseItem> {
        val today = engine.today()
        val recordsByDose = records
            .filter { it.scheduledAt != null }
            .associateBy { it.medicationId to it.scheduledAt }
        return medications
            .filter { it.status == MedicationStatus.ACTIVE }
            .flatMap { medication ->
                engine.plannedDosesOn(medication, today).map { scheduledAt ->
                    TodayDoseItem(
                        medication = medication,
                        scheduledAt = scheduledAt,
                        status = doseStatus(
                            record = recordsByDose[medication.id to scheduledAt],
                            scheduledAt = scheduledAt,
                            now = now,
                            gracePeriod = DOSE_GRACE_PERIOD,
                        ),
                    )
                }
            }
            .sortedBy { it.scheduledAt }
    }

    private companion object {
        const val STATUS_REFRESH_MILLIS = 30_000L
    }
}
