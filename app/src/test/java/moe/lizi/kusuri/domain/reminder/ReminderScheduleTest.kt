package moe.lizi.kusuri.domain.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.model.ReminderRepeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 通用提醒的"下一次 / 今天有没有"(docs/plan.md §14)。时区固定 +08:00,不受运行环境影响。 */
class ReminderScheduleTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun reminder(
        at: String,
        kind: ReminderRepeatKind = ReminderRepeatKind.ONCE,
        interval: Int = 1,
        doneAt: String? = null,
    ) = Reminder(
        title = "复诊",
        at = Instant.parse(at),
        repeatKind = kind,
        interval = interval,
        doneAt = doneAt?.let(Instant::parse),
    )

    private fun next(reminder: Reminder, after: String): Instant? =
        ReminderSchedule.nextOccurrence(reminder, Instant.parse(after), zone)

    @Test
    fun `a one-off reminder happens exactly once`() {
        val once = reminder("2026-09-30T01:00:00Z") // 09:00 +08

        assertEquals(Instant.parse("2026-09-30T01:00:00Z"), next(once, "2026-09-29T00:00:00Z"))
        assertNull("已经过去的一次性提醒没有下一次", next(once, "2026-10-01T00:00:00Z"))
    }

    @Test
    fun `an acknowledged one-off never happens again`() {
        val done = reminder("2026-09-30T01:00:00Z", doneAt = "2026-09-30T02:00:00Z")

        assertNull(next(done, "2026-09-01T00:00:00Z"))
        assertNull(ReminderSchedule.occurrenceOn(done, LocalDate.of(2026, 9, 30), zone))
    }

    @Test
    fun `daily keeps the local clock time`() {
        val daily = reminder("2026-09-30T01:00:00Z", ReminderRepeatKind.DAILY)

        assertEquals(Instant.parse("2026-10-06T01:00:00Z"), next(daily, "2026-10-05T04:00:00Z"))
    }

    @Test
    fun `weekly keeps the weekday`() {
        // 2026-09-30 是周三。
        val weekly = reminder("2026-09-30T01:00:00Z", ReminderRepeatKind.WEEKLY)

        assertEquals(Instant.parse("2026-10-07T01:00:00Z"), next(weekly, "2026-09-30T02:00:00Z"))
        assertNull(ReminderSchedule.occurrenceOn(weekly, LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun `monthly on the 31st falls back to the end of a short month without drifting`() {
        val monthly = reminder("2026-01-31T01:00:00Z", ReminderRepeatKind.MONTHLY) // 1/31 09:00

        assertEquals(Instant.parse("2026-02-28T01:00:00Z"), next(monthly, "2026-02-01T00:00:00Z"))
        assertEquals(
            "不能漂移成 3/28",
            Instant.parse("2026-03-31T01:00:00Z"),
            next(monthly, "2026-03-01T00:00:00Z"),
        )
        assertEquals(
            Instant.parse("2026-02-28T01:00:00Z"),
            ReminderSchedule.occurrenceOn(monthly, LocalDate.of(2026, 2, 28), zone),
        )
    }

    @Test
    fun `every n months skips the months in between`() {
        val quarterly = reminder("2026-01-31T01:00:00Z", ReminderRepeatKind.EVERY_N_MONTHS, interval = 3)

        assertEquals(Instant.parse("2026-04-30T01:00:00Z"), next(quarterly, "2026-02-01T00:00:00Z"))
        assertNull(ReminderSchedule.occurrenceOn(quarterly, LocalDate.of(2026, 3, 31), zone))
    }

    @Test
    fun `every n days counts from the first occurrence`() {
        val everyTwoDays = reminder("2026-09-30T01:00:00Z", ReminderRepeatKind.EVERY_N_DAYS, interval = 2)

        assertEquals(Instant.parse("2026-10-02T01:00:00Z"), next(everyTwoDays, "2026-09-30T02:00:00Z"))
        assertNull(ReminderSchedule.occurrenceOn(everyTwoDays, LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun `today's occurrence is what the today page shows`() {
        val daily = reminder("2026-09-30T01:00:00Z", ReminderRepeatKind.DAILY)

        assertEquals(
            Instant.parse("2026-10-05T01:00:00Z"),
            ReminderSchedule.occurrenceOn(daily, LocalDate.of(2026, 10, 5), zone),
        )
        assertNull("首次之前不该凭空发生", ReminderSchedule.occurrenceOn(daily, LocalDate.of(2026, 9, 29), zone))
    }
}
