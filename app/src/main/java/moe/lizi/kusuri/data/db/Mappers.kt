package moe.lizi.kusuri.data.db

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule

private const val MODE_DAILY_TIMES = "DAILY_TIMES"
private const val MODE_INTERVAL = "INTERVAL"
private const val MODE_PRN = "PRN"

internal fun Medication.toEntity(): MedicationEntity {
    val interval = schedule as? Schedule.Interval
    return MedicationEntity(
        id = id,
        name = name,
        unit = unit,
        defaultDose = defaultDose,
        mealTag = mealTag.name,
        notes = notes,
        status = status.name,
        createdAt = createdAt.toEpochMilli(),
        courseStart = courseStart.toString(),
        courseEnd = courseEnd?.toString(),
        scheduleMode = when (schedule) {
            is Schedule.DailyTimes -> MODE_DAILY_TIMES
            is Schedule.Interval -> MODE_INTERVAL
            is Schedule.Prn -> MODE_PRN
        },
        intervalEvery = interval?.every,
        intervalUnit = interval?.unit?.name,
        intervalAnchor = interval?.anchor?.toString(),
        prnMinIntervalMinutes = (schedule as? Schedule.Prn)?.minIntervalMinutes,
        prnMaxPerDay = (schedule as? Schedule.Prn)?.maxPerDay,
        lowStockThreshold = lowStockThreshold,
        stockAlertArmed = stockAlertArmed,
    )
}

internal fun MedicationRow.toDomain(): Medication = Medication(
    id = medication.id,
    name = medication.name,
    unit = medication.unit,
    defaultDose = medication.defaultDose,
    mealTag = MealTag.valueOf(medication.mealTag),
    notes = medication.notes,
    status = MedicationStatus.valueOf(medication.status),
    createdAt = Instant.ofEpochMilli(medication.createdAt),
    courseStart = LocalDate.parse(medication.courseStart),
    courseEnd = medication.courseEnd?.let(LocalDate::parse),
    schedule = medication.toSchedule(times),
    lowStockThreshold = medication.lowStockThreshold,
    stockAlertArmed = medication.stockAlertArmed,
    remainingStock = remainingStock,
)

internal fun Schedule.timeEntities(medicationId: Long): List<MedicationTimeEntity> =
    when (this) {
        is Schedule.DailyTimes -> times
            .map { MedicationTimeEntity(medicationId, it.toSecondOfDay() / 60) }
            .distinct()
        else -> emptyList()
    }

private fun MedicationEntity.toSchedule(times: List<MedicationTimeEntity>): Schedule =
    when (scheduleMode) {
        MODE_DAILY_TIMES -> Schedule.DailyTimes(
            times.map { LocalTime.ofSecondOfDay(it.minuteOfDay * 60L) }.sorted(),
        )

        MODE_INTERVAL -> Schedule.Interval(
            every = requireNotNull(intervalEvery),
            unit = IntervalUnit.valueOf(requireNotNull(intervalUnit)),
            anchor = LocalDateTime.parse(requireNotNull(intervalAnchor)),
        )

        MODE_PRN -> Schedule.Prn(
            minIntervalMinutes = prnMinIntervalMinutes,
            maxPerDay = prnMaxPerDay,
        )

        else -> error("未知的排程模式: $scheduleMode")
    }
