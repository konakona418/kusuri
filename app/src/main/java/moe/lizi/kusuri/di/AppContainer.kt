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
import moe.lizi.kusuri.alarm.AlarmReminderScheduler
import moe.lizi.kusuri.alarm.DoseNotifier
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
        )
    }

    val wipeAllData: WipeAllDataUseCase by lazy {
        WipeAllDataUseCase(medicationRepository, alarmScheduler, backupService)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reminderSyncStarted = false

    /**
     * 打开 App / 数据变化时的一次性巡检:疗程收官、库存告警同步、整体重排闹钟。
     *
     * **不补发通知**:用户人就在 App 里,今天页写着"到时间了"、历史页写着"错过",
     * 再弹一条通知只是重复打扰(docs/plan.md §5)。
     */
    suspend fun runMaintenance() {
        prepare()
        alarmScheduler.rescheduleAll()
        alarmScheduler.rescheduleAllReminders()
    }

    /**
     * 平台叫我们的时候(开机 / 改时间 / 改时区 / 应用更新 / 闹钟响了却认不出目标):
     * 重排闹钟之外,再把今天"已经到点、仍在宽限窗口内"的剂量、以及最近一小时内本该响过的
     * 提醒补上——手机是真关机了、闹钟是真没响,这时补发才不是打扰。
     *
     * 刻意不做自建的定时巡检:WorkManager 与闹钟受同一套省电策略约束,被系统按住时一起被按住,
     * 兜不住;能按时跑的时候,又只是每小时把已经到点的旧通知重挂一遍(docs/plan.md §5)。
     */
    suspend fun recoverMissed(alertAgain: Boolean = false) {
        runMaintenance()
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

    /** 药物/提醒数据一变就整体重排闹钟;幂等,可在 App 启动时重复调用。 */
    fun startReminderSync() {
        if (reminderSyncStarted) return
        reminderSyncStarted = true
        applicationScope.launch {
            // 启动即巡检:疗程收官、库存告警、重排闹钟。补发留给平台事件,见 recoverMissed。
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
        /** 补发提醒的时间窗:只补"刚刚过去"的,不把几小时前的事翻出来打扰。 */
        val REMINDER_CATCH_UP_WINDOW: Duration = Duration.ofHours(1)
    }
}
