package moe.lizi.kusuri.data

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.lizi.kusuri.data.db.DoseRecordEntity
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.db.toDomain
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource

class RoomDoseRecordRepository(
    private val db: KusuriDatabase,
    private val clock: Clock,
) : DoseRecordRepository {

    private val dao = db.doseRecordDao()

    override fun observeScheduledBetween(from: Instant, to: Instant): Flow<List<DoseRecord>> =
        dao.observeScheduledBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord? =
        dao.findScheduled(medicationId, scheduledAt.toEpochMilli())?.toDomain()

    override suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
    ) {
        dao.insert(
            DoseRecordEntity(
                medicationId = medicationId,
                scheduledAt = scheduledAt?.toEpochMilli(),
                actualAt = clock.millis(),
                amount = amount,
                action = action.name,
                source = source.name,
            ),
        )
    }
}
