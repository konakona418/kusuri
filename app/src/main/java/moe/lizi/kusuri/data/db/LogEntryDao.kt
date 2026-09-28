package moe.lizi.kusuri.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {

    @Query("SELECT * FROM log_entries WHERE at >= :from AND at < :to ORDER BY at DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insert(entry: LogEntryEntity): Long

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM log_entries")
    suspend fun getAll(): List<LogEntryEntity>

    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    /** 最近使用过的症状(含重复),由仓库去重后给界面做快捷建议。 */
    @Query(
        """
        SELECT symptom FROM log_entries
        WHERE type = 'SYMPTOM' AND symptom IS NOT NULL
        ORDER BY at DESC LIMIT :limit
        """,
    )
    suspend fun recentSymptomRows(limit: Int): List<String>
}
