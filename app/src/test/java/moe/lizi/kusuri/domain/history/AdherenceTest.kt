package moe.lizi.kusuri.domain.history

import java.time.Instant
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdherenceTest {

    private val taken = DoseStatus.Taken(actualAt = Instant.EPOCH, source = DoseSource.IN_APP)

    @Test
    fun `rate counts taken over resolved doses`() {
        val summary = adherenceRate(
            listOf(
                taken,
                taken,
                DoseStatus.Skipped,
                DoseStatus.Missed,
                DoseStatus.Pending,
                DoseStatus.Overdue,
                DoseStatus.Untracked,
            ),
        )

        assertEquals(2, summary.taken)
        assertEquals(4, summary.resolved)
        assertEquals(0.5, summary.rate!!, 0.0)
    }

    @Test
    fun `untracked doses do not count at all`() {
        val summary = adherenceRate(listOf(DoseStatus.Untracked, DoseStatus.Untracked))

        assertEquals(0, summary.resolved)
        assertNull(summary.rate)
    }

    @Test
    fun `unresolved doses alone have no rate`() {
        val summary = adherenceRate(listOf(DoseStatus.Pending, DoseStatus.Overdue))

        assertEquals(0, summary.resolved)
        assertNull(summary.rate)
    }

    @Test
    fun `empty history has no rate`() {
        assertNull(adherenceRate(emptyList()).rate)
    }
}
