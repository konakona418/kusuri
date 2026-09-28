package moe.lizi.kusuri.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import moe.lizi.kusuri.domain.model.LogEntry

interface LogEntryRepository {

    /** 时间落在 [from, to) 内的日志条目,按时间倒序。 */
    fun observeBetween(from: Instant, to: Instant): Flow<List<LogEntry>>

    /** 新建([LogEntry.id] 为 0)或更新;返回条目 id。 */
    suspend fun save(entry: LogEntry): Long

    suspend fun delete(id: Long)

    /** 最近使用过的症状(去重、按最近使用排序),用于快捷录入建议。 */
    suspend fun recentSymptoms(limit: Int = 8): List<String>
}
