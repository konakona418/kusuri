package moe.lizi.kusuri.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "medication_times",
    primaryKeys = ["medicationId", "minuteOfDay"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("medicationId")],
)
data class MedicationTimeEntity(
    val medicationId: Long,
    val minuteOfDay: Int,
)
