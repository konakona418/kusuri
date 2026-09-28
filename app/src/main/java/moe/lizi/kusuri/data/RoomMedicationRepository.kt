package moe.lizi.kusuri.data

import androidx.room.withTransaction
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.StockEventEntity
import moe.lizi.kusuri.data.db.timeEntities
import moe.lizi.kusuri.data.db.toDomain
import moe.lizi.kusuri.data.db.toEntity
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.StockEventType

class RoomMedicationRepository(
    private val db: KusuriDatabase,
    private val clock: Clock,
) : MedicationRepository {

    private val dao = db.medicationDao()

    override fun observeMedications(): Flow<List<Medication>> =
        dao.observeAll()
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeMedication(id: Long): Flow<Medication?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(medication: Medication): Long =
        db.withTransaction {
            val id = if (medication.id == 0L) {
                dao.insertMedication(medication.toEntity())
            } else {
                dao.updateMedication(medication.toEntity())
                medication.id
            }
            dao.deleteTimes(id)
            val times = medication.schedule.timeEntities(id)
            if (times.isNotEmpty()) dao.insertTimes(times)
            id
        }

    override suspend fun setStatus(id: Long, status: MedicationStatus) {
        dao.updateStatus(id, status.name)
    }

    override suspend fun delete(id: Long) {
        dao.deleteMedication(id)
    }

    override suspend fun addStock(id: Long, type: StockEventType, amount: Double) {
        dao.insertStockEvent(
            StockEventEntity(
                medicationId = id,
                type = type.name,
                delta = amount,
                at = clock.millis(),
                note = null,
            ),
        )
    }

    override suspend fun setStockAlertArmed(id: Long, armed: Boolean) {
        dao.updateStockAlertArmed(id, armed)
    }
}
