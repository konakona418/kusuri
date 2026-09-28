package moe.lizi.kusuri.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
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
) {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(): Unit = rescheduleAll(medicationRepository.observeMedications().first())

    fun rescheduleAll(medications: List<Medication>) {
        medications.forEach { medication ->
            cancelReminder(medication.id)
            if (medication.status == MedicationStatus.ACTIVE) scheduleNext(medication)
        }
    }

    fun scheduleNext(medication: Medication) {
        cancelReminder(medication.id)
        val next = engine.nextDoseAfter(medication, clock.instant()) ?: return
        setAlarm(next.toEpochMilli(), reminderPendingIntent(medication.id, next))
    }

    fun scheduleSnooze(medicationId: Long) {
        setAlarm(clock.millis() + ReminderActions.SNOOZE_MILLIS, snoozePendingIntent(medicationId))
    }

    /** 该次剂量已处理:撤下通知与稍后闹钟(提醒闹钟已在触发时结束)。 */
    fun cancelDose(medicationId: Long) {
        notifier.cancel(medicationId)
        existingPendingIntent(snoozeIntent(medicationId))?.let(alarmManager::cancel)
    }

    /** 药物被归档/删除:清掉它的全部提醒痕迹。 */
    fun cancelAllFor(medicationId: Long) {
        cancelReminder(medicationId)
        notifier.cancel(medicationId)
        existingPendingIntent(snoozeIntent(medicationId))?.let(alarmManager::cancel)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun cancelReminder(medicationId: Long) {
        existingPendingIntent(reminderIntent(medicationId))?.let(alarmManager::cancel)
    }

    private fun setAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun reminderPendingIntent(medicationId: Long, scheduledAt: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicationId),
            reminderIntent(medicationId).apply {
                putExtra(ReminderExtras.SCHEDULED_AT, scheduledAt.toEpochMilli())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun snoozePendingIntent(medicationId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicationId),
            snoozeIntent(medicationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun existingPendingIntent(intent: Intent): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            reminderRequestCode(intent.getLongExtra(ReminderExtras.MEDICATION_ID, -1L)),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderIntent(medicationId: Long): Intent =
        Intent(context, DoseAlarmReceiver::class.java).apply {
            action = ReminderActions.ACTION_DOSE_REMINDER
            putExtra(ReminderExtras.MEDICATION_ID, medicationId)
        }

    private fun snoozeIntent(medicationId: Long): Intent =
        Intent(context, DoseAlarmReceiver::class.java).apply {
            action = ReminderActions.ACTION_DOSE_SNOOZE
            putExtra(ReminderExtras.MEDICATION_ID, medicationId)
        }
}
