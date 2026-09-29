package moe.lizi.kusuri.domain

import java.time.Instant
import moe.lizi.kusuri.domain.model.DoseAlert

/** App 层对"服药提醒通知"的最小依赖(由通知出口实现,见 docs/plan.md §4.1)。 */
interface DoseAlertControl {

    /**
     * 重建某个计划时刻的服药提醒:
     * [recordedMedicationIds] 是这一刻已处理的药(撤掉它们的通知),[pending] 是还没处理的
     * (一条单独发、两条以上折叠成一组)。还没到 [now] 的点就不挂出来;
     * [alertAgain] 用于"稍后"——这一次要重新响。
     */
    fun sync(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
        alertAgain: Boolean = false,
    )

    fun cancel(medicationId: Long)

    fun cancelGroup(scheduledAt: Instant)
}
