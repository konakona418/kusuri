package moe.lizi.kusuri.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.model.ReminderRepeatKind

/**
 * 通用提醒的"发生时刻"计算(docs/plan.md §14)。
 *
 * 规则:
 * - 所有重复都从**第一次的时刻**起算,并保持本地钟点(夏令时按墙钟走);
 * - 月类规则锚定第一次那天的**日号**,短月回退到月末,而且**不会漂移**
 *   (1 月 31 日 → 2 月 28 日 → **3 月 31 日**,不是 3 月 28 日);
 * - 一次性提醒被"知道了"之后不再发生。
 */
object ReminderSchedule {

    /** 迭代上限:即使每 N 天(N=1)也够算 50 年,防止写错规则时死循环。 */
    private const val MAX_INDEX = 20_000L

    /** 第 [index] 次发生(从 0 开始);一次性提醒只有第 0 次。 */
    fun occurrenceAt(reminder: Reminder, index: Long, zone: ZoneId): Instant? {
        if (index < 0) return null
        val anchor = reminder.at.atZone(zone)
        val startDate = anchor.toLocalDate()
        val date = when (reminder.repeatKind) {
            ReminderRepeatKind.ONCE -> if (index == 0L) startDate else return null
            ReminderRepeatKind.DAILY -> startDate.plusDays(index)
            ReminderRepeatKind.WEEKLY -> startDate.plusWeeks(index)
            ReminderRepeatKind.MONTHLY -> startDate.plusMonths(index)
            ReminderRepeatKind.EVERY_N_DAYS -> startDate.plusDays(index * reminder.safeInterval)
            ReminderRepeatKind.EVERY_N_MONTHS -> startDate.plusMonths(index * reminder.safeInterval)
        }
        return date.atTime(anchor.toLocalTime()).atZone(zone).toInstant()
    }

    /** 严格晚于 [after] 的下一次;没有下一次(一次性已完成 / 已过期)返回 null。 */
    fun nextOccurrence(reminder: Reminder, after: Instant, zone: ZoneId): Instant? {
        if (reminder.doneAt != null) return null
        if (reminder.repeatKind == ReminderRepeatKind.ONCE) {
            return reminder.at.takeIf { it.isAfter(after) }
        }
        var index = firstCandidateIndex(reminder, after, zone)
        while (index < MAX_INDEX) {
            val occurrence = occurrenceAt(reminder, index, zone) ?: return null
            if (occurrence.isAfter(after)) return occurrence
            index++
        }
        return null
    }

    /** [date] 这一天该提醒是否发生;发生则给出当天的时刻。 */
    fun occurrenceOn(reminder: Reminder, date: LocalDate, zone: ZoneId): Instant? {
        if (reminder.doneAt != null) return null
        val anchor = reminder.at.atZone(zone)
        val startDate = anchor.toLocalDate()
        if (date.isBefore(startDate)) return null

        val occurs = when (reminder.repeatKind) {
            ReminderRepeatKind.ONCE -> date == startDate
            ReminderRepeatKind.DAILY -> true
            ReminderRepeatKind.WEEKLY -> date.dayOfWeek == startDate.dayOfWeek
            ReminderRepeatKind.MONTHLY -> isAnchorDay(startDate, date)
            ReminderRepeatKind.EVERY_N_MONTHS ->
                isAnchorDay(startDate, date) && monthsBetween(startDate, date) % reminder.safeInterval == 0L

            ReminderRepeatKind.EVERY_N_DAYS ->
                ChronoUnit.DAYS.between(startDate, date) % reminder.safeInterval == 0L
        }
        if (!occurs) return null
        return date.atTime(anchor.toLocalTime()).atZone(zone).toInstant()
    }

    /**
     * 短月回退:锚定 31 号时,2 月的那一次落在 2 月最后一天。
     * 以"月首"之间的距离判断月份,避免 [ChronoUnit.MONTHS] 在 1/31→2/28 上返回 0 的坑。
     */
    private fun isAnchorDay(startDate: LocalDate, date: LocalDate): Boolean {
        val effectiveDay = minOf(startDate.dayOfMonth, date.lengthOfMonth())
        return date.dayOfMonth == effectiveDay
    }

    private fun monthsBetween(startDate: LocalDate, date: LocalDate): Long =
        ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), date.withDayOfMonth(1))

    /** 从"上一次可能已经过去的位置"起跳,避免为了找个未来时刻空转几千轮。 */
    private fun firstCandidateIndex(reminder: Reminder, after: Instant, zone: ZoneId): Long {
        val startDate = reminder.at.atZone(zone).toLocalDate()
        val afterDate = after.atZone(zone).toLocalDate()
        val estimate = when (reminder.repeatKind) {
            ReminderRepeatKind.ONCE -> 0L
            ReminderRepeatKind.DAILY -> ChronoUnit.DAYS.between(startDate, afterDate)
            ReminderRepeatKind.WEEKLY -> ChronoUnit.DAYS.between(startDate, afterDate) / 7
            ReminderRepeatKind.MONTHLY -> monthsBetween(startDate, afterDate)
            ReminderRepeatKind.EVERY_N_DAYS ->
                ChronoUnit.DAYS.between(startDate, afterDate) / reminder.safeInterval

            ReminderRepeatKind.EVERY_N_MONTHS ->
                monthsBetween(startDate, afterDate) / reminder.safeInterval
        }
        return (estimate - 1).coerceAtLeast(0L)
    }
}
