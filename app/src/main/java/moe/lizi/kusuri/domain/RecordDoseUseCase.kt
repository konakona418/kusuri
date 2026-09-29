package moe.lizi.kusuri.domain

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource

/**
 * 记录一次计划剂量的服用/跳过(通知动作与 App 内操作共用)。
 * 幂等:同一(药物, 计划时间)只落一条记录——记录是唯一事实源;
 * 并发下重复插入由数据库唯一索引兜底。
 */
class RecordDoseUseCase(
    private val medicationRepository: MedicationRepository,
    private val doseRecordRepository: DoseRecordRepository,
    private val reminderControl: DoseReminderControl,
    private val checkLowStock: CheckLowStockUseCase,
    private val syncNotifications: SyncDoseNotificationsUseCase,
    private val clock: Clock,
) {

    /** 返回是否真正新写入了一条记录。 */
    suspend fun record(
        medicationId: Long,
        scheduledAt: Instant,
        action: DoseAction,
        source: DoseSource,
        actualAt: Instant? = null,
    ): Boolean = recordInternal(medicationId, scheduledAt, action, source, actualAt)

    /** 补记:为过去的计划剂量补一条"已服用",实际时间由用户指定。 */
    suspend fun backfill(medicationId: Long, scheduledAt: Instant, actualAt: Instant): Boolean =
        recordInternal(medicationId, scheduledAt, DoseAction.TAKEN, DoseSource.BACKFILL, actualAt)

    /** 按需(PRN)记录:没有计划时间,数量由用户给定。 */
    suspend fun recordPrn(medicationId: Long, amount: Double, actualAt: Instant): Boolean {
        medicationRepository.observeMedication(medicationId).first() ?: return false
        doseRecordRepository.record(
            medicationId = medicationId,
            scheduledAt = null,
            amount = amount,
            action = DoseAction.TAKEN,
            source = DoseSource.IN_APP,
            actualAt = actualAt,
        )
        checkLowStock.check(medicationId)
        return true
    }

    private suspend fun recordInternal(
        medicationId: Long,
        scheduledAt: Instant,
        action: DoseAction,
        source: DoseSource,
        actualAt: Instant?,
    ): Boolean {
        val medication = medicationRepository.observeMedication(medicationId).first() ?: return false
        val alreadyRecorded = doseRecordRepository.findByScheduled(medicationId, scheduledAt) != null
        if (!alreadyRecorded) {
            doseRecordRepository.record(
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                amount = medication.defaultDose,
                action = action,
                source = source,
                actualAt = actualAt,
            )
        }
        reminderControl.cancelDose(medicationId)
        // 同一时刻可能还有别的药没处理:只撤掉已处理的、把组收拢到正确味数,
        // 绝不重新挂出别的药——那一下会让别的药再响一遍(见 DoseAlertControl.refresh)。
        syncNotifications.refresh(scheduledAt, clock.instant())
        checkLowStock.check(medicationId)
        return !alreadyRecorded
    }
}
