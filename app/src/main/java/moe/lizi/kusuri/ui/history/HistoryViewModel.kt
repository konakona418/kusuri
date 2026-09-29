package moe.lizi.kusuri.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.LogEntryRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.history.AdherenceSummary
import moe.lizi.kusuri.domain.history.HistoryDay
import moe.lizi.kusuri.domain.history.HistoryDose
import moe.lizi.kusuri.domain.history.HistoryFilter
import moe.lizi.kusuri.domain.history.HistoryTimeline
import moe.lizi.kusuri.domain.history.buildHistoryTimeline
import moe.lizi.kusuri.domain.history.filteredDays
import moe.lizi.kusuri.domain.history.startDate
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine
import moe.lizi.kusuri.domain.util.clockTicks

/**
 * 历史页的界面状态。
 *
 * 过滤只作用于时间线;[adherence7]/[adherence30] 始终是近 7 / 30 天的固定口径,
 * 换筛选不会让"遵守率"跟着变——它是全量的结论,不是筛选结果的一部分。
 */
data class HistoryUiState(
    val today: LocalDate,
    val days: List<HistoryDay>,
    val adherence7: AdherenceSummary,
    val adherence30: AdherenceSummary,
    val medicationNames: Map<Long, String>,
    val filter: HistoryFilter,
    /** 未过滤前有没有内容:用来区分"还没有记录"和"这个筛选没有结果"。 */
    val hasAnyContent: Boolean,
) {
    companion object {
        val Empty = HistoryUiState(
            today = LocalDate.EPOCH,
            days = emptyList(),
            adherence7 = AdherenceSummary(taken = 0, resolved = 0),
            adherence30 = AdherenceSummary(taken = 0, resolved = 0),
            medicationNames = emptyMap(),
            filter = HistoryFilter(),
            hasAnyContent = false,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val logEntryRepository: LogEntryRepository,
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

    private val filter = MutableStateFlow(HistoryFilter())

    fun updateFilter(transform: (HistoryFilter) -> HistoryFilter) {
        filter.value = transform(filter.value)
    }

    fun resetFilter() {
        filter.value = HistoryFilter()
    }

    private val rawTimeline: Flow<HistoryTimeline> =
        combine(dateFlow, filter) { today, activeFilter -> today to activeFilter }
            .flatMapLatest { (today, activeFilter) ->
                // 查询窗口 = 默认 30 天 ∪ 筛选范围:遵守率卡片要近 30 天,自定义范围可能更早。
                val windowStart = minOf(
                    today.minusDays(WINDOW_DAYS - 1L),
                    activeFilter.startDate(today),
                )
                combine(
                    medicationRepository.observeMedications(),
                    doseRecordRepository.observeRecordsBetween(
                        engine.dayStart(windowStart),
                        engine.dayStart(today.plusDays(1)),
                    ),
                    logEntryRepository.observeBetween(
                        engine.dayStart(windowStart),
                        engine.dayStart(today.plusDays(1)),
                    ),
                    nowFlow,
                    settings.gracePeriodHours,
                ) { medications, records, logEntries, now, graceHours ->
                    buildHistoryTimeline(
                        medications = medications,
                        records = records,
                        today = today,
                        now = now,
                        engine = engine,
                        windowDays = (ChronoUnit.DAYS.between(windowStart, today) + 1).toInt(),
                        gracePeriod = Duration.ofHours(graceHours.toLong()),
                        logEntries = logEntries,
                    )
                }
            }

    val uiState: StateFlow<HistoryUiState> = combine(rawTimeline, filter) { timeline, activeFilter ->
        HistoryUiState(
            today = timeline.today,
            days = timeline.filteredDays(activeFilter),
            adherence7 = timeline.adherence7,
            adherence30 = timeline.adherence30,
            medicationNames = timeline.medicationNames,
            filter = activeFilter,
            hasAnyContent = timeline.days.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Empty)

    /** 给没有记录的剂量建一条记录,动作由用户选(未到点/到点未处理/已超时都能记)。 */
    fun record(dose: HistoryDose, actualAt: Instant, action: DoseAction) {
        val source = when (dose.status) {
            DoseStatus.Missed, DoseStatus.Untracked -> DoseSource.BACKFILL
            else -> DoseSource.IN_APP
        }
        viewModelScope.launch {
            recordDose.record(dose.medication.id, dose.scheduledAt, action, source, actualAt)
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
