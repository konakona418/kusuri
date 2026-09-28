package moe.lizi.kusuri.di

import android.content.Context
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import moe.lizi.kusuri.alarm.AlarmReminderScheduler
import moe.lizi.kusuri.alarm.DoseNotifier
import moe.lizi.kusuri.alarm.ReminderMaintenanceWorker
import moe.lizi.kusuri.data.RoomDoseRecordRepository
import moe.lizi.kusuri.data.RoomLogEntryRepository
import moe.lizi.kusuri.data.RoomMedicationRepository
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.data.backup.BackupService
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.data.lan.LanExportClient
import moe.lizi.kusuri.domain.CheckLowStockUseCase
import moe.lizi.kusuri.domain.CompleteFinishedCoursesUseCase
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.LogEntryRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.RecordDoseUseCase
import moe.lizi.kusuri.domain.WipeAllDataUseCase
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

/** 手动依赖容器:单模块小应用不引入 Hilt(docs/plan.md §8)。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val clock: Clock = Clock.systemDefaultZone()

    val scheduleEngine: ScheduleEngine = ScheduleEngine(clock)

    private val database: KusuriDatabase by lazy { KusuriDatabase.build(appContext) }

    val medicationRepository: MedicationRepository by lazy {
        RoomMedicationRepository(database, clock)
    }

    val doseRecordRepository: DoseRecordRepository by lazy {
        RoomDoseRecordRepository(database, clock)
    }

    val logEntryRepository: LogEntryRepository by lazy {
        RoomLogEntryRepository(database)
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val backupService: BackupService by lazy { BackupService(appContext, database, clock) }

    val lanExportClient: LanExportClient = LanExportClient()

    val doseNotifier: DoseNotifier by lazy { DoseNotifier(appContext, settingsRepository) }

    val alarmScheduler: AlarmReminderScheduler by lazy {
        AlarmReminderScheduler(appContext, medicationRepository, scheduleEngine, doseNotifier, clock)
    }

    val checkLowStock: CheckLowStockUseCase by lazy {
        CheckLowStockUseCase(medicationRepository, doseNotifier)
    }

    val completeFinishedCourses: CompleteFinishedCoursesUseCase by lazy {
        CompleteFinishedCoursesUseCase(medicationRepository, scheduleEngine)
    }

    val recordDose: RecordDoseUseCase by lazy {
        RecordDoseUseCase(medicationRepository, doseRecordRepository, alarmScheduler, checkLowStock)
    }

    val wipeAllData: WipeAllDataUseCase by lazy {
        WipeAllDataUseCase(medicationRepository, alarmScheduler, backupService)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reminderSyncStarted = false

    /** 开机/改时间/启动时的巡检:疗程收官、库存告警同步、整体重排闹钟。 */
    suspend fun runMaintenance() {
        prepare()
        alarmScheduler.rescheduleAll()
    }

    /** 只需要执行一次的巡检步骤(App 启动时会接着订阅药物变化,由订阅负责重排)。 */
    private suspend fun prepare() {
        completeFinishedCourses.completeFinished()
        medicationRepository.observeMedications().first().forEach { medication ->
            checkLowStock.check(medication.id)
        }
    }

    /** 每 6 小时一次的巡检:WorkManager 作为"漏排/被杀"的安全网(docs/plan.md §5)。 */
    fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<ReminderMaintenanceWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 药物数据一变就整体重排闹钟;幂等,可在 App 启动时重复调用。 */
    fun startReminderSync() {
        if (reminderSyncStarted) return
        reminderSyncStarted = true
        applicationScope.launch {
            prepare()
            medicationRepository.observeMedications().collect { medications ->
                alarmScheduler.rescheduleAll(medications)
            }
        }
    }

    private companion object {
        const val MAINTENANCE_WORK_NAME = "reminder-maintenance"
    }
}
