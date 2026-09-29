package moe.lizi.kusuri.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.model.DoseAlert
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

/**
 * 把"某个计划时刻的服药提醒"同步成实际的通知(docs/plan.md §4.1)。
 *
 * 到点闹钟触发、以及任何一条剂量被记录之后(通知按钮 / App 内打卡 / 补记)都调它:
 * 按数据库重算这一刻还有哪些药没处理,再交给 [DoseAlertControl] 重建
 * ——一条单独发、两条以上折叠成一组。
 *
 * "哪些药该在这一刻"用的判据与今天的界面一致:该药在该日展开出的计划剂量里包含这个时刻。
 */
class SyncDoseNotificationsUseCase(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val engine: ScheduleEngine,
    private val alerts: DoseAlertControl,
) {

    suspend fun sync(scheduledAt: Instant, now: Instant, alertAgain: Boolean = false) {
        val date = engine.dateOf(scheduledAt)
        val scheduled = medicationRepository.observeMedications().first()
            .filter { it.status == MedicationStatus.ACTIVE }
            .filter { medication -> scheduledAt in engine.plannedDosesOn(medication, date) }

        val recorded = mutableListOf<Long>()
        val pending = mutableListOf<DoseAlert>()
        scheduled.forEach { medication ->
            if (doseRecordRepository.findByScheduled(medication.id, scheduledAt) != null) {
                recorded += medication.id
            } else {
                pending += DoseAlert(medication, scheduledAt)
            }
        }

        alerts.sync(
            scheduledAt = scheduledAt,
            recordedMedicationIds = recorded,
            pending = pending,
            now = now,
            alertAgain = alertAgain,
        )
    }

    /**
     * 补发:闹钟可能被系统吞掉(App 被杀、省电策略、更新后没重排),这里把**今天已经到点
     * 且仍在宽限窗口内**的剂量重新挂上通知——按定义它们正是"到时间了"。
     *
     * 幂等:已经挂着的通知只是原地更新(不会重复响),已处理的不会出现。
     */
    suspend fun catchUp(
        now: Instant,
        gracePeriod: Duration,
        zone: ZoneId = ZoneId.systemDefault(),
        alertAgain: Boolean = false,
    ): Int {
        val today = now.atZone(zone).toLocalDate()
        val medications = medicationRepository.observeMedications().first()
            .filter { it.status == MedicationStatus.ACTIVE }

        val due = medications
            .flatMap { medication -> engine.plannedDosesOn(medication, today) }
            .distinct()
            .filter { instant -> !instant.isAfter(now) && now.isBefore(instant.plus(gracePeriod)) }
            .sorted()

        due.forEach { instant -> sync(instant, now, alertAgain = alertAgain) }
        return due.size
    }
}
