package moe.lizi.kusuri.domain.util

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 展示用数量格式:去掉无意义的尾零,如 1.0 -> "1"、0.50 -> "0.5"。 */
fun formatAmount(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

/** 24 小时制时间,服药场景下比 AM/PM 更不易误读。 */
fun formatTime(time: LocalTime): String = time.format(TIME_FORMATTER)

fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)
