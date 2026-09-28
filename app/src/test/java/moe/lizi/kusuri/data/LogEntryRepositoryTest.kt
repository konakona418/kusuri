package moe.lizi.kusuri.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.MedicationEntity
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogEntryRepositoryTest {

    private lateinit var db: KusuriDatabase
    private lateinit var repository: RoomLogEntryRepository
    private lateinit var medicationRepository: RoomMedicationRepository
    private val at = Instant.parse("2026-09-28T02:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KusuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLogEntryRepository(db)
        medicationRepository = RoomMedicationRepository(db, java.time.Clock.systemDefaultZone())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun symptom(
        name: String = "恶心",
        severity: Int = 4,
        at: Instant = this.at,
        medicationId: Long? = null,
        note: String? = null,
    ) = LogEntry(
        type = LogEntryType.SYMPTOM,
        at = at,
        symptom = name,
        severity = severity,
        medicationId = medicationId,
        note = note,
    )

    @Test
    fun `symptom and note entries round trip newest first`() = runTest {
        repository.save(symptom(note = "饭后"))
        repository.save(
            LogEntry(
                type = LogEntryType.NOTE,
                at = at.plusSeconds(60),
                symptom = null,
                severity = null,
                medicationId = null,
                note = "今天精神不错",
            ),
        )

        val entries = repository.observeBetween(at.minusSeconds(60), at.plusSeconds(3600)).first()

        assertEquals(2, entries.size)
        assertEquals(LogEntryType.NOTE, entries.first().type)
        assertEquals("今天精神不错", entries.first().note)
        assertEquals("恶心", entries.last().symptom)
        assertEquals(4, entries.last().severity)
    }

    @Test
    fun `entries outside the window are excluded`() = runTest {
        repository.save(symptom())

        val entries = repository.observeBetween(at.plusSeconds(60), at.plusSeconds(3600)).first()

        assertEquals(0, entries.size)
    }

    @Test
    fun `deleting a medication keeps its logs but clears the link`() = runTest {
        val medicationId = db.medicationDao().insertMedication(
            MedicationEntity(
                name = "布洛芬",
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
        repository.save(symptom(name = "皮疹", severity = 2, medicationId = medicationId))

        medicationRepository.delete(medicationId)

        val entry = repository.observeBetween(at.minusSeconds(60), at.plusSeconds(60)).first().single()
        assertEquals("皮疹", entry.symptom)
        assertNull(entry.medicationId)
    }

    @Test
    fun `recent symptoms are deduped newest first and limited`() = runTest {
        repository.save(symptom(name = "恶心", at = at))
        repository.save(symptom(name = "头晕", at = at.plusSeconds(60)))
        repository.save(symptom(name = "恶心", at = at.plusSeconds(120)))
        repository.save(symptom(name = "乏力", at = at.plusSeconds(180)))

        assertEquals(listOf("乏力", "恶心", "头晕"), repository.recentSymptoms(limit = 3))
    }

    @Test
    fun `entries can be updated and deleted`() = runTest {
        val id = repository.save(symptom())
        val saved = repository.observeBetween(at.minusSeconds(60), at.plusSeconds(60)).first().single()

        repository.save(saved.copy(id = id, severity = 2))
        assertEquals(
            2,
            repository.observeBetween(at.minusSeconds(60), at.plusSeconds(60)).first().single().severity,
        )

        repository.delete(id)
        assertEquals(0, repository.observeBetween(at.minusSeconds(60), at.plusSeconds(60)).first().size)
    }
}
