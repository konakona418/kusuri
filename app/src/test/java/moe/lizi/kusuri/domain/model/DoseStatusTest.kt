package moe.lizi.kusuri.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseStatusTest {

    private val scheduledAt = Instant.parse("2026-09-28T00:00:00Z")
    private val actualAt = Instant.parse("2026-09-28T00:10:00Z")
    private val grace = Duration.ofHours(2)

    private fun record(
        action: DoseAction,
        at: Instant = actualAt,
    ) = DoseRecord(
        id = 1L,
        medicationId = 1L,
        scheduledAt = scheduledAt,
        actualAt = at,
        amount = 1.0,
        action = action,
        source = DoseSource.IN_APP,
    )

    @Test
    fun `pending before the scheduled time`() {
        assertEquals(
            DoseStatus.Pending,
            doseStatus(record = null, scheduledAt = scheduledAt, now = scheduledAt.minusSeconds(1), gracePeriod = grace),
        )
    }

    @Test
    fun `overdue after the scheduled time but within grace`() {
        assertEquals(
            DoseStatus.Overdue,
            doseStatus(record = null, scheduledAt = scheduledAt, now = scheduledAt.plusSeconds(1), gracePeriod = grace),
        )
    }

    @Test
    fun `missed exactly at the grace boundary`() {
        assertEquals(
            DoseStatus.Missed,
            doseStatus(record = null, scheduledAt = scheduledAt, now = scheduledAt.plus(grace), gracePeriod = grace),
        )
    }

    @Test
    fun `taken record wins over time`() {
        assertEquals(
            DoseStatus.Taken(actualAt = actualAt, source = DoseSource.IN_APP),
            doseStatus(
                record = record(DoseAction.TAKEN),
                scheduledAt = scheduledAt,
                now = scheduledAt.plus(Duration.ofHours(5)),
                gracePeriod = grace,
            ),
        )
    }

    @Test
    fun `skipped record is not missed`() {
        assertEquals(
            DoseStatus.Skipped,
            doseStatus(
                record = record(DoseAction.SKIPPED),
                scheduledAt = scheduledAt,
                now = scheduledAt.plus(Duration.ofHours(5)),
                gracePeriod = grace,
            ),
        )
    }
}
