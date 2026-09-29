package moe.lizi.kusuri.data.backup

import moe.lizi.kusuri.data.db.DoseRecordEntity
import moe.lizi.kusuri.data.db.LogEntryEntity
import moe.lizi.kusuri.data.db.MedicationEntity
import moe.lizi.kusuri.data.db.MedicationTimeEntity
import moe.lizi.kusuri.data.db.ReminderEntity
import moe.lizi.kusuri.data.db.StockEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupJsonTest {

    private fun payload() = BackupPayload(
        formatVersion = BackupJson.FORMAT_VERSION,
        exportedAt = 1_700_000_000_000L,
        medications = listOf(
            MedicationEntity(
                id = 1L,
                name = "二甲双胍",
                unit = "粒",
                defaultDose = 0.5,
                mealTag = "BEFORE",
                notes = "随餐",
                status = "ACTIVE",
                createdAt = 123L,
                courseStart = "2026-09-01",
                courseEnd = "2026-10-01",
                scheduleMode = "INTERVAL",
                intervalEvery = 8,
                intervalUnit = "HOURS",
                intervalAnchor = "2026-09-01T08:00",
                prnMinIntervalMinutes = null,
                prnMaxPerDay = null,
                lowStockThreshold = 5.0,
                stockAlertArmed = true,
            ),
            MedicationEntity(
                id = 2L,
                name = "布洛芬",
                unit = "粒",
                defaultDose = 1.0,
                mealTag = "NONE",
                notes = null,
                status = "ACTIVE",
                createdAt = 456L,
                courseStart = "2026-09-02",
                courseEnd = null,
                scheduleMode = "PRN",
                intervalEvery = null,
                intervalUnit = null,
                intervalAnchor = null,
                prnMinIntervalMinutes = 360,
                prnMaxPerDay = 4,
                lowStockThreshold = 3.0,
                stockAlertArmed = false,
            ),
        ),
        medicationTimes = listOf(MedicationTimeEntity(medicationId = 1L, minuteOfDay = 480)),
        stockEvents = listOf(
            StockEventEntity(id = 1L, medicationId = 1L, type = "INITIAL", delta = 30.0, at = 999L, note = null),
        ),
        doseRecords = listOf(
            DoseRecordEntity(
                id = 1L,
                medicationId = 1L,
                scheduledAt = 111L,
                actualAt = 222L,
                amount = 0.5,
                action = "TAKEN",
                source = "NOTIFICATION",
            ),
            DoseRecordEntity(
                id = 2L,
                medicationId = 2L,
                scheduledAt = null,
                actualAt = 333L,
                amount = 1.0,
                action = "TAKEN",
                source = "IN_APP",
            ),
        ),
        logEntries = listOf(
            LogEntryEntity(
                id = 1L,
                type = "SYMPTOM",
                at = 555L,
                symptom = "恶心",
                severity = 4,
                medicationId = 1L,
                note = null,
            ),
            LogEntryEntity(
                id = 2L,
                type = "NOTE",
                at = 666L,
                symptom = null,
                severity = null,
                medicationId = null,
                note = "今天精神不错",
            ),
        ),
        reminders = listOf(
            ReminderEntity(
                id = 1L,
                title = "复诊",
                at = 777L,
                repeatKind = "ONCE",
                interval = 1,
                note = "带上化验单",
                createdAt = 888L,
                doneAt = null,
            ),
            ReminderEntity(
                id = 2L,
                title = "复查肝功",
                at = 999L,
                repeatKind = "EVERY_N_MONTHS",
                interval = 3,
                note = null,
                createdAt = 1000L,
                doneAt = 1111L,
            ),
        ),
    )

    @Test
    fun `encode and decode round trips without loss`() {
        val original = payload()

        val restored = BackupJson.decode(BackupJson.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `unknown format version is rejected`() {
        val json = BackupJson
            .encode(payload())
            .replace("\"formatVersion\": ${BackupJson.FORMAT_VERSION}", "\"formatVersion\": 99")

        assertThrows(IllegalArgumentException::class.java) { BackupJson.decode(json) }
    }

    @Test
    fun `legacy v1 backups without logs still import`() {
        val legacy = """
            {"formatVersion":1,"exportedAt":1,"medications":[],"medicationTimes":[],
             "stockEvents":[],"doseRecords":[]}
        """.trimIndent()

        val payload = BackupJson.decode(legacy)

        assertTrue(payload.logEntries.isEmpty())
        assertTrue(payload.reminders.isEmpty())
        assertTrue(payload.medications.isEmpty())
    }

    @Test
    fun `v2 backups without reminders still import`() {
        val v2 = """
            {"formatVersion":2,"exportedAt":1,"medications":[],"medicationTimes":[],
             "stockEvents":[],"doseRecords":[],"logEntries":[]}
        """.trimIndent()

        assertTrue(BackupJson.decode(v2).reminders.isEmpty())
    }
}
