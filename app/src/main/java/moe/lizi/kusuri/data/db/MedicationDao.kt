package moe.lizi.kusuri.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 剩余库存 = 库存事件之和 − 已服用(含补记)记录之和。
 * 跳过与错过不扣减;漏记的服用会高估库存,由补记修正(docs/plan.md §6)。
 */
private const val REMAINING_STOCK = """
    (SELECT COALESCE(SUM(e.delta), 0.0) FROM stock_events e WHERE e.medicationId = m.id)
    - (SELECT COALESCE(SUM(r.amount), 0.0) FROM dose_records r WHERE r.medicationId = m.id AND r.action = 'TAKEN')
"""

data class MedicationRow(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val times: List<MedicationTimeEntity>,
    @ColumnInfo(name = "remainingStock") val remainingStock: Double,
)

@Dao
interface MedicationDao {

    @Transaction
    @Query(
        """
        SELECT m.*, $REMAINING_STOCK AS remainingStock
        FROM medications m
        ORDER BY m.createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<MedicationRow>>

    @Transaction
    @Query("SELECT m.*, $REMAINING_STOCK AS remainingStock FROM medications m WHERE m.id = :id")
    fun observeById(id: Long): Flow<MedicationRow?>

    @Insert
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Query("UPDATE medications SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimes(times: List<MedicationTimeEntity>)

    @Query("DELETE FROM medication_times WHERE medicationId = :medicationId")
    suspend fun deleteTimes(medicationId: Long)

    @Insert
    suspend fun insertStockEvent(event: StockEventEntity): Long
}
