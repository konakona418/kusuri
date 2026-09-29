package moe.lizi.kusuri.domain

import moe.lizi.kusuri.domain.model.DoseStatus
import java.time.Instant
/**
 * 今日列表的排序位次(docs/plan.md §7)。
 *
 * 只有**已经不需要你动手**的才沉下去:
 *
 * - `0` = 待处理:未到点([DoseStatus.Pending])与到点但仍在宽限窗口内([DoseStatus.Overdue])。
 *   刚到点的那一剂留在上面——它才是此刻最该做的事。
 * - `1` = 已处理或已过期:已服用、已跳过、已错过(超时)、以及未追踪的。
 *
 * 同一位次内按计划时间由早到晚:上面那组就是"最该处理的在最上面"。
 */
fun todayLane(status: DoseStatus): Int = when (status) {
    DoseStatus.Pending, DoseStatus.Overdue -> 0
    is DoseStatus.Taken, DoseStatus.Skipped, DoseStatus.Missed, DoseStatus.Untracked -> 1
}

/**
 * 今日列表的排序规则。视图与测试共用这一份,免得"规则"在两处各写一遍。
 */
fun <T> todayOrder(
    scheduledAtOf: (T) -> Instant,
    statusOf: (T) -> DoseStatus,
): Comparator<T> = compareBy({ todayLane(statusOf(it)) }, { scheduledAtOf(it) })
