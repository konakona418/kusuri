package moe.lizi.kusuri.domain.model

import java.time.Duration
import java.time.Instant

/** 全局宽限窗口:计划时间后多久仍未处理即判为错过。可配置化留待 M5。 */
val DOSE_GRACE_PERIOD: Duration = Duration.ofHours(2)

sealed interface DoseStatus {
    /** 计划时间未到。 */
    data object Pending : DoseStatus

    /** 已到计划时间、仍在宽限窗口内。 */
    data object Overdue : DoseStatus

    data class Taken(val actualAt: Instant, val source: DoseSource) : DoseStatus

    data object Skipped : DoseStatus

    /** 超过计划时间 + 宽限窗口仍无记录(可补记,见 M3)。 */
    data object Missed : DoseStatus
}

fun doseStatus(
    record: DoseRecord?,
    scheduledAt: Instant,
    now: Instant,
    gracePeriod: Duration = DOSE_GRACE_PERIOD,
): DoseStatus = when {
    record == null && now >= scheduledAt.plus(gracePeriod) -> DoseStatus.Missed
    record == null && now >= scheduledAt -> DoseStatus.Overdue
    record == null -> DoseStatus.Pending
    record.action == DoseAction.TAKEN -> DoseStatus.Taken(record.actualAt, record.source)
    else -> DoseStatus.Skipped
}
