package moe.lizi.kusuri.domain.history

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 历史页筛选(docs/plan.md §7):时间范围、类型、药物、状态各管一维,互不牵连。 */
class HistoryFilterTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val earlier = today.minusDays(9)
    private val at9 = Instant.parse("2026-09-29T09:00:00Z")

    private val propranolol = medication(1L, "普萘洛尔")
    private val valproate = medication(2L, "丙戊酸钠")

    /** 今天:两味药(一服一漏)+ 一条关联普萘洛尔的症状 + 一条随手记。 */
    private val day29 = HistoryDay(
        date = today,
        doses = listOf(
            dose(propranolol, at9, DoseStatus.Taken(at9, DoseSource.IN_APP)),
            dose(valproate, at9, DoseStatus.Missed),
        ),
        logs = listOf(
            symptom("头晕", today, medicationId = propranolol.id),
            note("今天有点累", today),
        ),
    )

    /** 更早的一天:只有一味待服用的药,没有日志。 */
    private val dayEarlier = HistoryDay(
        date = earlier,
        doses = listOf(dose(propranolol, at9, DoseStatus.Pending)),
        logs = emptyList(),
    )

    private val timeline = HistoryTimeline(
        today = today,
        days = listOf(day29, dayEarlier),
        adherence7 = AdherenceSummary(taken = 0, resolved = 0),
        adherence30 = AdherenceSummary(taken = 0, resolved = 0),
        medicationNames = mapOf(1L to "普萘洛尔", 2L to "丙戊酸钠"),
    )

    @Test
    fun `默认是近 30 天且不限制任何维度`() {
        val days = timeline.filteredDays(HistoryFilter())

        assertEquals(listOf(today, earlier), days.map { it.date })
        assertEquals(2, days[0].doses.size)
        assertEquals(2, days[0].logs.size)
    }

    @Test
    fun `时间范围只影响日期`() {
        val onlyToday = timeline.filteredDays(HistoryFilter(range = HistoryRange.TODAY))
        assertEquals(listOf(today), onlyToday.map { it.date })

        val custom = timeline.filteredDays(
            HistoryFilter(
                range = HistoryRange.CUSTOM,
                customFrom = earlier,
                customTo = earlier,
            ),
        )
        assertEquals("自定义到哪一天就到哪一天", listOf(earlier), custom.map { it.date })
    }

    @Test
    fun `只看症状时服药行与随手记都消失`() {
        val days = timeline.filteredDays(HistoryFilter(types = setOf(HistoryType.SYMPTOM)))

        assertEquals(listOf(today), days.map { it.date })
        assertTrue("服药行被滤掉", days[0].doses.isEmpty())
        assertEquals(listOf("头晕"), days[0].logs.map { it.symptom })
    }

    @Test
    fun `只看服药时日志消失但整天不会消失`() {
        val days = timeline.filteredDays(HistoryFilter(types = setOf(HistoryType.DOSE)))

        assertEquals(listOf(today, earlier), days.map { it.date })
        assertEquals(2, days[0].doses.size)
        assertTrue(days[0].logs.isEmpty())
    }

    @Test
    fun `按药物过滤会同时作用于服药行与关联的日志`() {
        val days = timeline.filteredDays(HistoryFilter(medicationId = valproate.id))

        assertEquals(listOf(today), days.map { it.date })
        assertEquals(listOf("丙戊酸钠"), days[0].doses.map { it.medication.name })
        assertTrue("关联的是普萘洛尔,跟着一起被滤掉", days[0].logs.isEmpty())
    }

    @Test
    fun `未处理只留没有记录的`() {
        val days = timeline.filteredDays(HistoryFilter(statuses = setOf(HistoryStatus.UNHANDLED)))

        // 状态只管服药行:日志不受影响,所以今天只剩日志、更早那天只剩那味待服的药。
        assertEquals(listOf(today, earlier), days.map { it.date })
        assertTrue("今天的药一服一漏,都不算未处理", days[0].doses.isEmpty())
        assertEquals(2, days[0].logs.size)
        assertEquals(DoseStatus.Pending, days[1].doses.single().status)
    }

    @Test
    fun `已错过只留错过的那一条`() {
        val days = timeline.filteredDays(HistoryFilter(statuses = setOf(HistoryStatus.MISSED)))

        assertEquals(listOf(today), days.map { it.date })
        assertEquals(listOf("丙戊酸钠"), days[0].doses.map { it.medication.name })
    }

    @Test
    fun `空集合与默认值表示不限制`() {
        val filter = HistoryFilter(types = HistoryType.entries.toSet(), statuses = emptySet())
        assertEquals(2, timeline.filteredDays(filter).size)
        assertTrue(filter.isDefault)
    }

    private fun medication(id: Long, name: String) = Medication(
        id = id,
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
    )

    private fun dose(medication: Medication, at: Instant, status: DoseStatus) =
        HistoryDose(medication = medication, scheduledAt = at, record = null, status = status)

    private fun symptom(text: String, date: LocalDate, medicationId: Long?) = LogEntry(
        id = text.hashCode().toLong(),
        type = LogEntryType.SYMPTOM,
        at = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
        symptom = text,
        severity = 3,
        medicationId = medicationId,
        note = null,
    )

    private fun note(text: String, date: LocalDate) = LogEntry(
        id = text.hashCode().toLong(),
        type = LogEntryType.NOTE,
        at = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
        symptom = null,
        severity = null,
        medicationId = null,
        note = text,
    )
}
