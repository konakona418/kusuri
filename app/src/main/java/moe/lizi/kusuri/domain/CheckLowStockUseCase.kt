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
 *
 * [initialize] 用于新建药物:只有录入过库存(剩余 > 0)才算"在追踪",从这里开始武装;
 * 从未录入库存的药物不会凭空打扰,但一旦补货就会进入正常告警周期。
 */
class CheckLowStockUseCase(
    private val medicationRepository: MedicationRepository,
    private val control: LowStockAlertControl,
) {

    suspend fun check(medicationId: Long) {
        val medication = medicationRepository.observeMedication(medicationId).first() ?: return
        if (medication.status == MedicationStatus.ARCHIVED) return

        val low = medication.remainingStock <= medication.lowStockThreshold
        when {
            low && medication.stockAlertArmed -> {
                control.notifyLowStock(medication)
                medicationRepository.setStockAlertArmed(medicationId, false)
            }

            !low && !medication.stockAlertArmed -> {
                medicationRepository.setStockAlertArmed(medicationId, true)
            }
        }
    }

    suspend fun initialize(medicationId: Long) {
        val medication = medicationRepository.observeMedication(medicationId).first() ?: return
        medicationRepository.setStockAlertArmed(medicationId, medication.remainingStock > 0)
    }
}
