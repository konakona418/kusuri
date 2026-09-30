package moe.lizi.kusuri.domain

import java.time.Instant
import moe.lizi.kusuri.domain.model.DoseAlert

/** App 层对"服药提醒通知"的最小依赖(由通知出口实现,见 docs/plan.md §4.1)。 */
interface DoseAlertControl {

    /**
     * 重建并挂出某个计划时刻的服药提醒:到点闹钟响、平台叫我们补发时走这里。
     * [recordedMedicationIds] 是这一刻已处理的药(撤掉它们的通知),[pending] 是还没处理的
     * (一条单独发、两条以上折叠成一组)。还没到 [now] 的点就不挂出来;
     * [alertAgain] 用于"稍后"——这一次要重新响。
     */
    fun show(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
        alertAgain: Boolean = false,
    )

    /**
     * 记录之后的刷新:**只做减法,一次 `notify` 都不发**。
     *
     * 撤掉已处理的;组里只剩不到两味时连摘要一起撤掉(它已经没有意义)。
     * 其他的什么都不动——不重新挂、也不"更新摘要的数字":实测小米 HyperOS 对重挂
     * 照样会把通知重新上屏(AlertCoordinator 的 `onViewBound`),于是"补记一条"
     * 又会把这一刻别的药弹出来。摘要上的味数会暂时偏大,直到下一次到点或开机重建时校正;
     * 而味数从来不是重点(docs/plan.md §4.1、§11)。
     */
    fun refresh(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
    )

    fun cancel(medicationId: Long)

    fun cancelGroup(scheduledAt: Instant)
}
