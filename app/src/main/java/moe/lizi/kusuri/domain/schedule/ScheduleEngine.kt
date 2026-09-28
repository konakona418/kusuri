package moe.lizi.kusuri.domain.schedule

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.Schedule

/**
 * 纯排程引擎:把排程展开为绝对时刻。时刻按设备本地时区解释(墙钟语义,见 docs/plan.md §8);
 * M2 只处理"每天固定时间",间隔制与按需在 M4 接入。
 */
class ScheduleEngine(
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun today(): LocalDate = clock.instant().atZone(zone()).toLocalDate()

    fun dayStart(date: LocalDate): Instant = date.atStartOfDay(zone()).toInstant()

    fun plannedDosesOn(medication: Medication, date: LocalDate): List<Instant> {
        val schedule = medication.schedule as? Schedule.DailyTimes ?: return emptyList()
        if (!isWithinCourse(medication, date)) return emptyList()
        return schedule.times.sorted().map { time -> date.atTime(time).atZone(zone()).toInstant() }
    }

    fun nextDoseAfter(medication: Medication, after: Instant): Instant? {
        val schedule = medication.schedule as? Schedule.DailyTimes ?: return null
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

    private fun isWithinCourse(medication: Medication, date: LocalDate): Boolean {
        val courseEnd = medication.courseEnd
        return !date.isBefore(medication.courseStart) &&
            (courseEnd == null || !date.isAfter(courseEnd))
    }

    private companion object {
        const val MAX_LOOKAHEAD_DAYS = 366
    }
}
