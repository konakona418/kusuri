package moe.lizi.kusuri.domain.schedule

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.Schedule

/**
 * 纯排程引擎:把排程展开为绝对时刻。时刻按设备本地时区解释(墙钟语义,见 docs/plan.md §8)。
 *
 * 间隔制为固定锚点循环:每 N 小时按绝对时长排(医学上"每 8 小时"指间隔,不随 DST 变形);
 * 每 N 天按本地日期排(同一钟点)。
 */
class ScheduleEngine(
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun today(): LocalDate = clock.instant().atZone(zone()).toLocalDate()

    /** 把绝对时刻换算成设备本地日期(墙钟语义的唯一出处)。 */
    fun dateOf(instant: Instant): LocalDate = instant.atZone(zone()).toLocalDate()

    fun dayStart(date: LocalDate): Instant = date.atStartOfDay(zone()).toInstant()

    fun plannedDosesOn(medication: Medication, date: LocalDate): List<Instant> {
        if (!isWithinCourse(medication, date)) return emptyList()
        return when (val schedule = medication.schedule) {
            is Schedule.DailyTimes -> schedule.times
                .sorted()
                .map { time -> date.atTime(time).atZone(zone()).toInstant() }

            is Schedule.Interval -> intervalDosesOn(schedule, date)

            is Schedule.Prn -> emptyList()
        }
    }

    fun nextDoseAfter(medication: Medication, after: Instant): Instant? =
        when (val schedule = medication.schedule) {
            is Schedule.DailyTimes -> nextDailyDoseAfter(medication, schedule, after)
            is Schedule.Interval -> nextIntervalDoseAfter(medication, schedule, after)
            is Schedule.Prn -> null
        }

    private fun nextDailyDoseAfter(
        medication: Medication,
        schedule: Schedule.DailyTimes,
        after: Instant,
    ): Instant? {
        val times = schedule.times.sorted()
        if (times.isEmpty()) return null

        val courseEnd = medication.courseEnd
        var date = after.atZone(zone()).toLocalDate()
        repeat(MAX_LOOKAHEAD_DAYS) {
            if (courseEnd != null && date.isAfter(courseEnd)) return null
            if (isWithinCourse(medication, date)) {
                for (time in times) {
                    val instant = date.atTime(time).atZone(zone()).toInstant()
                    if (instant.isAfter(after)) return instant
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun intervalDosesOn(schedule: Schedule.Interval, date: LocalDate): List<Instant> {
        val z = zone()
        return when (schedule.unit) {
            IntervalUnit.HOURS -> {
                val stepMillis = schedule.every * MILLIS_PER_HOUR
                if (stepMillis <= 0) return emptyList()
                val anchorMillis = schedule.anchor.atZone(z).toInstant().toEpochMilli()
                val dayStartMillis = date.atStartOfDay(z).toInstant().toEpochMilli()
                val dayEndMillis = date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()

                val result = mutableListOf<Instant>()
                var k = ceilDiv(dayStartMillis - anchorMillis, stepMillis)
                while (anchorMillis + k * stepMillis < dayEndMillis) {
                    val instant = anchorMillis + k * stepMillis
                    if (instant >= dayStartMillis) result += Instant.ofEpochMilli(instant)
                    k++
                }
                result
            }

            IntervalUnit.DAYS -> {
                if (schedule.every <= 0) return emptyList()
                val days = ChronoUnit.DAYS.between(schedule.anchor.toLocalDate(), date)
                if (days < 0 || days % schedule.every != 0L) {
                    emptyList()
                } else {
                    listOf(date.atTime(schedule.anchor.toLocalTime()).atZone(z).toInstant())
                }
            }
        }
    }

    private fun nextIntervalDoseAfter(
        medication: Medication,
        schedule: Schedule.Interval,
        after: Instant,
    ): Instant? {
        val z = zone()
        val courseStartInstant = medication.courseStart.atStartOfDay(z).toInstant()
        val courseEndExclusive = medication.courseEnd?.plusDays(1)?.atStartOfDay(z)?.toInstant()

        return when (schedule.unit) {
            IntervalUnit.HOURS -> {
                val stepMillis = schedule.every * MILLIS_PER_HOUR
                if (stepMillis <= 0) return null
                val anchorMillis = schedule.anchor.atZone(z).toInstant().toEpochMilli()
                val lowerBound = maxOf(after.toEpochMilli(), courseStartInstant.toEpochMilli())

                var k = ceilDiv(lowerBound - anchorMillis, stepMillis)
                var candidate = anchorMillis + k * stepMillis
                while (candidate <= after.toEpochMilli()) {
                    k++
                    candidate = anchorMillis + k * stepMillis
                }
                if (courseEndExclusive != null && candidate >= courseEndExclusive.toEpochMilli()) {
                    return null
                }
                Instant.ofEpochMilli(candidate)
            }

            IntervalUnit.DAYS -> {
                if (schedule.every <= 0) return null
                val anchorDate = schedule.anchor.toLocalDate()
                val anchorTime = schedule.anchor.toLocalTime()
                val courseEnd = medication.courseEnd
                var date = maxOf(after.atZone(z).toLocalDate(), medication.courseStart)
                repeat(MAX_LOOKAHEAD_DAYS) {
                    if (courseEnd != null && date.isAfter(courseEnd)) return null
                    val days = ChronoUnit.DAYS.between(anchorDate, date)
                    if (days >= 0 && days % schedule.every == 0L) {
                        val instant = date.atTime(anchorTime).atZone(z).toInstant()
                        if (instant.isAfter(after)) return instant
                    }
                    date = date.plusDays(1)
                }
                null
            }
        }
    }

    private fun isWithinCourse(medication: Medication, date: LocalDate): Boolean {
        val courseEnd = medication.courseEnd
        return !date.isBefore(medication.courseStart) &&
            (courseEnd == null || !date.isAfter(courseEnd))
    }

    private companion object {
        const val MAX_LOOKAHEAD_DAYS = 366
        const val MILLIS_PER_HOUR = 3_600_000L

        /** 向上取整的整数除法;nested companion 里用 Math.floorDiv 支持负数。 */
        fun ceilDiv(a: Long, b: Long): Long = -Math.floorDiv(-a, b)
    }
}
