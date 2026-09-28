package moe.lizi.kusuri.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 服药记录。M1 仅建表并以它参与库存派生;打卡交互在 M2/M3 引入。 */
@Entity(
    tableName = "dose_records",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId"), Index("actualAt")],
)
data class DoseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val medicationId: Long,
    val scheduledAt: Long?,
    val actualAt: Long,
    val amount: Double,
    val action: String,
    val source: String,
)
