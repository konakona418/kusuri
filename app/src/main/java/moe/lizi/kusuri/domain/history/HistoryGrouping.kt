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

/**
 * 折叠行右侧只留一个状态词,取"最需要注意"的那一个:
 * 已错过 > 到时间了 > 待服用 > 已跳过 > 未追踪 > 已服用。
 *
 * 刻意不做"一致才显示、混合显示数量":数量词不说明任何事,而漏服不该被藏起来。
 */
fun HistoryDoseGroup.attentionStatusKind(): DoseStatusKind {
    // 组恒非空(由 [groupDosesByScheduledTime] 保证);空组兜底只为防御。
    val worst = doses.maxByOrNull { it.status.kind.attentionRank() } ?: return DoseStatusKind.PENDING
    return worst.status.kind
}

private fun DoseStatusKind.attentionRank(): Int = when (this) {
    DoseStatusKind.MISSED -> 5
    DoseStatusKind.OVERDUE -> 4
    DoseStatusKind.PENDING -> 3
    DoseStatusKind.SKIPPED -> 2
    DoseStatusKind.UNTRACKED -> 1
    DoseStatusKind.TAKEN -> 0
}
