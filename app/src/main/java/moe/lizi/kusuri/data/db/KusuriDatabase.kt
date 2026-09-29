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
        LogEntryEntity::class,
        ReminderEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class KusuriDatabase : RoomDatabase() {

    abstract fun medicationDao(): MedicationDao

    abstract fun doseRecordDao(): DoseRecordDao

    abstract fun logEntryDao(): LogEntryDao

    abstract fun reminderDao(): ReminderDao

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

        /** v3:日志条目(症状/随手记);删除药物时日志保留(medicationId 置空)。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`symptom` TEXT, " +
                        "`severity` INTEGER, " +
                        "`medicationId` INTEGER, " +
                        "`note` TEXT, " +
                        "FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_log_entries_at` ON `log_entries` (`at`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_log_entries_medicationId` " +
                        "ON `log_entries` (`medicationId`)",
                )
            }
        }

        /** v4:通用提醒(复诊/取药/复查/日常事项)。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, " +
                        "`repeatKind` TEXT NOT NULL, " +
                        "`interval` INTEGER NOT NULL, " +
                        "`note` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`doneAt` INTEGER)",
                )
            }
        }

        fun build(context: Context): KusuriDatabase =
            Room.databaseBuilder(context, KusuriDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
