package moe.lizi.kusuri.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 通用提醒(复诊/取药/复查/日常事项),docs/plan.md §14。 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val at: Long,
    val repeatKind: String,
    val interval: Int,
    val note: String?,
    val createdAt: Long,
    val doneAt: Long?,
)
