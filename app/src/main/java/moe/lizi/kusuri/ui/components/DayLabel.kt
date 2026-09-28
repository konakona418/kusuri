package moe.lizi.kusuri.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import moe.lizi.kusuri.R

/** 日志/历史共用的日期标题:今天 / 昨天 / M月d日 周X。 */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.history_today)
    today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> stringResource(
        R.string.history_day_label,
        date.monthValue,
        date.dayOfMonth,
        stringArrayResource(R.array.weekday_names)[date.dayOfWeek.value - 1],
    )
}
