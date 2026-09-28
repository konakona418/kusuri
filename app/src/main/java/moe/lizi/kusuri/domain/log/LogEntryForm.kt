package moe.lizi.kusuri.domain.log

import java.time.Instant
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType

const val DEFAULT_SEVERITY = 3

enum class LogFormField { SYMPTOM, SEVERITY, NOTE }

enum class LogFormError { REQUIRED, OUT_OF_RANGE }

/** 记录编辑器状态(原始输入),校验与转换是纯函数。 */
data class LogEntryFormState(
    val type: LogEntryType,
    val at: Instant,
    val symptom: String = "",
    val severity: Int = DEFAULT_SEVERITY,
    val medicationId: Long? = null,
    val note: String = "",
) {
    companion object {
        fun create(type: LogEntryType, at: Instant): LogEntryFormState =
            LogEntryFormState(type = type, at = at)
    }
}

data class LogEntryErrors(private val byField: Map<LogFormField, LogFormError>) {
    val isValid: Boolean get() = byField.isEmpty()
    operator fun get(field: LogFormField): LogFormError? = byField[field]
}

fun LogEntryFormState.validate(): LogEntryErrors {
    val errors = mutableMapOf<LogFormField, LogFormError>()
    when (type) {
        LogEntryType.SYMPTOM -> {
            if (symptom.isBlank()) errors[LogFormField.SYMPTOM] = LogFormError.REQUIRED
            if (!isValidSeverity(severity)) errors[LogFormField.SEVERITY] = LogFormError.OUT_OF_RANGE
        }

        LogEntryType.NOTE -> {
            if (note.isBlank()) errors[LogFormField.NOTE] = LogFormError.REQUIRED
        }
    }
    return LogEntryErrors(errors)
}

/** 症状条目带 symptom/severity;随手记只有 note;两类都可选关联药物。 */
fun LogEntryFormState.toEntry(existingId: Long?): LogEntry {
    val isSymptom = type == LogEntryType.SYMPTOM
    return LogEntry(
        id = existingId ?: 0L,
        type = type,
        at = at,
        symptom = if (isSymptom) symptom.trim().ifBlank { null } else null,
        severity = if (isSymptom) severity else null,
        medicationId = medicationId,
        note = note.trim().ifBlank { null },
    )
}
