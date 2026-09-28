package moe.lizi.kusuri.data.backup

import moe.lizi.kusuri.data.db.DoseRecordEntity
import moe.lizi.kusuri.data.db.LogEntryEntity
import moe.lizi.kusuri.data.db.MedicationEntity
import moe.lizi.kusuri.data.db.MedicationTimeEntity
import moe.lizi.kusuri.data.db.StockEventEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份的 JSON 编解码。格式版本与 Room schema 解耦:
 * v1(无日志) 与 v2(含日志) 都能导入;导出始终写当前版本。
 */
object BackupJson {

    const val FORMAT_VERSION = 2
    private const val MIN_SUPPORTED_VERSION = 1

    fun encode(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("formatVersion", payload.formatVersion)
        root.put("exportedAt", payload.exportedAt)

        root.put("medications", payload.medications.map(::encodeMedication).toJsonArray())
        root.put("medicationTimes", payload.medicationTimes.map(::encodeTime).toJsonArray())
        root.put("stockEvents", payload.stockEvents.map(::encodeStockEvent).toJsonArray())
        root.put("doseRecords", payload.doseRecords.map(::encodeDoseRecord).toJsonArray())
        root.put("logEntries", payload.logEntries.map(::encodeLogEntry).toJsonArray())
        return root.toString(2)
    }

    fun decode(json: String): BackupPayload {
        val root = JSONObject(json)
        val formatVersion = root.getInt("formatVersion")
        require(formatVersion in MIN_SUPPORTED_VERSION..FORMAT_VERSION) {
            "unsupported backup format version: $formatVersion " +
                "(supported $MIN_SUPPORTED_VERSION..$FORMAT_VERSION)"
        }
        return BackupPayload(
            formatVersion = formatVersion,
            exportedAt = root.getLong("exportedAt"),
            medications = root.getJSONArray("medications").mapObjects(::decodeMedication),
            medicationTimes = root.getJSONArray("medicationTimes").mapObjects(::decodeTime),
            stockEvents = root.getJSONArray("stockEvents").mapObjects(::decodeStockEvent),
            doseRecords = root.getJSONArray("doseRecords").mapObjects(::decodeDoseRecord),
            // v1 备份没有日志段。
            logEntries = root.optJSONArray("logEntries")?.mapObjects(::decodeLogEntry).orEmpty(),
        )
    }

    private fun encodeMedication(medication: MedicationEntity) = JSONObject().apply {
        put("id", medication.id)
        put("name", medication.name)
        put("unit", medication.unit)
        put("defaultDose", medication.defaultDose)
        put("mealTag", medication.mealTag)
        put("notes", medication.notes)
        put("status", medication.status)
        put("createdAt", medication.createdAt)
        put("courseStart", medication.courseStart)
        put("courseEnd", medication.courseEnd)
        put("scheduleMode", medication.scheduleMode)
        put("intervalEvery", medication.intervalEvery)
        put("intervalUnit", medication.intervalUnit)
        put("intervalAnchor", medication.intervalAnchor)
        put("prnMinIntervalMinutes", medication.prnMinIntervalMinutes)
        put("prnMaxPerDay", medication.prnMaxPerDay)
        put("lowStockThreshold", medication.lowStockThreshold)
        put("stockAlertArmed", medication.stockAlertArmed)
    }

    private fun decodeMedication(json: JSONObject) = MedicationEntity(
        id = json.getLong("id"),
        name = json.getString("name"),
        unit = json.getString("unit"),
        defaultDose = json.getDouble("defaultDose"),
        mealTag = json.getString("mealTag"),
        notes = json.stringOrNull("notes"),
        status = json.getString("status"),
        createdAt = json.getLong("createdAt"),
        courseStart = json.getString("courseStart"),
        courseEnd = json.stringOrNull("courseEnd"),
        scheduleMode = json.getString("scheduleMode"),
        intervalEvery = json.intOrNull("intervalEvery"),
        intervalUnit = json.stringOrNull("intervalUnit"),
        intervalAnchor = json.stringOrNull("intervalAnchor"),
        prnMinIntervalMinutes = json.intOrNull("prnMinIntervalMinutes"),
        prnMaxPerDay = json.intOrNull("prnMaxPerDay"),
        lowStockThreshold = json.getDouble("lowStockThreshold"),
        stockAlertArmed = json.getBoolean("stockAlertArmed"),
    )

    private fun encodeTime(time: MedicationTimeEntity) = JSONObject().apply {
        put("medicationId", time.medicationId)
        put("minuteOfDay", time.minuteOfDay)
    }

    private fun decodeTime(json: JSONObject) = MedicationTimeEntity(
        medicationId = json.getLong("medicationId"),
        minuteOfDay = json.getInt("minuteOfDay"),
    )

    private fun encodeStockEvent(event: StockEventEntity) = JSONObject().apply {
        put("id", event.id)
        put("medicationId", event.medicationId)
        put("type", event.type)
        put("delta", event.delta)
        put("at", event.at)
        put("note", event.note)
    }

    private fun decodeStockEvent(json: JSONObject) = StockEventEntity(
        id = json.getLong("id"),
        medicationId = json.getLong("medicationId"),
        type = json.getString("type"),
        delta = json.getDouble("delta"),
        at = json.getLong("at"),
        note = json.stringOrNull("note"),
    )

    private fun encodeDoseRecord(record: DoseRecordEntity) = JSONObject().apply {
        put("id", record.id)
        put("medicationId", record.medicationId)
        put("scheduledAt", record.scheduledAt)
        put("actualAt", record.actualAt)
        put("amount", record.amount)
        put("action", record.action)
        put("source", record.source)
    }

    private fun decodeDoseRecord(json: JSONObject) = DoseRecordEntity(
        id = json.getLong("id"),
        medicationId = json.getLong("medicationId"),
        scheduledAt = json.longOrNull("scheduledAt"),
        actualAt = json.getLong("actualAt"),
        amount = json.getDouble("amount"),
        action = json.getString("action"),
        source = json.getString("source"),
    )

    private fun encodeLogEntry(entry: LogEntryEntity) = JSONObject().apply {
        put("id", entry.id)
        put("type", entry.type)
        put("at", entry.at)
        put("symptom", entry.symptom)
        put("severity", entry.severity)
        put("medicationId", entry.medicationId)
        put("note", entry.note)
    }

    private fun decodeLogEntry(json: JSONObject) = LogEntryEntity(
        id = json.getLong("id"),
        type = json.getString("type"),
        at = json.getLong("at"),
        symptom = json.stringOrNull("symptom"),
        severity = json.intOrNull("severity"),
        medicationId = json.longOrNull("medicationId"),
        note = json.stringOrNull("note"),
    )

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.intOrNull(name: String): Int? =
        if (isNull(name)) null else getInt(name)

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun List<JSONObject>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { array.put(it) }
        return array
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { index -> transform(getJSONObject(index)) }
}
