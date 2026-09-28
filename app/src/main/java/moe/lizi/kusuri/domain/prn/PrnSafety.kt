package moe.lizi.kusuri.domain.prn

import java.time.Duration
import java.time.Instant

data class PrnSafety(
    val minutesSinceLastDose: Long?,
    val takenTodayCount: Int,
    val minIntervalMinutes: Int?,
    val maxPerDay: Int?,
) {
    val violatesMinInterval: Boolean
        get() = minutesSinceLastDose != null &&
            minIntervalMinutes != null &&
            minutesSinceLastDose < minIntervalMinutes

    val violatesMaxPerDay: Boolean
        get() = maxPerDay != null && takenTodayCount >= maxPerDay

    val hasWarning: Boolean get() = violatesMinInterval || violatesMaxPerDay
}

/**
 * 按需(PRN)药的安全检查(docs/plan.md §4):只提示、不阻止记录。
 * 记录 = 事实,防线是"吃药那一刻看得见风险"。
 */
fun prnSafety(
    lastTakenAt: Instant?,
    takenTodayCount: Int,
    minIntervalMinutes: Int?,
    maxPerDay: Int?,
    now: Instant,
): PrnSafety = PrnSafety(
    minutesSinceLastDose = lastTakenAt?.let { Duration.between(it, now).toMinutes() },
    takenTodayCount = takenTodayCount,
    minIntervalMinutes = minIntervalMinutes,
    maxPerDay = maxPerDay,
)
