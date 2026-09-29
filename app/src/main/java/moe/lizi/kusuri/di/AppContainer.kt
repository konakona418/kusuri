package moe.lizi.kusuri.di

import android.content.Context
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
import moe.lizi.kusuri.alarm.ReminderNotifier
import moe.lizi.kusuri.data.RoomDoseRecordRepository
import moe.lizi.kusuri.data.RoomLogEntryRepository
import moe.lizi.kusuri.data.RoomMedicationRepository
import moe.lizi.kusuri.data.RoomReminderRepository
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
import moe.lizi.kusuri.domain.ReminderRepository
import moe.lizi.kusuri.domain.SyncDoseNotificationsUseCase
import moe.lizi.kusuri.domain.WipeAllDataUseCase
import moe.lizi.kusuri.domain.reminder.ReminderSchedule
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

    val reminderRepository: ReminderRepository by lazy {
        RoomReminderRepository(database)
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val backupService: BackupService by lazy { BackupService(appContext, database, clock) }

    val lanExportClient: LanExportClient = LanExportClient()

    val doseNotifier: DoseNotifier by lazy { DoseNotifier(appContext, settingsRepository) }

    val reminderNotifier: ReminderNotifier by lazy { ReminderNotifier(appContext, settingsRepository) }

    val syncDoseNotifications: SyncDoseNotificationsUseCase by lazy {
        SyncDoseNotificationsUseCase(
            medicationRepository = medicationRepository,
            doseRecordRepository = doseRecordRepository,
            engine = scheduleEngine,
            alerts = doseNotifier,
        )
    }

    val alarmScheduler: AlarmReminderScheduler by lazy {
        AlarmReminderScheduler(
            context = appContext,
            medicationRepository = medicationRepository,
            reminderRepository = reminderRepository,
            engine = scheduleEngine,
            notifier = doseNotifier,
            reminderNotifier = reminderNotifier,
            clock = clock,
        )
    }

    val checkLowStock: CheckLowStockUseCase by lazy {
        CheckLowStockUseCase(medicationRepository, doseNotifier)
    }

    val completeFinishedCourses: CompleteFinishedCoursesUseCase by lazy {
        CompleteFinishedCoursesUseCase(medicationRepository, scheduleEngine)
    }

    val recordDose: RecordDoseUseCase by lazy {
        RecordDoseUseCase(
            medicationRepository = medicationRepository,
            doseRecordRepository = doseRecordRepository,
            reminderControl = alarmScheduler,
            checkLowStock = checkLowStock,
            syncNotifications = syncDoseNotifications,
            clock = clock,
        )
    }

    val wipeAllData: WipeAllDataUseCase by lazy {
        WipeAllDataUseCase(medicationRepository, alarmScheduler, backupService)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reminderSyncStarted = false

    /** 开机/改时间/启动时的巡检:疗程收官、库存告警同步、整体重排闹钟、补发漏掉的提醒。 */
    suspend fun runMaintenance(alertAgain: Boolean = false) {
        prepare()
        alarmScheduler.rescheduleAll()
        alarmScheduler.rescheduleAllReminders()
        catchUpMissed(clock.instant(), alertAgain)
    }

    /**
     * 闹钟可能被系统吞掉(App 被杀、省电策略、更新后没来得及重排),或者闹钟广播的 extras
     * 被 ROM 剥空导致接收器认不出是哪一次:把今天"已经到点、还在宽限窗口内"的剂量、
     * 以及最近一小时内本该响过的一次性/重复提醒补上。
     * 幂等——已经挂着的通知只是原地更新,不会重复响。
     */
    private suspend fun catchUpMissed(now: Instant, alertAgain: Boolean) {
        syncDoseNotifications.catchUp(
            now = now,
            gracePeriod = Duration.ofHours(settingsRepository.gracePeriodHours.value.toLong()),
            alertAgain = alertAgain,
        )
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        reminderRepository.observeAll().first()
            .filter { it.doneAt == null }
            .forEach { reminder ->
                val occurrence = ReminderSchedule.occurrenceOn(reminder, today, zone) ?: return@forEach
                val missedRecently = !occurrence.isAfter(now) &&
                    occurrence.isAfter(now.minus(REMINDER_CATCH_UP_WINDOW))
                if (missedRecently) {
                    reminderNotifier.notify(reminder, occurrence, now, alertAgain = alertAgain)
                }
            }
    }

    /** 只需要执行一次的巡检步骤(App 启动时会接着订阅药物变化,由订阅负责重排)。 */
    private suspend fun prepare() {
        completeFinishedCourses.completeFinished()
        medicationRepository.observeMedications().first().forEach { medication ->
            checkLowStock.check(medication.id)
        }
    }

    /** 每 1 小时一次的巡检:WorkManager 作为"漏排/被杀"的安全网(docs/plan.md §5)。 */
    fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<ReminderMaintenanceWorker>(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 药物/提醒数据一变就整体重排闹钟;幂等,可在 App 启动时重复调用。 */
    fun startReminderSync() {
        if (reminderSyncStarted) return
        reminderSyncStarted = true
        applicationScope.launch {
            // 启动即巡检:疗程收官、库存告警、重排闹钟,并把被系统吞掉的提醒补上。
            runMaintenance()
            launch {
                medicationRepository.observeMedications().collect { medications ->
                    alarmScheduler.rescheduleAll(medications)
                }
            }
            launch {
                reminderRepository.observeAll().collect { reminders ->
                    alarmScheduler.rescheduleAllReminders(reminders)
                }
            }
        }
    }

    private companion object {
        const val MAINTENANCE_WORK_NAME = "reminder-maintenance"

        /** 补发提醒的时间窗:只补"刚刚过去"的,不把几小时前的事翻出来打扰。 */
        val REMINDER_CATCH_UP_WINDOW: Duration = Duration.ofHours(1)
    }
}
