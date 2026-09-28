package moe.lizi.kusuri.domain.util

import java.math.BigDecimal

/** 展示用数量格式:去掉无意义的尾零,如 1.0 -> "1"、0.50 -> "0.5"。 */
fun formatAmount(value: Double): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
