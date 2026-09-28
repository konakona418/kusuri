package moe.lizi.kusuri.di

import android.content.Context
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.lizi.kusuri.alarm.AlarmReminderScheduler
import moe.lizi.kusuri.alarm.DoseNotifier
import moe.lizi.kusuri.data.RoomDoseRecordRepository
import moe.lizi.kusuri.data.RoomMedicationRepository
import moe.lizi.kusuri.data.db.KusuriDatabase
import moe.lizi.kusuri.domain.DoseRecordRepository
import moe.lizi.kusuri.domain.MedicationRepository
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

    val doseNotifier: DoseNotifier by lazy { DoseNotifier(appContext) }

    val alarmScheduler: AlarmReminderScheduler by lazy {
        AlarmReminderScheduler(appContext, medicationRepository, scheduleEngine, doseNotifier, clock)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var reminderSyncStarted = false

    /** 药物数据一变就整体重排闹钟;幂等,可在 App 启动时重复调用。 */
    fun startReminderSync() {
        if (reminderSyncStarted) return
        reminderSyncStarted = true
        applicationScope.launch {
            medicationRepository.observeMedications().collect { medications ->
                alarmScheduler.rescheduleAll(medications)
            }
        }
    }
}
