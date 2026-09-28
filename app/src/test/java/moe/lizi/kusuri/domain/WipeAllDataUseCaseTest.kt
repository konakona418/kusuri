package moe.lizi.kusuri.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.StockEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WipeAllDataUseCaseTest {

    private fun medication(id: Long) = Medication(
        id = id,
        name = "药$id",
        unit = "粒",
        defaultDose = 1.0,
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
    fun `cancels reminders for every medication before wiping`() = runTest {
        val reminders = RecordingReminderControl()
        val wiper = RecordingDataWiper()

        WipeAllDataUseCase(
            medicationRepository = WipeFakeMedicationRepository(listOf(medication(1L), medication(2L))),
            reminderControl = reminders,
            dataWiper = wiper,
        ).wipe()

        assertEquals(listOf(1L, 2L), reminders.cancelledAll)
        assertTrue(wiper.wiped)
    }

    @Test
    fun `still wipes when there is nothing to cancel`() = runTest {
        val wiper = RecordingDataWiper()

        WipeAllDataUseCase(
            medicationRepository = WipeFakeMedicationRepository(emptyList()),
            reminderControl = RecordingReminderControl(),
            dataWiper = wiper,
        ).wipe()

        assertTrue(wiper.wiped)
    }
}

private class WipeFakeMedicationRepository(private val medications: List<Medication>) : MedicationRepository {
    override fun observeMedications(): Flow<List<Medication>> = flowOf(medications)
    override fun observeMedication(id: Long): Flow<Medication?> =
        flowOf(medications.firstOrNull { it.id == id })

    override suspend fun save(medication: Medication): Long = medication.id
    override suspend fun setStatus(id: Long, status: MedicationStatus) = Unit
    override suspend fun delete(id: Long) = Unit
    override suspend fun addStock(id: Long, type: StockEventType, amount: Double) = Unit
    override suspend fun setStockAlertArmed(id: Long, armed: Boolean) = Unit
}

private class RecordingReminderControl : DoseReminderControl {
    val cancelled = mutableListOf<Long>()
    val cancelledAll = mutableListOf<Long>()

    override fun cancelDose(medicationId: Long) {
        cancelled += medicationId
    }

    override fun cancelAllFor(medicationId: Long) {
        cancelledAll += medicationId
    }
}

private class RecordingDataWiper : DataWiper {
    var wiped = false
        private set

    override suspend fun wipeAll() {
        wiped = true
    }
}
