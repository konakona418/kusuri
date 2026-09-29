package moe.lizi.kusuri.domain

import java.time.Instant
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
}
