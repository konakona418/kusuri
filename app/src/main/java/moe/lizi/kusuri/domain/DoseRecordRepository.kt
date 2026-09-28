package moe.lizi.kusuri.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseRecord
import moe.lizi.kusuri.domain.model.DoseSource

interface DoseRecordRepository {

    /** 计划时间(或按需记录的实际时间)落在 [from, to) 内的记录,按实际时间升序。 */
    fun observeRecordsBetween(from: Instant, to: Instant): Flow<List<DoseRecord>>

    suspend fun findByScheduled(medicationId: Long, scheduledAt: Instant): DoseRecord?

    /** 最近一次"已服用"(含按需),用于按需药的最少间隔检查。 */
    suspend fun lastTaken(medicationId: Long): DoseRecord?

    /** 区间内的"已服用"次数,用于按需药的每日上限检查。 */
    suspend fun countTaken(medicationId: Long, from: Instant, to: Instant): Int

    /** 落一笔记录;[actualAt] 为空时用注入的时钟。 */
    suspend fun record(
        medicationId: Long,
        scheduledAt: Instant?,
        amount: Double,
        action: DoseAction,
        source: DoseSource,
        actualAt: Instant? = null,
    )

    suspend fun update(recordId: Long, actualAt: Instant, action: DoseAction)

    suspend fun delete(recordId: Long)
}
