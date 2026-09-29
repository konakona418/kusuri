package moe.lizi.kusuri.domain

import java.time.Clock
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDoseUseCaseTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-09-28T00:30:00Z"), zone)
    private val scheduledAt = Instant.parse("2026-09-28T00:00:00Z")
    private val medication = Medication(
        id = 1L,
        name = "二甲双胍",
        unit = "粒",
        defaultDose = 0.5,
        mealTag = MealTag.NONE,
        notes = null,
        status = MedicationStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        courseStart = LocalDate.of(2026, 9, 1),
        courseEnd = null,
        schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0))),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )

    private fun useCase(
        records: FakeDoseRecordRepository = FakeDoseRecordRepository(),
        reminderControl: FakeReminderControl = FakeReminderControl(),
        lowStockControl: FakeLowStockControl = FakeLowStockControl(),
        medicationRepository: FakeMedicationRepository = FakeMedicationRepository(medication),
        alerts: FakeDoseAlerts = FakeDoseAlerts(),
    ) = RecordDoseUseCase(
        medicationRepository = medicationRepository,
        doseRecordRepository = records,
        reminderControl = reminderControl,
        checkLowStock = CheckLowStockUseCase(medicationRepository, lowStockControl),
        syncNotifications = SyncDoseNotificationsUseCase(
            medicationRepository = medicationRepository,
            doseRecordRepository = records,
            engine = ScheduleEngine(clock) { zone },
            alerts = alerts,
        ),
        clock = clock,
    )

    @Test
    fun `records once and always cancels the reminder`() = runTest {
        val records = FakeDoseRecordRepository()
        val control = FakeReminderControl()
        val useCase = useCase(records = records, reminderControl = control)

        assertTrue(useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP))
        assertFalse(useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.NOTIFICATION))

        assertEquals(1, records.records.size)
        assertEquals(0.5, records.records.single().amount, 0.0)
        assertEquals(listOf(1L, 1L), control.cancelled)
    }

    @Test
    fun `backfill records taken with explicit time and backfill source`() = runTest {
        val records = FakeDoseRecordRepository()
        val actualAt = Instant.parse("2026-09-28T06:00:00Z")
        val useCase = useCase(records = records)

        assertTrue(useCase.backfill(1L, scheduledAt, actualAt))

        val record = records.records.single()
        assertEquals(actualAt, record.actualAt)
        assertEquals(DoseAction.TAKEN, record.action)
        assertEquals(DoseSource.BACKFILL, record.source)
    }

    @Test
    fun `prn records have no planned time and use the given amount`() = runTest {
        val records = FakeDoseRecordRepository()
        val useCase = useCase(records = records)
        val actualAt = Instant.parse("2026-09-28T06:00:00Z")

        assertTrue(useCase.recordPrn(1L, amount = 2.0, actualAt = actualAt))

        val record = records.records.single()
        assertNull(record.scheduledAt)
        assertEquals(2.0, record.amount, 0.0)
        assertEquals(actualAt, record.actualAt)
        assertEquals(DoseAction.TAKEN, record.action)
    }

    @Test
    fun `recording a dose re-checks low stock`() = runTest {        val lowStockControl = FakeLowStockControl()
        val useCase = useCase(lowStockControl = lowStockControl)

        useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP)

        assertEquals(listOf(medication), lowStockControl.notified)
    }

    @Test
    fun `low stock alerts once and then disarms`() = runTest {
        val medicationRepository = FakeMedicationRepository(medication)
        val lowStockControl = FakeLowStockControl()
        val check = CheckLowStockUseCase(medicationRepository, lowStockControl)

        check.check(1L)
        check.check(1L)

        assertEquals(listOf(medication), lowStockControl.notified)
        assertTrue(medicationRepository.armedChanges.contains(1L to false))
    }

    @Test
    fun `initialize only arms medications that track stock`() = runTest {
        // 剩余 0 视为"从未录入库存":不武装,避免凭空打扰。
        val untracked = FakeMedicationRepository(medication)
        CheckLowStockUseCase(untracked, FakeLowStockControl()).initialize(1L)
        assertTrue(untracked.armedChanges.contains(1L to false))

        val tracked = FakeMedicationRepository(medication.copy(remainingStock = 30.0))
        CheckLowStockUseCase(tracked, FakeLowStockControl()).initialize(1L)
        assertTrue(tracked.armedChanges.contains(1L to true))
    }

    @Test
    fun `unknown medication is ignored`() = runTest {
        val records = FakeDoseRecordRepository()
        val useCase = useCase(records = records, medicationRepository = FakeMedicationRepository(null))

        assertFalse(useCase.record(99L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP))
        assertTrue(records.records.isEmpty())
    }

    @Test
    fun `recording rebuilds the notification of that instant`() = runTest {
        // 同一时刻可能还有别的药:记录一条之后必须重建那一刻的通知(组会缩小)。
        val alerts = FakeDoseAlerts()
        val useCase = useCase(alerts = alerts)

        useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP)

        assertEquals(listOf(scheduledAt), alerts.synced)
    }
}

private class FakeMedicationRepository(private var medication: Medication?) : MedicationRepository {
    val armedChanges = mutableListOf<Pair<Long, Boolean>>()

    override fun observeMedications(): Flow<List<Medication>> = flowOf(listOfNotNull(medication))
    override fun observeMedication(id: Long): Flow<Medication?> =
        flowOf(medication?.takeIf { it.id == id })
    override suspend fun save(medication: Medication): Long = medication.id
    override suspend fun setStatus(id: Long, status: MedicationStatus) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun addStock(id: Long, type: StockEventType, amount: Double) = Unit
    override suspend fun setStockAlertArmed(id: Long, armed: Boolean) {
        armedChanges += id to armed
        medication = medication?.copy(stockAlertArmed = armed)
    }
}

private class FakeDoseRecordRepository : DoseRecordRepository {
    val records = mutableListOf<DoseRecord>()

    override fun observeRecordsBetween(from: Instant, to: Instant): Flow<List<DoseRecord>> =
        flowOf(records.toList())

    override suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord? =
        records.firstOrNull { it.medicationId == medicationId && it.scheduledAt == scheduledAt }

    override suspend fun lastTaken(medicationId: Long): DoseRecord? =
        records.filter { it.medicationId == medicationId && it.action == DoseAction.TAKEN }
            .maxByOrNull { it.actualAt }

    override suspend fun countTaken(medicationId: Long, from: Instant, to: Instant): Int =
        records.count {
            it.medicationId == medicationId &&
                it.action == DoseAction.TAKEN &&
                it.actualAt >= from &&
                it.actualAt < to
        }

    override suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
        actualAt: Instant?,
    ) {
        records += DoseRecord(
            id = records.size + 1L,
            medicationId = medicationId,
            scheduledAt = scheduledAt,
            actualAt = actualAt ?: Instant.EPOCH,
            amount = amount,
            action = action,
            source = source,
        )
    }

    override suspend fun update(recordId: Long, actualAt: Instant, action: DoseAction) {
        val index = records.indexOfFirst { it.id == recordId }
        if (index >= 0) records[index] = records[index].copy(actualAt = actualAt, action = action)
    }

    override suspend fun delete(recordId: Long) {
        records.removeAll { it.id == recordId }
    }
}

private class FakeReminderControl : DoseReminderControl {
    val cancelled = mutableListOf<Long>()
    override fun cancelDose(medicationId: Long) {
        cancelled += medicationId
    }

    override fun cancelAllFor(medicationId: Long) {
        cancelled += medicationId
    }
}

private class FakeLowStockControl : LowStockAlertControl {
    val notified = mutableListOf<Medication>()
    override fun notifyLowStock(medication: Medication) {
        notified += medication
    }
}

private class FakeDoseAlerts : DoseAlertControl {
    val synced = mutableListOf<Instant>()
    val cancelled = mutableListOf<Long>()

    override fun sync(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
        alertAgain: Boolean,
    ) {
        synced += scheduledAt
    }

    override fun cancel(medicationId: Long) {
        cancelled += medicationId
    }

    override fun cancelGroup(scheduledAt: Instant) = Unit
}
