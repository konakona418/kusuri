package moe.lizi.kusuri.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseRecordDao {

    @Query(
        """
        SELECT * FROM dose_records
        WHERE scheduledAt IS NOT NULL AND scheduledAt >= :from AND scheduledAt < :to
        ORDER BY scheduledAt
        """,
    )
    fun observeScheduledBetween(from: Long, to: Long): Flow<List<DoseRecordEntity>>

    @Query("SELECT * FROM dose_records WHERE medicationId = :medicationId AND scheduledAt = :scheduledAt LIMIT 1")
    suspend fun findScheduled(medicationId: Long, scheduledAt: Long): DoseRecordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: DoseRecordEntity): Long

    @Query("UPDATE dose_records SET actualAt = :actualAt, action = :action WHERE id = :id")
    suspend fun update(id: Long, actualAt: Long, action: String)

    @Query("DELETE FROM dose_records WHERE id = :id")
    suspend fun delete(id: Long)
}
