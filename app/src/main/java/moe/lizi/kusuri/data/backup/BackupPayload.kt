package moe.lizi.kusuri.data.backup

import moe.lizi.kusuri.data.db.DoseRecordEntity
import moe.lizi.kusuri.data.db.MedicationEntity
import moe.lizi.kusuri.data.db.MedicationTimeEntity
import moe.lizi.kusuri.data.db.StockEventEntity

/** 全量备份的内容,与数据库表一一对应,便于无损往返。 */
data class BackupPayload(
    val formatVersion: Int,
    val exportedAt: Long,
    val medications: List<MedicationEntity>,
    val medicationTimes: List<MedicationTimeEntity>,
    val stockEvents: List<StockEventEntity>,
    val doseRecords: List<DoseRecordEntity>,
)
