package moe.lizi.kusuri.domain.form

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationFormValidationTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val today = LocalDate.of(2026, 9, 28)

    private fun form() = MedicationFormState.create(today).copy(unit = "粒")

    @Test
    fun `valid minimal daily form produces medication`() {
        val state = form().copy(
            name = "  二甲双胍  ",
            doseText = "0.5",
            dailyTimes = listOf(LocalTime.of(20, 0), LocalTime.of(8, 0), LocalTime.of(8, 0)),
        )

        assertTrue(state.validate().isValid)

        val medication = state.toMedication(existing = null, clock = clock)

        assertEquals(0L, medication.id)
        assertEquals("二甲双胍", medication.name)
        assertEquals("粒", medication.unit)
        assertEquals(0.5, medication.defaultDose, 0.0)
        assertEquals(MealTag.NONE, medication.mealTag)
        assertEquals(MedicationStatus.ACTIVE, medication.status)
        assertEquals(clock.instant(), medication.createdAt)
        assertEquals(today, medication.courseStart)
        assertEquals(
            Schedule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
            medication.schedule,
        )
        assertTrue(medication.stockAlertArmed)
        assertEquals(0.0, medication.remainingStock, 0.0)
    }

    @Test
    fun `blank name is rejected`() {
        val errors = form().copy(name = "   ").validate()

        assertEquals(FormError.REQUIRED, errors[FormField.NAME])
    }

    @Test
    fun `blank unit is rejected`() {
        val errors = form().copy(unit = " ").validate()

        assertEquals(FormError.REQUIRED, errors[FormField.UNIT])
    }

    @Test
    fun `non numeric dose is rejected`() {
        assertEquals(FormError.INVALID_NUMBER, form().copy(doseText = "abc").validate()[FormField.DOSE])
    }

    @Test
    fun `zero or negative dose is rejected`() {
        assertEquals(FormError.MUST_BE_POSITIVE, form().copy(doseText = "0").validate()[FormField.DOSE])
        assertEquals(FormError.MUST_BE_POSITIVE, form().copy(doseText = "-1").validate()[FormField.DOSE])
    }

    @Test
    fun `daily schedule without times is rejected`() {
        val errors = form().copy(dailyTimes = emptyList()).validate()

        assertEquals(FormError.NEEDS_AT_LEAST_ONE_TIME, errors[FormField.TIMES])
    }

    @Test
    fun `interval every must be a positive integer`() {
        assertEquals(
            FormError.INVALID_NUMBER,
            form().copy(mode = ScheduleMode.INTERVAL, intervalEveryText = "abc").validate()[FormField.INTERVAL_EVERY],
        )
        assertEquals(
            FormError.MUST_BE_POSITIVE,
            form().copy(mode = ScheduleMode.INTERVAL, intervalEveryText = "0").validate()[FormField.INTERVAL_EVERY],
        )
    }

    @Test
    fun `prn limits are optional but must be positive when present`() {
        val ok = form().copy(name = "布洛芬", mode = ScheduleMode.PRN, prnMinIntervalText = "", prnMaxPerDayText = "")
        assertTrue(ok.validate().isValid)

        val badInterval = form().copy(mode = ScheduleMode.PRN, prnMinIntervalText = "abc")
        assertEquals(FormError.INVALID_NUMBER, badInterval.validate()[FormField.PRN_MIN_INTERVAL])

        val zeroInterval = form().copy(mode = ScheduleMode.PRN, prnMinIntervalText = "0")
        assertEquals(FormError.MUST_BE_POSITIVE, zeroInterval.validate()[FormField.PRN_MIN_INTERVAL])

        val badMax = form().copy(mode = ScheduleMode.PRN, prnMaxPerDayText = "0")
        assertEquals(FormError.MUST_BE_POSITIVE, badMax.validate()[FormField.PRN_MAX_PER_DAY])
    }

    @Test
    fun `course end must not precede start`() {
        val state = form().copy(
            hasCourseEnd = true,
            courseStart = LocalDate.of(2026, 9, 28),
            courseEnd = LocalDate.of(2026, 9, 27),
        )

        assertEquals(FormError.END_BEFORE_START, state.validate()[FormField.COURSE_END])
    }

    @Test
    fun `course end is required when enabled`() {
        val errors = form().copy(hasCourseEnd = true, courseEnd = null).validate()

        assertEquals(FormError.REQUIRED, errors[FormField.COURSE_END])
    }

    @Test
    fun `threshold and initial stock must be non negative numbers`() {
        assertEquals(FormError.INVALID_NUMBER, form().copy(lowStockThresholdText = "x").validate()[FormField.THRESHOLD])
        assertEquals(FormError.MUST_BE_NON_NEGATIVE, form().copy(lowStockThresholdText = "-1").validate()[FormField.THRESHOLD])
        assertEquals(FormError.INVALID_NUMBER, form().copy(initialStockText = "x").validate()[FormField.INITIAL_STOCK])
        assertEquals(FormError.MUST_BE_NON_NEGATIVE, form().copy(initialStockText = "-1").validate()[FormField.INITIAL_STOCK])
    }

    @Test
    fun `editing keeps identity and derives prn schedule`() {
        val existing = Medication(
            id = 42L,
            name = "布洛芬",
            unit = "粒",
            defaultDose = 1.0,
            mealTag = MealTag.AFTER,
            notes = "胃不舒服时随餐吃",
            status = MedicationStatus.ACTIVE,
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            courseStart = LocalDate.of(2026, 9, 1),
            courseEnd = null,
            schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0))),
            lowStockThreshold = 5.0,
            stockAlertArmed = false,
            remainingStock = 12.0,
        )

        val state = form().copy(
            name = "布洛芬",
            notes = "胃不舒服时随餐吃",
            mode = ScheduleMode.PRN,
            prnMinIntervalText = "360",
            prnMaxPerDayText = "4",
        )

        assertTrue(state.validate().isValid)
        val medication = state.toMedication(existing = existing, clock = clock)

        assertEquals(42L, medication.id)
        assertEquals(existing.createdAt, medication.createdAt)
        assertEquals(MedicationStatus.ACTIVE, medication.status)
        assertFalse(medication.stockAlertArmed)
        assertEquals(12.0, medication.remainingStock, 0.0)
        assertEquals(Schedule.Prn(minIntervalMinutes = 360, maxPerDay = 4), medication.schedule)
        assertEquals("胃不舒服时随餐吃", medication.notes)
    }

    @Test
    fun `medication maps to form and back without losing data`() {
        val medication = Medication(
            id = 7L,
            name = "阿奇霉素",
            unit = "片",
            defaultDose = 0.5,
            mealTag = MealTag.AFTER,
            notes = "饭后半小时",
            status = MedicationStatus.COMPLETED,
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
            courseStart = LocalDate.of(2026, 9, 1),
            courseEnd = LocalDate.of(2026, 9, 7),
            schedule = Schedule.Interval(2, IntervalUnit.DAYS, java.time.LocalDateTime.of(2026, 9, 1, 9, 30)),
            lowStockThreshold = 5.0,
            stockAlertArmed = false,
            remainingStock = 3.5,
        )

        val form = medication.toFormState()

        assertTrue(form.validate().isValid)
        assertEquals(medication, form.toMedication(existing = medication, clock = clock))
    }

    @Test
    fun `interval schedule carries anchor date and time`() {
        val state = form().copy(
            mode = ScheduleMode.INTERVAL,
            intervalEveryText = "2",
            intervalUnit = IntervalUnit.DAYS,
            intervalAnchorDate = LocalDate.of(2026, 10, 1),
            intervalAnchorTime = LocalTime.of(9, 30),
        )

        val medication = state.toMedication(existing = null, clock = clock)

        assertEquals(
            Schedule.Interval(
                every = 2,
                unit = IntervalUnit.DAYS,
                anchor = java.time.LocalDateTime.of(2026, 10, 1, 9, 30),
            ),
            medication.schedule,
        )
    }
}
