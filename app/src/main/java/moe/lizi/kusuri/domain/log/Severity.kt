package moe.lizi.kusuri.domain.log

/** 症状严重程度的取值范围(1 轻 – 5 重)。 */
const val MIN_SEVERITY = 1
const val MAX_SEVERITY = 5

fun isValidSeverity(severity: Int): Boolean = severity in MIN_SEVERITY..MAX_SEVERITY
