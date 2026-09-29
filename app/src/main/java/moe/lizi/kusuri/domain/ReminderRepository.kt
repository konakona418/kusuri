package moe.lizi.kusuri.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import moe.lizi.kusuri.domain.model.Reminder

/** 通用提醒的读写(docs/plan.md §14)。 */
interface ReminderRepository {

    /** 全部提醒,按第一次(唯一一次)的时刻升序。 */
    fun observeAll(): Flow<List<Reminder>>

    suspend fun all(): List<Reminder>

    suspend fun get(id: Long): Reminder?

    /** 新建([Reminder.id] 为 0)或更新;返回 id。 */
    suspend fun save(reminder: Reminder): Long

    suspend fun delete(id: Long)

    /**
     * "知道了":只对**一次性**提醒落状态。
     * 重复提醒不记完成——它没有"完成"这一说,下一次自然会发生;这里只负责取消通知。
     */
    suspend fun markDone(id: Long, at: Instant)
}
