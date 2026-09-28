package moe.lizi.kusuri.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.DoseReminderControl
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

/**
 * 每味药只维护"下一个未完成剂量"的闹钟:
 * 触发时发通知并排下一剂;开机/改时间/换时区/药物变更后整体重排。
 * 没有精确闹钟权限时降级为非精确闹钟(可能延迟),由 App 内状态提示。
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val engine: ScheduleEngine,
    private val notifier: DoseNotifier,
    private val clock: Clock,
) : DoseReminderControl {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(): Unit = rescheduleAll(medicationRepository.observeMedications().first())

    fun rescheduleAll(medications: List<Medication>) {
        medications.forEach { medication ->
            cancelReminder(medication.id)
            if (medication.status == MedicationStatus.ACTIVE) {
                scheduleNext(medication)
            } else {
                // 巡检的一部分:归档/完成的药物不应再挂着提醒通知。
                notifier.cancel(medication.id)
            }
        }
    }

    fun scheduleNext(medication: Medication) {
        cancelReminder(medication.id)
        val next = engine.nextDoseAfter(medication, clock.instant()) ?: return
        setAlarm(next.toEpochMilli(), reminderPendingIntent(medication.id, next))
    }

    /** "稍后 15 分钟":对同一次剂量再响一次,不改动记录与错过计时。 */
    fun scheduleSnooze(medicationId: Long, scheduledAt: Instant) {
        setAlarm(
            triggerAtMillis = clock.millis() + ReminderActions.SNOOZE_MILLIS,
            pendingIntent = snoozePendingIntent(medicationId, scheduledAt),
        )
    }

    /** 该次剂量已处理:撤下通知与稍后闹钟(提醒闹钟已在触发时结束)。 */
    override fun cancelDose(medicationId: Long) {
        notifier.cancel(medicationId)
        alarmManager.cancel(snoozePendingIntent(medicationId, clock.instant()))
    }

    /** 药物被归档/删除:清掉它的全部提醒痕迹。 */
    fun cancelAllFor(medicationId: Long) {
        cancelReminder(medicationId)
        notifier.cancel(medicationId)
        alarmManager.cancel(snoozePendingIntent(medicationId, clock.instant()))
    }

    private fun cancelReminder(medicationId: Long) {
        alarmManager.cancel(reminderPendingIntent(medicationId, clock.instant()))
    }

    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (ExactAlarmPermissions.canScheduleExactAlarms(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun reminderPendingIntent(medicationId: Long, scheduledAt: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicationId),
            reminderIntent(medicationId, scheduledAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun snoozePendingIntent(medicationId: Long, scheduledAt: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicationId),
            snoozeIntent(medicationId, scheduledAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderIntent(medicationId: Long, scheduledAt: Instant): Intent =
        Intent(context, DoseAlarmReceiver::class.java).apply {
            action = ReminderActions.ACTION_DOSE_REMINDER
            putExtra(ReminderExtras.MEDICATION_ID, medicationId)
            putExtra(ReminderExtras.SCHEDULED_AT, scheduledAt.toEpochMilli())
        }

    private fun snoozeIntent(medicationId: Long, scheduledAt: Instant): Intent =
        Intent(context, DoseAlarmReceiver::class.java).apply {
            action = ReminderActions.ACTION_DOSE_SNOOZE
            putExtra(ReminderExtras.MEDICATION_ID, medicationId)
            putExtra(ReminderExtras.SCHEDULED_AT, scheduledAt.toEpochMilli())
        }
}
