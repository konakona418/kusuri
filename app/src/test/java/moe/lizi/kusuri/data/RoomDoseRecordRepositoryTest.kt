package moe.lizi.kusuri.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.MedicationEntity
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomDoseRecordRepositoryTest {

    private lateinit var db: KusuriDatabase
    private lateinit var repository: RoomDoseRecordRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val scheduledAt = Instant.parse("2026-09-28T00:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KusuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomDoseRecordRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertMedication() {
        db.medicationDao().insertMedication(
            MedicationEntity(
                id = 1L,
                name = "二甲双胍",
                unit = "粒",
                defaultDose = 1.0,
                mealTag = "NONE",
                notes = null,
                status = "ACTIVE",
                createdAt = 0L,
                courseStart = "2026-09-01",
                courseEnd = null,
                scheduleMode = "PRN",
                intervalEvery = null,
                intervalUnit = null,
                intervalAnchor = null,
                prnMinIntervalMinutes = null,
                prnMaxPerDay = null,
                lowStockThreshold = 5.0,
                stockAlertArmed = true,
            ),
        )
    }

    @Test
    fun `recorded doses are observed inside the window`() = runTest {
        insertMedication()
        repository.record(
            medicationId = 1L,
            scheduledAt = scheduledAt,
            amount = 0.5,
            action = DoseAction.TAKEN,
            source = DoseSource.IN_APP,
        )

        val record = repository
            .observeRecordsBetween(scheduledAt.minusSeconds(60), scheduledAt.plusSeconds(60))
            .first()
            .single()

        assertEquals(1L, record.medicationId)
        assertEquals(scheduledAt, record.scheduledAt)
        assertEquals(clock.instant(), record.actualAt)
        assertEquals(0.5, record.amount, 0.0)
        assertEquals(DoseAction.TAKEN, record.action)
        assertEquals(DoseSource.IN_APP, record.source)
    }

    @Test
    fun `records outside the window are excluded`() = runTest {
        insertMedication()
        repository.record(1L, scheduledAt, 1.0, DoseAction.TAKEN, DoseSource.NOTIFICATION)

        val records = repository
            .observeRecordsBetween(scheduledAt.plusSeconds(1), scheduledAt.plusSeconds(3600))
            .first()

        assertTrue(records.isEmpty())
    }

    @Test
    fun `find by scheduled returns the matching record`() = runTest {
        insertMedication()
        repository.record(1L, scheduledAt, 1.0, DoseAction.SKIPPED, DoseSource.IN_APP)

        assertEquals(DoseAction.SKIPPED, repository.findByScheduled(1L, scheduledAt)!!.action)
        assertNull(repository.findByScheduled(2L, scheduledAt))
    }

    @Test
    fun `last taken ignores skips and older records`() = runTest {
        insertMedication()
        val earlier = scheduledAt
        val later = scheduledAt.plusSeconds(3600)
        repository.record(1L, earlier, 1.0, DoseAction.TAKEN, DoseSource.IN_APP, actualAt = earlier)
        repository.record(1L, later, 1.0, DoseAction.SKIPPED, DoseSource.IN_APP, actualAt = later)

        assertEquals(earlier, repository.lastTaken(1L)!!.actualAt)
        assertNull(repository.lastTaken(99L))
    }

    @Test
    fun `explicit actual time is used for backfill`() = runTest {
        insertMedication()
        val actualAt = Instant.parse("2026-09-28T05:00:00Z")

        repository.record(1L, scheduledAt, 0.5, DoseAction.TAKEN, DoseSource.BACKFILL, actualAt = actualAt)

        assertEquals(actualAt, repository.findByScheduled(1L, scheduledAt)!!.actualAt)
    }

    @Test
    fun `a second record for the same planned dose is ignored`() = runTest {
        insertMedication()

        repository.record(1L, scheduledAt, 1.0, DoseAction.TAKEN, DoseSource.IN_APP)
        repository.record(1L, scheduledAt, 1.0, DoseAction.SKIPPED, DoseSource.NOTIFICATION)

        val records = repository
            .observeRecordsBetween(scheduledAt.minusSeconds(1), scheduledAt.plusSeconds(1))
            .first()
        assertEquals(1, records.size)
        assertEquals(DoseAction.TAKEN, records.single().action)
    }

    @Test
    fun `records can be updated and deleted`() = runTest {
        insertMedication()
        repository.record(1L, scheduledAt, 1.0, DoseAction.SKIPPED, DoseSource.IN_APP)
        val record = repository.findByScheduled(1L, scheduledAt)!!
        val actualAt = Instant.parse("2026-09-28T05:30:00Z")

        repository.update(record.id, actualAt = actualAt, action = DoseAction.TAKEN)

        val updated = repository.findByScheduled(1L, scheduledAt)!!
        assertEquals(actualAt, updated.actualAt)
        assertEquals(DoseAction.TAKEN, updated.action)

        repository.delete(record.id)

        assertNull(repository.findByScheduled(1L, scheduledAt))
    }
}
