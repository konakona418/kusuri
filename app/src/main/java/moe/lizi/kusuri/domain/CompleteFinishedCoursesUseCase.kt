package moe.lizi.kusuri.domain

import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

/** 疗程结束日期已过 → 自动停止提醒并标记"已完成"(记录保留,可延长疗程)。 */
class CompleteFinishedCoursesUseCase(
    private val medicationRepository: MedicationRepository,
    private val engine: ScheduleEngine,
) {

    suspend fun completeFinished() {
        val today = engine.today()
        medicationRepository.observeMedications().first()
            .filter { it.status == MedicationStatus.ACTIVE }
            .filter { it.courseEnd != null && it.courseEnd.isBefore(today) }
            .forEach { medicationRepository.setStatus(it.id, MedicationStatus.COMPLETED) }
    }
}
