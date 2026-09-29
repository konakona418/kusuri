package moe.lizi.kusuri.data

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.toDomain
import moe.lizi.kusuri.data.db.toEntity
import moe.lizi.kusuri.domain.ReminderRepository
import moe.lizi.kusuri.domain.model.Reminder

class RoomReminderRepository(
    private val db: KusuriDatabase,
) : ReminderRepository {

    private val dao = db.reminderDao()

    override fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<Reminder> = dao.getAll().map { it.toDomain() }

    override suspend fun get(id: Long): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun save(reminder: Reminder): Long =
        if (reminder.id == 0L) {
            dao.insert(reminder.toEntity())
        } else {
            dao.update(reminder.toEntity())
            reminder.id
        }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun markDone(id: Long, at: Instant) = dao.markDone(id, at.toEpochMilli())
}
