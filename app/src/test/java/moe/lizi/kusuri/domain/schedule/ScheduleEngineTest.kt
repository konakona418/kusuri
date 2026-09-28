package moe.lizi.kusuri.domain.schedule

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), zone)
    private val engine = ScheduleEngine(clock) { zone }

    private fun medication(
        times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        courseStart: LocalDate = LocalDate.of(2026, 9, 1),
        courseEnd: LocalDate? = null,
    ) = Medication(
        id = 1L,
        name = "二甲双胍",
        unit = "粒",
        defaultDose = 1.0,
        mealTag = MealTag.NONE,
        notes = null,
        status = MedicationStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        courseStart = courseStart,
        courseEnd = courseEnd,
        schedule = Schedule.DailyTimes(times),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )

    @Test
    fun `planned doses on a day map local times to instants`() {
        val doses = engine.plannedDosesOn(medication(), LocalDate.of(2026, 9, 28))

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 9, 28, 8, 0).atZone(zone).toInstant(),
                LocalDateTime.of(2026, 9, 28, 20, 0).atZone(zone).toInstant(),
            ),
            doses,
        )
    }

    @Test
    fun `no doses outside the course`() {
        assertTrue(
            engine.plannedDosesOn(
                medication(courseStart = LocalDate.of(2026, 9, 28)),
                LocalDate.of(2026, 9, 27),
            ).isEmpty(),
        )
        assertTrue(
            engine.plannedDosesOn(
                medication(courseEnd = LocalDate.of(2026, 9, 27)),
                LocalDate.of(2026, 9, 28),
            ).isEmpty(),
        )
    }

    @Test
    fun `course end is inclusive`() {
        val doses = engine.plannedDosesOn(
            medication(courseEnd = LocalDate.of(2026, 9, 28)),
            LocalDate.of(2026, 9, 28),
        )

        assertEquals(2, doses.size)
    }

    @Test
    fun `next dose picks the next time today then tomorrow`() {
        val medication = medication()

        // 10:00 local → today 20:00
        assertEquals(
            LocalDateTime.of(2026, 9, 28, 20, 0).atZone(zone).toInstant(),
            engine.nextDoseAfter(medication, Instant.parse("2026-09-28T02:00:00Z")),
        )
        // 21:00 local → tomorrow 08:00
        assertEquals(
            LocalDateTime.of(2026, 9, 29, 8, 0).atZone(zone).toInstant(),
            engine.nextDoseAfter(medication, Instant.parse("2026-09-28T13:00:00Z")),
        )
    }

    @Test
    fun `next dose is null after the course ends`() {
        val medication = medication(courseEnd = LocalDate.of(2026, 9, 28))

        assertNull(engine.nextDoseAfter(medication, Instant.parse("2026-09-28T13:00:00Z")))
    }

    @Test
    fun `non daily schedules have no planned doses yet`() {
        val prn = medication().copy(schedule = Schedule.Prn(minIntervalMinutes = null, maxPerDay = null))

        assertTrue(engine.plannedDosesOn(prn, LocalDate.of(2026, 9, 28)).isEmpty())
        assertNull(engine.nextDoseAfter(prn, Instant.parse("2026-09-28T02:00:00Z")))
    }

    @Test
    fun `dst spring forward resolves to a valid instant`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val berlinEngine = ScheduleEngine(clock) { berlin }
        // 2026-03-29 02:30 does not exist in Berlin: clocks jump 02:00 → 03:00.
        val medication = medication(times = listOf(LocalTime.of(2, 30)), courseStart = LocalDate.of(2026, 1, 1))

        val doses = berlinEngine.plannedDosesOn(medication, LocalDate.of(2026, 3, 29))

        assertEquals(1, doses.size)
        assertEquals(
            LocalDateTime.of(2026, 3, 29, 3, 30).atZone(berlin).toInstant(),
            doses.single(),
        )
    }
}
