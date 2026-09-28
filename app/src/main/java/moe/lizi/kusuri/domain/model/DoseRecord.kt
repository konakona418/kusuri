package moe.lizi.kusuri.domain.model

import java.time.Instant

enum class DoseAction { TAKEN, SKIPPED }

enum class DoseSource { IN_APP, NOTIFICATION, BACKFILL }

data class DoseRecord(
    val id: Long = 0L,
    val medicationId: Long,
    val scheduledAt: Instant?,
    val actualAt: Instant,
    val amount: Double,
    val action: DoseAction,
    val source: DoseSource,
)
