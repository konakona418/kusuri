package moe.lizi.kusuri.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource

interface DoseRecordRepository {

    /** 计划时间落在 [from, to) 内的记录,按计划时间升序(用于时间线匹配)。 */
    fun observeScheduledBetween(from: Instant, to: Instant): Flow<List<DoseRecord>>

    suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord?

    /** 以注入的时钟作为实际服用时间落一笔记录。 */
    suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
    )
}
