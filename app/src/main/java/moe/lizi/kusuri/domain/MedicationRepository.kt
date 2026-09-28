package moe.lizi.kusuri.domain

import kotlinx.coroutines.flow.Flow
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.StockEventType

interface MedicationRepository {

    /** 未归档的药物(含已完成),按创建时间倒序。 */
    fun observeMedications(): Flow<List<Medication>>

    fun observeMedication(id: Long): Flow<Medication?>

    /** 新建([Medication.id] 为 0)或整体更新;返回药物 id。 */
    suspend fun save(medication: Medication): Long

    suspend fun setStatus(id: Long, status: MedicationStatus)

    suspend fun delete(id: Long)

    /** 追加一笔库存事件(初始/补货/调整),不改变药物本身。 */
    suspend fun addStock(id: Long, type: StockEventType, amount: Double)
}
