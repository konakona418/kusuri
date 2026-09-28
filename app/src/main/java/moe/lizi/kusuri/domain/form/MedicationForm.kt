package moe.lizi.kusuri.domain.form

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.util.formatAmount

/** 表单上的调度模式选择,与 [Schedule] 的三个变体一一对应。 */
enum class ScheduleMode { DAILY_TIMES, INTERVAL, PRN }

enum class FormField {
    NAME, UNIT, DOSE, TIMES, INTERVAL_EVERY, PRN_MIN_INTERVAL, PRN_MAX_PER_DAY,
    COURSE_END, THRESHOLD, INITIAL_STOCK,
}

enum class FormError {
    REQUIRED,
    INVALID_NUMBER,
    MUST_BE_POSITIVE,
    MUST_BE_NON_NEGATIVE,
    NEEDS_AT_LEAST_ONE_TIME,
    END_BEFORE_START,
}

/** 表单原始输入(全部为字符串/领域枚举),由 UI 持有;校验与转换是纯函数。 */
data class MedicationFormState(
    val name: String = "",
    val unit: String = "",
    val doseText: String = "1",
    val mealTag: MealTag = MealTag.NONE,
    val notes: String = "",
    val mode: ScheduleMode = ScheduleMode.DAILY_TIMES,
    val dailyTimes: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val intervalEveryText: String = "8",
    val intervalUnit: IntervalUnit = IntervalUnit.HOURS,
    val intervalAnchorDate: LocalDate,
    val intervalAnchorTime: LocalTime = LocalTime.of(8, 0),
    val prnMinIntervalText: String = "",
    val prnMaxPerDayText: String = "",
    val hasCourseEnd: Boolean = false,
    val courseStart: LocalDate,
    val courseEnd: LocalDate? = null,
    val lowStockThresholdText: String = "5",
    val initialStockText: String = "0",
) {
    companion object {
        /** [today] 由调用方从注入的时钟取得,表单本身不读取系统时间。 */
        fun create(today: LocalDate): MedicationFormState = MedicationFormState(
            intervalAnchorDate = today,
            courseStart = today,
        )
    }
}

data class MedicationFormErrors(private val byField: Map<FormField, FormError>) {
    val isValid: Boolean get() = byField.isEmpty()
    operator fun get(field: FormField): FormError? = byField[field]
}

fun MedicationFormState.validate(): MedicationFormErrors {
    val errors = mutableMapOf<FormField, FormError>()

    if (name.isBlank()) errors[FormField.NAME] = FormError.REQUIRED
    if (unit.isBlank()) errors[FormField.UNIT] = FormError.REQUIRED

    when (val dose = doseText.trim().toDoubleOrNull()) {
        null -> errors[FormField.DOSE] = FormError.INVALID_NUMBER
        else -> if (dose <= 0) errors[FormField.DOSE] = FormError.MUST_BE_POSITIVE
    }

    when (mode) {
        ScheduleMode.DAILY_TIMES -> {
            if (dailyTimes.isEmpty()) errors[FormField.TIMES] = FormError.NEEDS_AT_LEAST_ONE_TIME
        }

        ScheduleMode.INTERVAL -> {
            when (val every = intervalEveryText.trim().toIntOrNull()) {
                null -> errors[FormField.INTERVAL_EVERY] = FormError.INVALID_NUMBER
                else -> if (every <= 0) errors[FormField.INTERVAL_EVERY] = FormError.MUST_BE_POSITIVE
            }
        }

        ScheduleMode.PRN -> {
            validateOptionalPositiveInt(prnMinIntervalText) { errors[FormField.PRN_MIN_INTERVAL] = it }
            validateOptionalPositiveInt(prnMaxPerDayText) { errors[FormField.PRN_MAX_PER_DAY] = it }
        }
    }

    if (hasCourseEnd) {
        when {
            courseEnd == null -> errors[FormField.COURSE_END] = FormError.REQUIRED
            courseEnd.isBefore(courseStart) -> errors[FormField.COURSE_END] = FormError.END_BEFORE_START
        }
    }

    parseNonNegative(lowStockThresholdText)?.let { errors[FormField.THRESHOLD] = it }
    parseNonNegative(initialStockText)?.let { errors[FormField.INITIAL_STOCK] = it }

    return MedicationFormErrors(errors)
}

/**
 * 仅在 [validate] 通过后调用。表单即事实:编辑时 [existing] 只提供不被表单编辑的身份字段
 * (id、创建时间、状态、库存告警武装标志),其余全部来自表单。
 */
fun MedicationFormState.toMedication(existing: Medication?, clock: Clock): Medication {
    val schedule = when (mode) {
        ScheduleMode.DAILY_TIMES -> Schedule.DailyTimes(dailyTimes.distinct().sorted())
        ScheduleMode.INTERVAL -> Schedule.Interval(
            every = intervalEveryText.trim().toInt(),
            unit = intervalUnit,
            anchor = LocalDateTime.of(intervalAnchorDate, intervalAnchorTime),
        )

        ScheduleMode.PRN -> Schedule.Prn(
            minIntervalMinutes = prnMinIntervalText.trim().toIntOrNull(),
            maxPerDay = prnMaxPerDayText.trim().toIntOrNull(),
        )
    }

    return Medication(
        id = existing?.id ?: 0L,
        name = name.trim(),
        unit = unit.trim(),
        defaultDose = doseText.trim().toDouble(),
        mealTag = mealTag,
        notes = notes.trim().ifBlank { null },
        status = existing?.status ?: MedicationStatus.ACTIVE,
        createdAt = existing?.createdAt ?: clock.instant(),
        courseStart = courseStart,
        courseEnd = if (hasCourseEnd) courseEnd else null,
        schedule = schedule,
        lowStockThreshold = lowStockThresholdText.trim().toDouble(),
        stockAlertArmed = existing?.stockAlertArmed ?: true,
        remainingStock = existing?.remainingStock ?: 0.0,
    )
}

private fun validateOptionalPositiveInt(text: String, onError: (FormError) -> Unit) {
    if (text.isBlank()) return
    when (val value = text.trim().toIntOrNull()) {
        null -> onError(FormError.INVALID_NUMBER)
        else -> if (value <= 0) onError(FormError.MUST_BE_POSITIVE)
    }
}

private fun parseNonNegative(text: String): FormError? {
    val value = text.trim().toDoubleOrNull() ?: return FormError.INVALID_NUMBER
    return if (value < 0) FormError.MUST_BE_NON_NEGATIVE else null
}

/** 编辑回填:把已有药物还原为表单状态,与 [toMedication] 互逆。 */
fun Medication.toFormState(): MedicationFormState {
    val interval = schedule as? Schedule.Interval
    val prn = schedule as? Schedule.Prn
    return MedicationFormState(
        name = name,
        unit = unit,
        doseText = formatAmount(defaultDose),
        mealTag = mealTag,
        notes = notes.orEmpty(),
        mode = when (schedule) {
            is Schedule.DailyTimes -> ScheduleMode.DAILY_TIMES
            is Schedule.Interval -> ScheduleMode.INTERVAL
            is Schedule.Prn -> ScheduleMode.PRN
        },
        dailyTimes = (schedule as? Schedule.DailyTimes)?.times ?: listOf(LocalTime.of(8, 0)),
        intervalEveryText = interval?.every?.toString() ?: "8",
        intervalUnit = interval?.unit ?: IntervalUnit.HOURS,
        intervalAnchorDate = interval?.anchor?.toLocalDate() ?: courseStart,
        intervalAnchorTime = interval?.anchor?.toLocalTime() ?: LocalTime.of(8, 0),
        prnMinIntervalText = prn?.minIntervalMinutes?.toString().orEmpty(),
        prnMaxPerDayText = prn?.maxPerDay?.toString().orEmpty(),
        hasCourseEnd = courseEnd != null,
        courseStart = courseStart,
        courseEnd = courseEnd,
        lowStockThresholdText = formatAmount(lowStockThreshold),
        initialStockText = "0",
    )
}
