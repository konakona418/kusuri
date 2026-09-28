package moe.lizi.kusuri.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.data.db.DoseRecordEntity
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.domain.model.IntervalUnit
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.StockEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomMedicationRepositoryTest {

    private lateinit var db: KusuriDatabase
    private lateinit var repository: RoomMedicationRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KusuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMedicationRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun medication(
        name: String = "二甲双胍",
        schedule: Schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
    ) = Medication(
        id = 0L,
        name = name,
        unit = "粒",
        defaultDose = 0.5,
        mealTag = MealTag.NONE,
        notes = null,
        status = MedicationStatus.ACTIVE,
        createdAt = clock.instant(),
        courseStart = LocalDate.of(2026, 9, 28),
        courseEnd = null,
        schedule = schedule,
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )

    @Test
    fun `save and observe roundtrips medication with daily times`() = runTest {
        val id = repository.save(medication())

        val loaded = repository.observeMedications().first().single()

        assertEquals(id, loaded.id)
        assertEquals("二甲双胍", loaded.name)
        assertEquals("粒", loaded.unit)
        assertEquals(0.5, loaded.defaultDose, 0.0)
        assertEquals(MedicationStatus.ACTIVE, loaded.status)
        assertEquals(
            Schedule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))),
            loaded.schedule,
        )
    }

    @Test
    fun `interval and prn schedules roundtrip`() = runTest {
        val intervalId = repository.save(
            medication(
                name = "阿奇霉素",
                schedule = Schedule.Interval(
                    every = 2,
                    unit = IntervalUnit.DAYS,
                    anchor = LocalDateTime.of(2026, 10, 1, 9, 30),
                ),
            ),
        )
        val prnId = repository.save(
            medication(
                name = "布洛芬",
                schedule = Schedule.Prn(minIntervalMinutes = 360, maxPerDay = 4),
            ),
        )

        val interval = repository.observeMedication(intervalId).first()!!
        val prn = repository.observeMedication(prnId).first()!!

        assertEquals(
            Schedule.Interval(2, IntervalUnit.DAYS, LocalDateTime.of(2026, 10, 1, 9, 30)),
            interval.schedule,
        )
        assertEquals(Schedule.Prn(minIntervalMinutes = 360, maxPerDay = 4), prn.schedule)
    }

    @Test
    fun `update replaces schedule times without duplicates`() = runTest {
        val id = repository.save(medication())

        repository.save(medication().copy(id = id, schedule = Schedule.Prn(null, null)))
        repository.save(medication().copy(id = id, schedule = Schedule.DailyTimes(listOf(LocalTime.of(7, 30)))))

        val reloaded = repository.observeMedication(id).first()!!
        assertEquals(Schedule.DailyTimes(listOf(LocalTime.of(7, 30))), reloaded.schedule)
    }

    @Test
    fun `archived medications stay in the full list`() = runTest {
        val id = repository.save(medication())

        repository.setStatus(id, MedicationStatus.ARCHIVED)

        val all = repository.observeMedications().first()
        assertEquals(1, all.size)
        assertEquals(MedicationStatus.ARCHIVED, all.single().status)
        assertEquals(MedicationStatus.ARCHIVED, repository.observeMedication(id).first()!!.status)
    }

    @Test
    fun `deleted medications are gone`() = runTest {
        val id = repository.save(medication())

        repository.delete(id)

        assertNull(repository.observeMedication(id).first())
        assertTrue(repository.observeMedications().first().isEmpty())
    }

    @Test
    fun `remaining stock is derived from events minus taken records`() = runTest {
        val id = repository.save(medication())
        repository.addStock(id, StockEventType.REFILL, 30.0)
        repository.addStock(id, StockEventType.REFILL, 10.0)

        db.medicationDao().insertDoseRecord(takenRecord(id, amount = 1.0))
        val skipped = takenRecord(id, amount = 1.0).copy(action = "SKIPPED")
        db.medicationDao().insertDoseRecord(skipped)

        val loaded = repository.observeMedication(id).first()!!
        assertEquals(39.0, loaded.remainingStock, 0.0)
        assertEquals(39.0, repository.observeMedications().first().single().remainingStock, 0.0)
    }

    private fun takenRecord(medicationId: Long, amount: Double) = DoseRecordEntity(
        medicationId = medicationId,
        scheduledAt = null,
        actualAt = clock.millis(),
        amount = amount,
        action = "TAKEN",
        source = "IN_APP",
    )
}
