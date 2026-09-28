package moe.lizi.kusuri.domain

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
) {

    /** 返回是否真正新写入了一条记录。 */
    suspend fun record(
        medicationId: Long,
        scheduledAt: Instant,
        action: DoseAction,
        source: DoseSource,
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
            )
        }
        reminderControl.cancelDose(medicationId)
        return !alreadyRecorded
    }

    /** 补记:为过去的计划剂量补一条"已服用",实际时间由用户指定。 */
    suspend fun backfill(medicationId: Long, scheduledAt: Instant, actualAt: Instant): Boolean {
        val medication = medicationRepository.observeMedication(medicationId).first() ?: return false
        val alreadyRecorded = doseRecordRepository.findByScheduled(medicationId, scheduledAt) != null
        if (!alreadyRecorded) {
            doseRecordRepository.record(
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                amount = medication.defaultDose,
                action = DoseAction.TAKEN,
                source = DoseSource.BACKFILL,
                actualAt = actualAt,
            )
        }
        reminderControl.cancelDose(medicationId)
        return !alreadyRecorded
    }
}
