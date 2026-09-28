package moe.lizi.kusuri.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.StockEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordDoseUseCaseTest {

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

    @Test
    fun `records once and always cancels the reminder`() = runTest {
        val records = FakeDoseRecordRepository()
        val control = FakeReminderControl()
        val useCase = RecordDoseUseCase(FakeMedicationRepository(medication), records, control)

        assertTrue(useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP))
        assertFalse(useCase.record(1L, scheduledAt, DoseAction.TAKEN, DoseSource.NOTIFICATION))

        assertEquals(1, records.records.size)
        assertEquals(0.5, records.records.single().amount, 0.0)
        assertEquals(listOf(1L, 1L), control.cancelled)
    }

    @Test
    fun `unknown medication is ignored`() = runTest {
        val records = FakeDoseRecordRepository()
        val useCase = RecordDoseUseCase(FakeMedicationRepository(null), records, FakeReminderControl())

        assertFalse(useCase.record(99L, scheduledAt, DoseAction.TAKEN, DoseSource.IN_APP))
        assertTrue(records.records.isEmpty())
    }
}

private class FakeMedicationRepository(private val medication: Medication?) : MedicationRepository {
    override fun observeMedications(): Flow<List<Medication>> = flowOf(listOfNotNull(medication))
    override fun observeMedication(id: Long): Flow<Medication?> =
        flowOf(medication?.takeIf { it.id == id })
    override suspend fun save(medication: Medication): Long = medication.id
    override suspend fun setStatus(id: Long, status: MedicationStatus) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun addStock(id: Long, type: StockEventType, amount: Double) = Unit
}

private class FakeDoseRecordRepository : DoseRecordRepository {
    val records = mutableListOf<DoseRecord>()

    override fun observeScheduledBetween(from: Instant, to: Instant): Flow<List<DoseRecord>> =
        flowOf(records.toList())

    override suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord? =
        records.firstOrNull { it.medicationId == medicationId && it.scheduledAt == scheduledAt }

    override suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
    ) {
        records += DoseRecord(
            id = records.size + 1L,
            medicationId = medicationId,
            scheduledAt = scheduledAt,
            actualAt = Instant.EPOCH,
            amount = amount,
            action = action,
            source = source,
        )
    }
}

private class FakeReminderControl : DoseReminderControl {
    val cancelled = mutableListOf<Long>()
    override fun cancelDose(medicationId: Long) {
        cancelled += medicationId
    }
}
