package moe.lizi.kusuri.domain

import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus

/** App 层对"发一条低库存提醒"的最小依赖(由通知器实现)。 */
interface LowStockAlertControl {
    fun notifyLowStock(medication: Medication)
}

/**
 * 低库存判定(docs/plan.md §6):
 * 剩余 ≤ 阈值且告警仍处于武装状态 → 提醒一次并解除武装;
 * 补货回到阈值以上后重新武装,下次跌破再提醒。
 */
class CheckLowStockUseCase(
    private val medicationRepository: MedicationRepository,
    private val control: LowStockAlertControl,
) {

    /** [alertIfLow] 为 false 时只同步武装状态,不发通知(用于新建药物后的初始化)。 */
    suspend fun check(medicationId: Long, alertIfLow: Boolean = true) {
        val medication = medicationRepository.observeMedication(medicationId).first() ?: return
        if (medication.status == MedicationStatus.ARCHIVED) return

        val low = medication.remainingStock <= medication.lowStockThreshold
        when {
            low && medication.stockAlertArmed -> {
                if (alertIfLow) control.notifyLowStock(medication)
                medicationRepository.setStockAlertArmed(medicationId, false)
            }

            !low && !medication.stockAlertArmed -> {
                medicationRepository.setStockAlertArmed(medicationId, true)
            }
        }
    }
}
