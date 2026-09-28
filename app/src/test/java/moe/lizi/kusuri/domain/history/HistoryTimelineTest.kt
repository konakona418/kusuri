package moe.lizi.kusuri.domain.history

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.schedule.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTimelineTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-28T01:00:00Z") // 09:00 本地
    private val clock = Clock.fixed(now, zone)
    private val engine = ScheduleEngine(clock) { zone }
    private val today = LocalDate.of(2026, 9, 28)

    private fun medication(status: MedicationStatus = MedicationStatus.ACTIVE) = Medication(
        id = 1L,
        name = "二甲双胍",
        unit = "粒",
        defaultDose = 1.0,
        mealTag = MealTag.NONE,
        notes = null,
        status = status,
        createdAt = Instant.EPOCH,
        courseStart = LocalDate.of(2026, 9, 26),
        courseEnd = null,
        schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0))),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )

    private fun record(
        scheduledAt: Instant,
        action: DoseAction = DoseAction.TAKEN,
    ) = DoseRecord(
        id = 1L,
        medicationId = 1L,
        scheduledAt = scheduledAt,
        actualAt = scheduledAt,
        amount = 1.0,
        action = action,
        source = DoseSource.IN_APP,
    )

    private fun at(date: LocalDate, time: LocalTime = LocalTime.of(8, 0)): Instant =
        LocalDateTime.of(date, time).atZone(zone).toInstant()

    @Test
    fun `planned doses get derived statuses and count towards adherence`() {
        val timeline = buildHistoryTimeline(
            medications = listOf(medication()),
            records = listOf(record(at(LocalDate.of(2026, 9, 27)))),
            today = today,
            now = now,
            engine = engine,
        )

        val statusByDate = timeline.days.associate { it.date to it.doses.single().status }

        assertTrue(statusByDate[LocalDate.of(2026, 9, 27)] is DoseStatus.Taken)
        assertEquals(DoseStatus.Missed, statusByDate[LocalDate.of(2026, 9, 26)])
        assertEquals(DoseStatus.Overdue, statusByDate[today]) // 08:00 计划、现在 09:00,仍在宽限窗口内
        assertEquals(1, timeline.adherence7.taken)
        assertEquals(2, timeline.adherence7.resolved) // 已服用 1 + 错过 1;今天尚未判定
    }

    @Test
    fun `records that no longer match a planned dose stay visible and count`() {
        val orphan = at(LocalDate.of(2026, 9, 27), LocalTime.of(9, 0)) // 比计划的 08:00 晚一小时

        val timeline = buildHistoryTimeline(
            medications = listOf(medication()),
            records = listOf(record(orphan)),
            today = today,
            now = now,
            engine = engine,
        )

        val dosesOn27 = timeline.days.single { it.date == LocalDate.of(2026, 9, 27) }.doses
        assertEquals(2, dosesOn27.size)
        assertTrue(dosesOn27.any { it.scheduledAt == orphan && it.status is DoseStatus.Taken })
        assertEquals(1, timeline.adherence7.taken)
        assertEquals(3, timeline.adherence7.resolved) // 孤儿记录(已服用)+ 9/27 错过 + 9/26 错过
    }

    @Test
    fun `archived medications are not expanded into missed doses`() {
        val timeline = buildHistoryTimeline(
            medications = listOf(medication(status = MedicationStatus.ARCHIVED)),
            records = emptyList(),
            today = today,
            now = now,
            engine = engine,
        )

        assertTrue(timeline.days.isEmpty())
        assertEquals(0, timeline.adherence30.resolved)
    }

    @Test
    fun `archived medications keep their recorded doses visible`() {
        val recorded = at(LocalDate.of(2026, 9, 27))

        val timeline = buildHistoryTimeline(
            medications = listOf(medication(status = MedicationStatus.ARCHIVED)),
            records = listOf(record(recorded)),
            today = today,
            now = now,
            engine = engine,
        )

        val dose = timeline.days.single { it.date == LocalDate.of(2026, 9, 27) }.doses.single()
        assertTrue(dose.status is DoseStatus.Taken)
        assertEquals(1, timeline.adherence30.taken)
    }
}
