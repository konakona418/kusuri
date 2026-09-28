package moe.lizi.kusuri.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseRecordDao {

    /**
     * 窗口内的记录:
     * - 计划剂量按 scheduledAt 落入窗口;
     * - 按需(PRN)记录没有计划时间,按 actualAt 落入窗口。
     */
    @Query(
        """
        SELECT * FROM dose_records
        WHERE (scheduledAt IS NOT NULL AND scheduledAt >= :from AND scheduledAt < :to)
           OR (scheduledAt IS NULL AND actualAt >= :from AND actualAt < :to)
        ORDER BY actualAt
        """,
    )
    fun observeBetween(from: Long, to: Long): Flow<List<DoseRecordEntity>>

    @Query("SELECT * FROM dose_records WHERE medicationId = :medicationId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun findScheduled(medicationId: Long, scheduledAt: Long): DoseRecordEntity?

    @Query(
        """
        SELECT * FROM dose_records
        WHERE medicationId = :medicationId AND action = 'TAKEN'
        ORDER BY actualAt DESC LIMIT 1
        """,
    )
    suspend fun lastTaken(medicationId: Long): DoseRecordEntity?

    @Query(
        """
        SELECT COUNT(*) FROM dose_records
        WHERE medicationId = :medicationId AND action = 'TAKEN'
          AND actualAt >= :from AND actualAt < :to
        """,
    )
    suspend fun countTakenBetween(medicationId: Long, from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: DoseRecordEntity): Long

    @Query("UPDATE dose_records SET actualAt = :actualAt, action = :action WHERE id = :id")
    suspend fun update(id: Long, actualAt: Long, action: String)

    @Query("DELETE FROM dose_records WHERE id = :id")
    suspend fun delete(id: Long)
}
