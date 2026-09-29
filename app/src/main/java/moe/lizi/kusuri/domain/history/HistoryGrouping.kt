package moe.lizi.kusuri.domain.history

import java.time.Instant
import moe.lizi.kusuri.domain.model.DoseStatusKind
import moe.lizi.kusuri.domain.model.kind

/**
 * 同一计划时刻的多味药在时间线里折叠成一组(docs/plan.md §7)。
 *
 * 按**计划时刻**分组:历史时间线展示的就是计划时间;若同一时刻有服用也有错过,
 * 依然是一组(它们是同一个服药时点),展开后各自带着自己的状态。
 */
data class HistoryDoseGroup(val scheduledAt: Instant, val doses: List<HistoryDose>) {
    /** 只有一味药时不折叠,直接按普通行显示。 */
    val collapsing: Boolean get() = doses.size > 1
}

/** 输入应按计划时间排好序;输出保持时间顺序,组内保持原顺序。 */
fun groupDosesByScheduledTime(doses: List<HistoryDose>): List<HistoryDoseGroup> =
    doses.groupBy { it.scheduledAt }
        .entries
        .sortedBy { it.key }
        .map { (scheduledAt, group) -> HistoryDoseGroup(scheduledAt, group) }

/** 组内状态种类一致时给出该种类;混合则返回 null(折叠行退化为只显示数量)。 */
fun HistoryDoseGroup.sharedStatusKind(): DoseStatusKind? {
    val kinds = doses.map { it.status.kind }.distinct()
    return kinds.singleOrNull()
}
