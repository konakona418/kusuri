package moe.lizi.kusuri.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.util.formatAmount

/** 导出 CSV 需要本地化的标签,由界面(资源)提供,数据层不依赖 strings.xml。 */
data class CsvLabels(
    val header: List<String>,
    val taken: String,
    val skipped: String,
    val sourceInApp: String,
    val sourceNotification: String,
    val sourceBackfill: String,
)

/**
 * 导入导出的落地:
 * - JSON 为全量备份/恢复(换机);
 * - CSV 为服药记录(给医生看),按时间区间。
 * 通过 SAF 读写,全程无需网络(docs/plan.md §2)。
 */
class BackupService(
    private val context: Context,
    private val db: KusuriDatabase,
    private val clock: Clock,
) {

    suspend fun exportJson(): String {
        val payload = BackupPayload(
            formatVersion = BackupJson.FORMAT_VERSION,
            exportedAt = clock.millis(),
            medications = db.medicationDao().getAll(),
            medicationTimes = db.medicationDao().getAllTimes(),
            stockEvents = db.medicationDao().getAllStockEvents(),
            doseRecords = db.doseRecordDao().getAll(),
        )
        return BackupJson.encode(payload)
    }

    /** 恢复会**整体替换**现有数据;返回导入的药物数量。 */
    suspend fun importJson(json: String): Int {
        val payload = BackupJson.decode(json)
        db.withTransaction {
            db.doseRecordDao().deleteAll()
            db.medicationDao().deleteAllStockEvents()
            db.medicationDao().deleteAllTimes()
            db.medicationDao().deleteAll()
            db.medicationDao().insertMedications(payload.medications)
            db.medicationDao().insertTimes(payload.medicationTimes)
            db.medicationDao().insertStockEvents(payload.stockEvents)
            db.doseRecordDao().insertAll(payload.doseRecords)
        }
        return payload.medications.size
    }

    suspend fun exportCsv(from: Instant, to: Instant, labels: CsvLabels): String {
        val medications = db.medicationDao().getAll().associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val rows = db.doseRecordDao().getAll()
            .filter { it.actualAt >= from.toEpochMilli() && it.actualAt < to.toEpochMilli() }
            .sortedBy { it.actualAt }
            .map { record ->
                val medication = medications[record.medicationId]
                val action = runCatching { DoseAction.valueOf(record.action) }.getOrNull()
                val source = runCatching { DoseSource.valueOf(record.source) }.getOrNull()
                listOf(
                    medication?.name.orEmpty(),
                    "${formatAmount(record.amount)} ${medication?.unit.orEmpty()}".trim(),
                    formatInstant(record.scheduledAt, zone),
                    formatInstant(record.actualAt, zone),
                    if (action == DoseAction.TAKEN) labels.taken else labels.skipped,
                    sourceLabel(source, labels),
                )
            }
        return toCsv(labels.header, rows)
    }

    fun writeText(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("cannot open output stream for the selected file")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("cannot open input stream for the selected file")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun sourceLabel(source: DoseSource?, labels: CsvLabels): String = when (source) {
        DoseSource.NOTIFICATION -> labels.sourceNotification
        DoseSource.BACKFILL -> labels.sourceBackfill
        else -> labels.sourceInApp
    }

    private fun formatInstant(millis: Long?, zone: ZoneId): String =
        millis?.let { Instant.ofEpochMilli(it).atZone(zone).format(TIMESTAMP_FORMAT) }.orEmpty()

    private companion object {
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
