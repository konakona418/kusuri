package moe.lizi.kusuri.domain

/** App 层对"取消某次剂量的提醒痕迹"的最小依赖(由闹钟调度器实现)。 */
interface DoseReminderControl {
    fun cancelDose(medicationId: Long)

    /** 清掉某味药的全部提醒痕迹(提醒/稍后闹钟 + 通知)。 */
    fun cancelAllFor(medicationId: Long)
}
