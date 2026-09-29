package moe.lizi.kusuri.domain

import moe.lizi.kusuri.domain.model.Reminder

/** App 层对"通用提醒的闹钟"的最小依赖(由闹钟调度器实现)。 */
interface ReminderControl {

    /** 按重复规则排下一次;一次性已完成 / 已过期时不排。 */
    fun scheduleNext(reminder: Reminder)

    /** 清掉某条提醒的全部痕迹(闹钟 + 通知);删除或完成一次性提醒时用。 */
    fun cancel(reminderId: Long)

    /** 只撤下这一次的通知与稍后闹钟,保留重复提醒的下一次;"知道了"用它。 */
    fun clearNotification(reminderId: Long)
}
