package moe.lizi.kusuri.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MedicationEntity::class,
        MedicationTimeEntity::class,
        StockEventEntity::class,
        DoseRecordEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KusuriDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    abstract fun doseRecordDao(): DoseRecordDao

    companion object {
        const val NAME = "kusuri.db"

        /** v2:为服药记录加上 (药物, 计划时间) 唯一索引,保证"一次剂量只记一条"。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_dose_records_medicationId_scheduledAt " +
                        "ON dose_records (medicationId, scheduledAt)",
                )
            }
        }

        fun build(context: Context): KusuriDatabase =
            Room.databaseBuilder(context, KusuriDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
