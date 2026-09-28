package moe.lizi.kusuri.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MedicationEntity::class,
        MedicationTimeEntity::class,
        StockEventEntity::class,
        DoseRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KusuriDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    abstract fun doseRecordDao(): DoseRecordDao

    companion object {
        const val NAME = "kusuri.db"

        fun build(context: Context): KusuriDatabase =
            Room.databaseBuilder(context, KusuriDatabase::class.java, NAME).build()
    }
}
