package moe.lizi.kusuri.domain.history

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.DoseStatusKind
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 历史时间线里"同一时刻多味药"的分组(docs/plan.md §7)。 */
class HistoryGroupingTest {

    private val at9 = Instant.parse("2026-09-29T09:00:00Z")
    private val at21 = Instant.parse("2026-09-29T21:00:00Z")

    @Test
    fun `doses at the same planned time form one group`() {
        val groups = groupDosesByScheduledTime(
            listOf(
                dose("普萘洛尔", at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
                dose("丙戊酸钠", at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
                dose("氟伏沙明", at21, DoseStatus.Pending),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(at9, groups[0].scheduledAt)
        assertEquals(listOf("普萘洛尔", "丙戊酸钠"), groups[0].doses.map { it.medication.name })
        assertTrue(groups[0].collapsing)
        assertFalse("单独一味药不折叠", groups[1].collapsing)
    }

    @Test
    fun `the folded label is the one that needs the most attention`() {
        val allTaken = HistoryDoseGroup(
            scheduledAt = at9,
            doses = listOf(
                dose("普萘洛尔", at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
                // 实际时间不同也算同一种状态:比较的是"种类",不是那条记录本身。
                dose("丙戊酸钠", at9, DoseStatus.Taken(at9.plusSeconds(600), DoseSource.BACKFILL)),
            ),
        )
        assertEquals(DoseStatusKind.TAKEN, allTaken.attentionStatusKind())

        val missed = HistoryDoseGroup(
            scheduledAt = at9,
            doses = listOf(
                dose("普萘洛尔", at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
                dose("丙戊酸钠", at9, DoseStatus.Missed),
            ),
        )
        assertEquals("漏服不能被藏起来", DoseStatusKind.MISSED, missed.attentionStatusKind())

        val stillPending = HistoryDoseGroup(
            scheduledAt = at9,
            doses = listOf(
                dose("普萘洛尔", at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
                dose("丙戊酸钠", at9, DoseStatus.Pending),
            ),
        )
        assertEquals(DoseStatusKind.PENDING, stillPending.attentionStatusKind())

        val skipped = HistoryDoseGroup(
            scheduledAt = at9,
            doses = listOf(
                dose("普萘洛尔", at9, DoseStatus.Pending),
                dose("丙戊酸钠", at9, DoseStatus.Skipped),
            ),
        )
        assertEquals("还有没服的,就该显示待服用", DoseStatusKind.PENDING, skipped.attentionStatusKind())
    }

    private fun dose(name: String, at: Instant, status: DoseStatus) = HistoryDose(
        medication = Medication(
            id = name.hashCode().toLong(),
            name = name,
            unit = "粒",
            defaultDose = 1.0,
            mealTag = MealTag.NONE,
            notes = null,
            status = MedicationStatus.ACTIVE,
            createdAt = Instant.EPOCH,
            courseStart = LocalDate.of(2026, 9, 1),
            courseEnd = null,
            schedule = Schedule.DailyTimes(listOf(LocalTime.of(9, 0))),
            lowStockThreshold = 5.0,
            stockAlertArmed = true,
            remainingStock = 0.0,
        ),
        scheduledAt = at,
        record = null,
        status = status,
    )
}
