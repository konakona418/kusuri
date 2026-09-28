package moe.lizi.kusuri.data

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.toDomain
import moe.lizi.kusuri.data.db.toEntity
import moe.lizi.kusuri.domain.LogEntryRepository
import moe.lizi.kusuri.domain.model.LogEntry

class RoomLogEntryRepository(
    private val db: KusuriDatabase,
) : LogEntryRepository {

    private val dao = db.logEntryDao()

    override fun observeBetween(from: Instant, to: Instant): Flow<List<LogEntry>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(entry: LogEntry): Long =
        if (entry.id == 0L) {
            dao.insert(entry.toEntity())
        } else {
            dao.update(entry.toEntity())
            entry.id
        }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun recentSymptoms(limit: Int): List<String> =
        dao.recentSymptomRows(RECENT_SYMPTOM_SCAN)
            .distinct()
            .take(limit)

    private companion object {
        const val RECENT_SYMPTOM_SCAN = 50
    }
}
