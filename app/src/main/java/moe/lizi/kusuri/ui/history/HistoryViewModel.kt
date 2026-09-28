package moe.lizi.kusuri.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.history.HistoryDose
import moe.lizi.kusuri.domain.history.HistoryTimeline
import moe.lizi.kusuri.domain.history.buildHistoryTimeline
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.schedule.ScheduleEngine
import moe.lizi.kusuri.domain.util.clockTicks

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val settings: SettingsRepository,
    private val engine: ScheduleEngine,
    private val recordDose: RecordDoseUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val nowFlow: Flow<Instant> = clockTicks(clock, STATUS_REFRESH_MILLIS)

    /** 日期只在真正跨天时变化,跨天后重建查询窗口。 */
    private val dateFlow: Flow<LocalDate> = flow {
        var last: LocalDate? = null
        while (true) {
            val today = engine.today()
            if (today != last) {
                emit(today)
                last = today
            }
            delay(DATE_CHECK_MILLIS)
        }
    }

    val timeline: StateFlow<HistoryTimeline> = dateFlow
        .flatMapLatest { today ->
            combine(
                medicationRepository.observeMedications(),
                doseRecordRepository.observeRecordsBetween(
                    engine.dayStart(today.minusDays(WINDOW_DAYS - 1L)),
                    engine.dayStart(today.plusDays(1)),
                ),
                nowFlow,
                settings.gracePeriodHours,
            ) { medications, records, now, graceHours ->
                buildHistoryTimeline(
                    medications = medications,
                    records = records,
                    today = today,
                    now = now,
                    engine = engine,
                    windowDays = WINDOW_DAYS,
                    gracePeriod = Duration.ofHours(graceHours.toLong()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryTimeline.Empty)

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

    private companion object {
        const val WINDOW_DAYS = 30
        const val STATUS_REFRESH_MILLIS = 30_000L
        const val DATE_CHECK_MILLIS = 60_000L
    }
}
