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
     * 记录之后的刷新:**只做减法**。
     *
     * 撤掉已处理的,把组摘要收拢到正确的味数与药名,剩一味时退回单独一条;
     * 已经不在通知栏里的就让它不在,不"复活"。重挂一条不在栏里的通知,
     * 在系统看来是一条**新通知**(会响)——用户按下"已服用"的那一下,
     * 不该让这一刻别的药再响一遍(docs/plan.md §4.1)。
     */
    fun refresh(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
    )

    fun cancel(medicationId: Long)

    fun cancelGroup(scheduledAt: Instant)
}
