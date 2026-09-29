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
 * 两条入口,边界刻意分明:
 * - [show]:到点闹钟响、平台叫我们补发时,按数据库重算这一刻还差哪些药,挂出通知
 *   ——一条单独发、两条以上折叠成一组。
 * - [refresh]:某一味药被记录之后,只撤掉它、把组收拢,绝不重新挂出别的药。
 *
 * "哪些药该在这一刻"用的判据与今天的界面一致:该药在该日展开出的计划剂量里包含这个时刻。
 */
class SyncDoseNotificationsUseCase(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val engine: ScheduleEngine,
    private val alerts: DoseAlertControl,
) {

    /** 到点或补发:重建这一刻的通知并挂出去;[alertAgain] 用于"稍后"。 */
    suspend fun show(scheduledAt: Instant, now: Instant, alertAgain: Boolean = false) {
        val (recorded, pending) = resolve(scheduledAt)
        alerts.show(scheduledAt, recorded, pending, now, alertAgain)
    }

    /** 记录之后:撤掉已处理的、收拢组摘要,**不重新挂出任何通知**。 */
    suspend fun refresh(scheduledAt: Instant, now: Instant) {
        val (recorded, pending) = resolve(scheduledAt)
        alerts.refresh(scheduledAt, recorded, pending, now)
    }

    /**
     * 补发:闹钟可能被系统吞掉(App 被杀、省电策略、更新后没重排),这里把**今天已经到点
     * 且仍在宽限窗口内**的剂量重新挂上通知——按定义它们正是"到时间了"。
     *
     * 只在**平台叫我们**的时刻调用(开机/改时间/应用更新、闹钟响了却认不出目标);
     * 不做自建的定时巡检:WorkManager 与闹钟受同一套省电策略约束,兜不住被按住的情况,
     * 只会每小时重复打扰一次(docs/plan.md §5)。
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

        due.forEach { instant -> show(instant, now, alertAgain = alertAgain) }
        return due.size
    }

    /** 这一刻(计划时刻)该处理的与还没处理的。 */
    private suspend fun resolve(scheduledAt: Instant): Pair<List<Long>, List<DoseAlert>> {
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
        return recorded to pending
    }
}
