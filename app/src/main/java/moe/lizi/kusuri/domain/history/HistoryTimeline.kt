package moe.lizi.kusuri.domain.history

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import moe.lizi.kusuri.domain.model.DOSE_GRACE_PERIOD
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.doseStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

data class HistoryDose(
    val medication: Medication,
    val scheduledAt: Instant,
    val record: DoseRecord?,
    val status: DoseStatus,
)

data class HistoryDay(val date: LocalDate, val doses: List<HistoryDose>)

data class HistoryTimeline(
    val today: LocalDate,
    val days: List<HistoryDay>,
    val adherence7: AdherenceSummary,
    val adherence30: AdherenceSummary,
) {
    companion object {
        val Empty = HistoryTimeline(
            today = LocalDate.EPOCH,
            days = emptyList(),
            adherence7 = AdherenceSummary(taken = 0, resolved = 0),
            adherence30 = AdherenceSummary(taken = 0, resolved = 0),
        )
    }
}

/**
 * 把"药物排程 + 服药记录"投影成历史时间线。
 *
 * 两条原则(docs/plan.md §3):
 * 1. 记录是唯一事实源:与计划对不上的记录(改过排程、或药物已归档)仍然出现在时间线里,可修改可撤销。
 * 2. 归档药物不再向前展开排程,避免虚构出大量"错过"稀释遵守率;它们的记录仍会显示。
 *
 * [windowDays] 目前固定为 30(遵守率展示近 7 / 近 30 天)。
 */
fun buildHistoryTimeline(
    medications: List<Medication>,
    records: List<DoseRecord>,
    today: LocalDate,
    now: Instant,
    engine: ScheduleEngine,
    windowDays: Int = 30,
    gracePeriod: Duration = DOSE_GRACE_PERIOD,
): HistoryTimeline {
    val windowStart = today.minusDays(windowDays - 1L)
    val medicationsById = medications.associateBy { it.id }
    val recordsByDose = records
        .filter { it.scheduledAt != null }
        .associateBy { it.medicationId to it.scheduledAt }
    val dosesByDate = mutableMapOf<LocalDate, MutableList<HistoryDose>>()
    val matchedKeys = mutableSetOf<Pair<Long, Instant>>()

    val scheduledMedications = medications.filter { it.status != MedicationStatus.ARCHIVED }
    var date = today
    var scanned = 0
    while (scanned < windowDays) {
        scheduledMedications.forEach { medication ->
            engine.plannedDosesOn(medication, date).forEach { scheduledAt ->
                val key = medication.id to scheduledAt
                matchedKeys += key
                val record = recordsByDose[key]
                dosesByDate.getOrPut(date) { mutableListOf() } += HistoryDose(
                    medication = medication,
                    scheduledAt = scheduledAt,
                    record = record,
                    status = doseStatus(record, scheduledAt, now, gracePeriod),
                )
            }
        }
        date = date.minusDays(1)
        scanned++
    }

    records.forEach { record ->
        val scheduledAt = record.scheduledAt ?: return@forEach
        if ((record.medicationId to scheduledAt) in matchedKeys) return@forEach
        val medication = medicationsById[record.medicationId] ?: return@forEach
        val recordDate = engine.dateOf(scheduledAt)
        if (recordDate.isBefore(windowStart) || recordDate.isAfter(today)) return@forEach
        dosesByDate.getOrPut(recordDate) { mutableListOf() } += HistoryDose(
            medication = medication,
            scheduledAt = scheduledAt,
            record = record,
            status = doseStatus(record, scheduledAt, now, gracePeriod),
        )
    }

    val days = dosesByDate.entries
        .sortedByDescending { it.key }
        .map { (day, doses) -> HistoryDay(date = day, doses = doses.sortedBy { it.scheduledAt }) }

    val last7 = days
        .filter { !it.date.isBefore(today.minusDays(6)) }
        .flatMap { it.doses }
        .map { it.status }
    val last30 = days.flatMap { it.doses }.map { it.status }

    return HistoryTimeline(
        today = today,
        days = days,
        adherence7 = adherenceRate(last7),
        adherence30 = adherenceRate(last30),
    )
}
