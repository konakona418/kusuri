package moe.lizi.kusuri.ui.medications

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.util.formatTime

@Composable
fun scheduleSummary(schedule: Schedule): String = when (schedule) {
    is Schedule.DailyTimes -> stringResource(
        R.string.schedule_daily_times,
        schedule.times.joinToString(stringResource(R.string.list_separator), transform = ::formatTime),
    )

    is Schedule.Interval ->
        if (schedule.unit == IntervalUnit.HOURS) {
            stringResource(R.string.schedule_interval_hours, schedule.every)
        } else {
            stringResource(R.string.schedule_interval_days, schedule.every)
        }

    is Schedule.Prn -> stringResource(R.string.schedule_prn)
}

@Composable
fun mealTagLabel(tag: MealTag): String? = when (tag) {
    MealTag.NONE -> null
    MealTag.BEFORE -> stringResource(R.string.meal_before)
    MealTag.AFTER -> stringResource(R.string.meal_after)
    MealTag.WITH -> stringResource(R.string.meal_with)
}

@Composable
fun formatMinuteSpan(minutes: Int): String =
    if (minutes % 60 == 0) {
        stringResource(R.string.duration_hours, minutes / 60)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
