package moe.lizi.kusuri.domain.history

import java.time.LocalDate
import moe.lizi.kusuri.domain.model.DoseStatusKind
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.model.kind

/** 历史页的时间范围。预设档覆盖遵守率卡片用的近 7 / 30 天,自定义用于回看更早的区间。 */
enum class HistoryRange { TODAY, LAST_7_DAYS, LAST_30_DAYS, CUSTOM }

/** 类型维度:服药 / 症状 / 随手记。 */
enum class HistoryType { DOSE, SYMPTOM, NOTE }

/** 状态维度,只作用于服药行。"未处理" = 还没有任何记录(待服用 / 到时间了 / 未追踪)。 */
enum class HistoryStatus {
    TAKEN,
    SKIPPED,
    MISSED,
    UNHANDLED;

    val kinds: Set<DoseStatusKind>
        get() = when (this) {
            TAKEN -> setOf(DoseStatusKind.TAKEN)
            SKIPPED -> setOf(DoseStatusKind.SKIPPED)
            MISSED -> setOf(DoseStatusKind.MISSED)
            UNHANDLED -> setOf(
                DoseStatusKind.PENDING,
                DoseStatusKind.OVERDUE,
                DoseStatusKind.UNTRACKED,
            )
        }
}

/**
 * 历史页的过滤条件(docs/plan.md §7)。
 *
 * 每个维度互相独立:时间范围、类型、药物、状态。空集 / null 表示"这一维不限制"。
 * 故意不落盘:换页回来仍在,重启回到默认(近 30 天、全部),免得下次打开一头雾水。
 */
data class HistoryFilter(
    val range: HistoryRange = HistoryRange.LAST_30_DAYS,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val types: Set<HistoryType> = HistoryType.entries.toSet(),
    val medicationId: Long? = null,
    val statuses: Set<HistoryStatus> = emptySet(),
) {
    /** 除时间范围外还有维度在起作用(筛选入口据此提示"正在筛选")。 */
    val narrowing: Boolean
        get() = types.size != HistoryType.entries.size || medicationId != null || statuses.isNotEmpty()

    val isDefault: Boolean
        get() = range == HistoryRange.LAST_30_DAYS && !narrowing
}

/** 过滤窗口的起点(自定义缺省回落到近 30 天)。 */
fun HistoryFilter.startDate(today: LocalDate): LocalDate = when (range) {
    HistoryRange.TODAY -> today
    HistoryRange.LAST_7_DAYS -> today.minusDays(6)
    HistoryRange.LAST_30_DAYS -> today.minusDays(29)
    HistoryRange.CUSTOM -> customFrom ?: today.minusDays(29)
}

/** 过滤窗口的终点;未来不展开,所以最多到今天。 */
fun HistoryFilter.endDate(today: LocalDate): LocalDate = when (range) {
    HistoryRange.CUSTOM -> (customTo ?: today).coerceAtMost(today)
    else -> today
}

/** 过滤一天;这一天没有剩下的内容就返回 null(整天从列表里消失)。 */
fun HistoryDay.filteredBy(filter: HistoryFilter): HistoryDay? {
    val doses = doses.filter { dose ->
        HistoryType.DOSE in filter.types &&
            (filter.medicationId == null || dose.medication.id == filter.medicationId) &&
            (filter.statuses.isEmpty() || filter.statuses.any { dose.status.kind in it.kinds })
    }
    val logs = logs.filter { entry ->
        val type = when (entry.type) {
            LogEntryType.SYMPTOM -> HistoryType.SYMPTOM
            LogEntryType.NOTE -> HistoryType.NOTE
        }
        type in filter.types &&
            (filter.medicationId == null || entry.medicationId == filter.medicationId)
    }
    if (doses.isEmpty() && logs.isEmpty()) return null
    return copy(doses = doses, logs = logs)
}

/** 时间范围 + 各维度一起过滤;返回的 days 仍按日期倒序。 */
fun HistoryTimeline.filteredDays(filter: HistoryFilter): List<HistoryDay> {
    val start = filter.startDate(today)
    val end = filter.endDate(today)
    return days
        .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        .mapNotNull { it.filteredBy(filter) }
}
