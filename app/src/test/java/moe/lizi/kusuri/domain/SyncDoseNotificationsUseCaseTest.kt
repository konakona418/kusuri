package moe.lizi.kusuri.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseAlert
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.StockEventType
import moe.lizi.kusuri.domain.schedule.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两条入口的边界(docs/plan.md §4.1、§5):平台叫我们时 [SyncDoseNotificationsUseCase.catchUp]
 * / [SyncDoseNotificationsUseCase.show] 才挂通知;记录之后的
 * [SyncDoseNotificationsUseCase.refresh] 只做减法,绝不重挂。时区固定 +08:00。
 */
class SyncDoseNotificationsUseCaseTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private val morning = medication(1L, "普萘洛尔", LocalTime.of(9, 0))
    private val evening = medication(2L, "丙戊酸镁", LocalTime.of(21, 0))

    private fun useCase(
        records: CatchUpRecordRepository = CatchUpRecordRepository(),
        medications: List<Medication> = listOf(morning, evening),
    ): Pair<SyncDoseNotificationsUseCase, RecordingDoseAlerts> {
        val clock = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), zone)
        val alerts = RecordingDoseAlerts()
        return SyncDoseNotificationsUseCase(
            medicationRepository = CatchUpMedicationRepository(medications),
            doseRecordRepository = records,
            engine = ScheduleEngine(clock) { zone },
            alerts = alerts,
        ) to alerts
    }

    @Test
    fun `a dose that just came due is posted`() = runTest {
        val (useCase, alerts) = useCase()
        val now = Instant.parse("2026-09-29T01:30:00Z") // 09:30,09:00 已到点半小时

        assertEquals(1, useCase.catchUp(now, Duration.ofHours(3), zone))
        assertEquals(listOf(Instant.parse("2026-09-29T01:00:00Z")), alerts.shown)
        assertEquals(1, alerts.pending.last().size)
    }

    @Test
    fun `doses outside the grace window are left alone`() = runTest {
        val (useCase, alerts) = useCase()

        // 距 09:00 已 4 小时,超出 3 小时宽限 → 那已经是"错过",不再补发通知。
        assertEquals(0, useCase.catchUp(Instant.parse("2026-09-29T05:00:00Z"), Duration.ofHours(3), zone))
        assertTrue(alerts.shown.isEmpty())
    }

    @Test
    fun `doses that are already recorded are not posted again`() = runTest {
        val records = CatchUpRecordRepository().apply {
            seeded += DoseRecord(
                id = 1L,
                medicationId = morning.id,
                scheduledAt = Instant.parse("2026-09-29T01:00:00Z"),
                actualAt = Instant.parse("2026-09-29T01:05:00Z"),
                amount = 1.0,
                action = DoseAction.TAKEN,
                source = DoseSource.IN_APP,
            )
        }
        val (useCase, alerts) = useCase(records = records)

        useCase.catchUp(Instant.parse("2026-09-29T01:30:00Z"), Duration.ofHours(3), zone)

        assertTrue("已经服用过,不该再挂通知", alerts.pending.all { it.isEmpty() })
    }

    @Test
    fun `catch up can re-alert when the alarm was a snooze`() = runTest {
        val (useCase, alerts) = useCase()

        useCase.catchUp(
            now = Instant.parse("2026-09-29T01:30:00Z"),
            gracePeriod = Duration.ofHours(3),
            zone = zone,
            alertAgain = true,
        )

        assertTrue("补发默认安静,但\"稍后\"要重新响", alerts.alertAgain.all { it })
    }

    @Test
    fun `refresh only subtracts and never re-shows`() = runTest {
        val (useCase, alerts) = useCase()

        useCase.refresh(scheduledAt = Instant.parse("2026-09-29T01:00:00Z"))

        assertEquals(listOf(Instant.parse("2026-09-29T01:00:00Z")), alerts.refreshed)
        assertTrue("记录之后不该重新挂出任何通知", alerts.shown.isEmpty())
    }

    private fun medication(id: Long, name: String, time: LocalTime) = Medication(
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
        schedule = Schedule.DailyTimes(listOf(time)),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )
}

private class CatchUpMedicationRepository(private val medications: List<Medication>) : MedicationRepository {
    override fun observeMedications(): Flow<List<Medication>> = flowOf(medications)
    override fun observeMedication(id: Long): Flow<Medication?> =
        flowOf(medications.firstOrNull { it.id == id })

    override suspend fun save(medication: Medication): Long = medication.id
    override suspend fun setStatus(id: Long, status: MedicationStatus) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun addStock(id: Long, type: StockEventType, amount: Double) = Unit
    override suspend fun setStockAlertArmed(id: Long, armed: Boolean) = Unit
}

private class CatchUpRecordRepository : DoseRecordRepository {
    val seeded = mutableListOf<DoseRecord>()

    override fun observeRecordsBetween(from: Instant, to: Instant): Flow<List<DoseRecord>> =
        flowOf(seeded.toList())

    override suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord? =
        seeded.firstOrNull { it.medicationId == medicationId && it.scheduledAt == scheduledAt }

    override suspend fun lastTaken(medicationId: Long): DoseRecord? = null
    override suspend fun countTaken(medicationId: Long, from: Instant, to: Instant): Int = 0
    override suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
        actualAt: Instant?,
    ) = Unit

    override suspend fun update(recordId: Long, actualAt: Instant, action: DoseAction) = Unit
    override suspend fun delete(recordId: Long) = Unit
}

private class RecordingDoseAlerts : DoseAlertControl {
    val shown = mutableListOf<Instant>()
    val refreshed = mutableListOf<Instant>()
    val pending = mutableListOf<List<DoseAlert>>()
    val alertAgain = mutableListOf<Boolean>()

    override fun show(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
        alertAgain: Boolean,
    ) {
        shown += scheduledAt
        this.pending += pending
        this.alertAgain += alertAgain
    }

    override fun refresh(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
    ) {
        refreshed += scheduledAt
    }

    override fun cancel(medicationId: Long) = Unit
    override fun cancelGroup(scheduledAt: Instant) = Unit
}
