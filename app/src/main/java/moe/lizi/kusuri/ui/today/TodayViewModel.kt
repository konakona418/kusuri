package moe.lizi.kusuri.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.ReminderRepository
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.reminder.ReminderSchedule
import moe.lizi.kusuri.domain.todayOrder
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.doseStatus
import moe.lizi.kusuri.domain.prn.PrnSafety
import moe.lizi.kusuri.domain.prn.prnSafety
import moe.lizi.kusuri.domain.schedule.ScheduleEngine
import moe.lizi.kusuri.domain.util.clockTicks

data class TodayDoseItem(
    val medication: Medication,
    val scheduledAt: Instant,
    val status: DoseStatus,
)

data class PrnInfo(
    val medication: Medication,
    val lastTakenAt: Instant?,
    val takenTodayCount: Int,
)

/** 今天到期的一条通用提醒(提醒本身在"提醒"标签里管理)。 */
data class TodayReminder(
    val reminder: Reminder,
    val at: Instant,
)

data class TodayUiState(
    val now: Instant,
    val doses: List<TodayDoseItem>,
    val prnInfos: List<PrnInfo>,
    val lowStockMedications: List<Medication>,
    val reminders: List<TodayReminder>,
) {
    val isEmpty: Boolean
        get() = doses.isEmpty() && prnInfos.isEmpty() && reminders.isEmpty()

    companion object {
        val Empty = TodayUiState(
            now = Instant.EPOCH,
            doses = emptyList(),
            prnInfos = emptyList(),
            lowStockMedications = emptyList(),
            reminders = emptyList(),
        )
    }
}

/** 按需药记录对话框所需的上下文(药物 + 安全提示)。 */
data class PrnRecordState(
    val medication: Medication,
    val safety: PrnSafety,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val reminderRepository: ReminderRepository,
    private val settings: SettingsRepository,
    private val engine: ScheduleEngine,
    private val recordDose: RecordDoseUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** 状态随时间流逝而变(待服用 → 到时间了 → 错过),界面打开时定期重算。 */
    private val nowFlow = clockTicks(clock, STATUS_REFRESH_MILLIS)

    val uiState: StateFlow<TodayUiState> = combine(
        medicationRepository.observeMedications(),
        reminderRepository.observeAll(),
        nowFlow,
        settings.gracePeriodHours,
    ) { medications, reminders, now, graceHours ->
        TodayInputs(medications, reminders, now, graceHours)
    }
        .flatMapLatest { inputs ->
            val today = engine.today()
            val grace = Duration.ofHours(inputs.graceHours.toLong())
            val zone = ZoneId.systemDefault()
            doseRecordRepository
                .observeRecordsBetween(engine.dayStart(today), engine.dayStart(today.plusDays(1)))
                .map { records ->
                    val active = inputs.medications.filter { it.status == MedicationStatus.ACTIVE }
                    TodayUiState(
                        now = inputs.now,
                        doses = buildDoses(active, records, inputs.now, grace),
                        prnInfos = active
                            .filter { it.schedule is Schedule.Prn }
                            .map { medication ->
                                PrnInfo(
                                    medication = medication,
                                    lastTakenAt = doseRecordRepository.lastTaken(medication.id)?.actualAt,
                                    takenTodayCount = records.count {
                                        it.medicationId == medication.id && it.action == DoseAction.TAKEN
                                    },
                                )
                            },
                        lowStockMedications = active.filter {
                            it.remainingStock <= it.lowStockThreshold
                        },
                        reminders = inputs.reminders
                            .mapNotNull { reminder ->
                                ReminderSchedule.occurrenceOn(reminder, today, zone)
                                    ?.let { TodayReminder(reminder, it) }
                            }
                            .sortedBy { it.at },
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Empty)

    private data class TodayInputs(
        val medications: List<Medication>,
        val reminders: List<Reminder>,
        val now: Instant,
        val graceHours: Int,
    )

    private val _prnTarget = MutableStateFlow<PrnRecordState?>(null)
    val prnTarget: StateFlow<PrnRecordState?> = _prnTarget.asStateFlow()

    fun markTaken(item: TodayDoseItem) = record(item, DoseAction.TAKEN)

    fun markSkipped(item: TodayDoseItem) = record(item, DoseAction.SKIPPED)

    fun backfill(item: TodayDoseItem, actualAt: Instant) {
        viewModelScope.launch {
            recordDose.backfill(item.medication.id, item.scheduledAt, actualAt)
        }
    }

    fun startPrnRecord(medication: Medication) {
        viewModelScope.launch {
            val today = engine.today()
            val lastTaken = doseRecordRepository.lastTaken(medication.id)
            val takenToday = doseRecordRepository.countTaken(
                medicationId = medication.id,
                from = engine.dayStart(today),
                to = engine.dayStart(today.plusDays(1)),
            )
            val schedule = medication.schedule as? Schedule.Prn
            _prnTarget.value = PrnRecordState(
                medication = medication,
                safety = prnSafety(
                    lastTakenAt = lastTaken?.actualAt,
                    takenTodayCount = takenToday,
                    minIntervalMinutes = schedule?.minIntervalMinutes,
                    maxPerDay = schedule?.maxPerDay,
                    now = clock.instant(),
                ),
            )
        }
    }

    fun cancelPrnRecord() {
        _prnTarget.value = null
    }

    fun confirmPrnRecord(amount: Double) {
        val target = _prnTarget.value ?: return
        viewModelScope.launch {
            recordDose.recordPrn(target.medication.id, amount, clock.instant())
            _prnTarget.value = null
        }
    }

    private fun record(item: TodayDoseItem, action: DoseAction) {
        viewModelScope.launch {
            recordDose.record(item.medication.id, item.scheduledAt, action, DoseSource.IN_APP)
        }
    }

    private fun buildDoses(
        medications: List<Medication>,
        records: List<DoseRecord>,
        now: Instant,
        gracePeriod: Duration,
    ): List<TodayDoseItem> {
        val today = engine.today()
        val recordsByDose = records
            .filter { it.scheduledAt != null }
            .associateBy { it.medicationId to it.scheduledAt }
        val ordered = medications
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
                            gracePeriod = gracePeriod,
                            trackedFrom = medication.createdAt,
                        ),
                    )
                }
            }
        // 待处理的(未到点 + 到点但还在宽限窗口内)排在前面,已处理/已超时的排在后面;
        // 两组内都按计划时间由早到晚(docs/plan.md §7、domain/TodayOrder.kt)。
        return ordered.sortedWith(todayOrder({ it.scheduledAt }, { it.status }))
    }

    private companion object {
        const val STATUS_REFRESH_MILLIS = 30_000L
    }
}
