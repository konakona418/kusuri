package moe.lizi.kusuri.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日志条目:症状(结构化)或随手记(自由文本)。
 * 删除药物时日志**保留**(SET NULL)——日志属于"我的身体记录",不随药物消失。
 */
@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("at"), Index("medicationId")],
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val at: Long,
    val symptom: String?,
    val severity: Int?,
    val medicationId: Long?,
    val note: String?,
)
