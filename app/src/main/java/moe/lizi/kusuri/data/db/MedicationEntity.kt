package moe.lizi.kusuri.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val unit: String,
    val defaultDose: Double,
    val mealTag: String,
    val notes: String?,
    val status: String,
    val createdAt: Long,
    val courseStart: String,
    val courseEnd: String?,
    val scheduleMode: String,
    val intervalEvery: Int?,
    val intervalUnit: String?,
    val intervalAnchor: String?,
    val prnMinIntervalMinutes: Int?,
    val prnMaxPerDay: Int?,
    val lowStockThreshold: Double,
    val stockAlertArmed: Boolean,
)
