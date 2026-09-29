package moe.lizi.kusuri.domain.model

import java.time.Duration
import java.time.Instant

/** 全局宽限窗口:计划时间后多久仍未处理即判为错过。可配置化留待 M5。 */
val DOSE_GRACE_PERIOD: Duration = Duration.ofHours(2)

/** 状态的"种类":不含实际时间这类细节,用于比较与折叠行的汇总。 */
enum class DoseStatusKind { PENDING, OVERDUE, TAKEN, SKIPPED, MISSED, UNTRACKED }

sealed interface DoseStatus {
    /** 计划时间未到。 */
    data object Pending : DoseStatus

    /** 已到计划时间、仍在宽限窗口内。 */
    data object Overdue : DoseStatus

    data class Taken(val actualAt: Instant, val source: DoseSource) : DoseStatus

    data object Skipped : DoseStatus

    /** 超过计划时间 + 宽限窗口仍无记录(可补记,见 M3)。 */
    data object Missed : DoseStatus

    /**
     * 计划时间早于该药物的创建时间:这段时间 App 还没开始追踪,
     * 不算错过、不计入遵守率,但允许补记(早上吃过、晚上才来建药)。
     */
    data object Untracked : DoseStatus
}

fun doseStatus(
    record: DoseRecord?,
    scheduledAt: Instant,
    now: Instant,
    gracePeriod: Duration = DOSE_GRACE_PERIOD,
    trackedFrom: Instant? = null,
): DoseStatus = when {
    record == null && trackedFrom != null && scheduledAt.isBefore(trackedFrom) -> DoseStatus.Untracked
    record == null && now >= scheduledAt.plus(gracePeriod) -> DoseStatus.Missed
    record == null && now >= scheduledAt -> DoseStatus.Overdue
    record == null -> DoseStatus.Pending
    record.action == DoseAction.TAKEN -> DoseStatus.Taken(record.actualAt, record.source)
    else -> DoseStatus.Skipped
}

val DoseStatus.kind: DoseStatusKind
    get() = when (this) {
        DoseStatus.Pending -> DoseStatusKind.PENDING
        DoseStatus.Overdue -> DoseStatusKind.OVERDUE
        is DoseStatus.Taken -> DoseStatusKind.TAKEN
        DoseStatus.Skipped -> DoseStatusKind.SKIPPED
        DoseStatus.Missed -> DoseStatusKind.MISSED
        DoseStatus.Untracked -> DoseStatusKind.UNTRACKED
    }
