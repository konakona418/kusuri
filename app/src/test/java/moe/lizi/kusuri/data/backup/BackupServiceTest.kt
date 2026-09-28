package moe.lizi.kusuri.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import moe.lizi.kusuri.data.RoomMedicationRepository
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Schedule
import moe.lizi.kusuri.domain.model.StockEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BackupServiceTest {

    private lateinit var db: KusuriDatabase
    private lateinit var service: BackupService
    private lateinit var medicationRepository: RoomMedicationRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-28T02:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KusuriDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = BackupService(context, db, clock)
        medicationRepository = RoomMedicationRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun medication(name: String = "二甲双胍") = Medication(
        name = name,
        unit = "粒",
        defaultDose = 0.5,
        mealTag = MealTag.BEFORE,
        notes = null,
        status = MedicationStatus.ACTIVE,
        createdAt = clock.instant(),
        courseStart = LocalDate.of(2026, 9, 1),
        courseEnd = null,
        schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0))),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )

    @Test
    fun `export then import restores the same data`() = runTest {
        val id = medicationRepository.save(medication())
        medicationRepository.addStock(id, StockEventType.INITIAL, 30.0)
        db.doseRecordDao().insert(
            moe.lizi.kusuri.data.db.DoseRecordEntity(
                medicationId = id,
                scheduledAt = clock.millis(),
                actualAt = clock.millis(),
                amount = 0.5,
                action = "TAKEN",
                source = "IN_APP",
            ),
        )
        val backup = service.exportJson()

        // 破坏现状后再恢复
        medicationRepository.delete(id)
        assertTrue(medicationRepository.observeMedications().first().isEmpty())

        val imported = service.importJson(backup)

        assertEquals(1, imported)
        val restored = medicationRepository.observeMedications().first().single()
        assertEquals("二甲双胍", restored.name)
        assertEquals(29.5, restored.remainingStock, 0.0)
        assertEquals(1, db.doseRecordDao().getAll().size)
    }

    @Test
    fun `import replaces existing data`() = runTest {
        medicationRepository.save(medication(name = "旧药"))
        val backup = run {
            medicationRepository.delete(medicationRepository.observeMedications().first().single().id)
            val id = medicationRepository.save(medication(name = "新药"))
            medicationRepository.addStock(id, StockEventType.INITIAL, 10.0)
            service.exportJson()
        }

        service.importJson(backup)

        val names = medicationRepository.observeMedications().first().map { it.name }
        assertEquals(listOf("新药"), names)
    }

    @Test
    fun `csv export contains localized rows within the window`() = runTest {
        val id = medicationRepository.save(medication())
        db.doseRecordDao().insert(
            moe.lizi.kusuri.data.db.DoseRecordEntity(
                medicationId = id,
                scheduledAt = clock.millis(),
                actualAt = clock.millis(),
                amount = 0.5,
                action = DoseAction.TAKEN.name,
                source = DoseSource.IN_APP.name,
            ),
        )
        val labels = CsvLabels(
            header = listOf("药物", "剂量", "计划时间", "实际时间", "状态", "来源"),
            taken = "已服用",
            skipped = "已跳过",
            sourceInApp = "应用内",
            sourceNotification = "通知",
            sourceBackfill = "补记",
        )

        val csv = service.exportCsv(
            from = clock.instant().minusSeconds(60),
            to = clock.instant().plusSeconds(60),
            labels = labels,
        )

        assertTrue(csv.startsWith("药物,剂量,计划时间,实际时间,状态,来源\r\n"))
        assertTrue(csv.contains("二甲双胍,0.5 粒"))
        assertTrue(csv.contains("已服用"))
        assertTrue(csv.contains("应用内"))
    }
}
