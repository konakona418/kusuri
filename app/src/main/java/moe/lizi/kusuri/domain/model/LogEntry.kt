package moe.lizi.kusuri.domain.model

import java.time.Instant

enum class LogEntryType { SYMPTOM, NOTE }

data class LogEntry(
    val id: Long = 0L,
    val type: LogEntryType,
    val at: Instant,
    val symptom: String?,
    val severity: Int?,
    val medicationId: Long?,
    val note: String?,
)
