package moe.lizi.kusuri.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import moe.lizi.kusuri.domain.DoseReminderControl
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.ReminderControl
import moe.lizi.kusuri.domain.ReminderRepository
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.reminder.ReminderSchedule
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

/**
 * 闹钟排程:
 * - 每味药只维护"下一个未完成剂量"的闹钟;触发时发通知并排下一剂。
 * - 每条通用提醒只维护"下一次"的闹钟(重复规则在领域层算);同样触发后重排。
 * 开机/改时间/换时区/数据变更后整体重排;没有精确闹钟权限时降级为非精确(可能延迟),由 App 内状态提示。
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val reminderRepository: ReminderRepository,
    private val engine: ScheduleEngine,
    private val notifier: DoseNotifier,
    private val reminderNotifier: ReminderNotifier,
    private val clock: Clock,
) : DoseReminderControl, ReminderControl {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------- 服药提醒 ----------

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
    override fun cancelAllFor(medicationId: Long) {
        cancelReminder(medicationId)
        notifier.cancel(medicationId)
        alarmManager.cancel(snoozePendingIntent(medicationId, clock.instant()))
    }

    // ---------- 通用提醒 ----------

    suspend fun rescheduleAllReminders(): Unit =
        rescheduleAllReminders(reminderRepository.observeAll().first())

    fun rescheduleAllReminders(reminders: List<Reminder>) {
        reminders.forEach { reminder ->
            cancel(reminder.id)
            scheduleNext(reminder)
        }
    }

    override fun scheduleNext(reminder: Reminder) {
        cancel(reminder.id)
        val next = ReminderSchedule.nextOccurrence(reminder, clock.instant(), zone) ?: return
        setAlarm(next.toEpochMilli(), reminderAlarmPendingIntent(reminder.id, next))
    }

    /** "稍后 15 分钟":对同一次提醒再响一次,不改动任何记录。 */
    fun scheduleReminderSnooze(reminderId: Long) {
        setAlarm(
            triggerAtMillis = clock.millis() + ReminderActions.SNOOZE_MILLIS,
            pendingIntent = reminderSnoozePendingIntent(reminderId, clock.instant()),
        )
    }

    override fun cancel(reminderId: Long) {
        alarmManager.cancel(reminderAlarmPendingIntent(reminderId, clock.instant()))
        alarmManager.cancel(reminderSnoozePendingIntent(reminderId, clock.instant()))
        reminderNotifier.cancel(reminderId)
    }

    override fun clearNotification(reminderId: Long) {
        alarmManager.cancel(reminderSnoozePendingIntent(reminderId, clock.instant()))
        reminderNotifier.cancel(reminderId)
    }

    // ---------- 基础设施 ----------

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

    private fun reminderAlarmPendingIntent(reminderId: Long, occurrence: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderAlarmRequestCode(reminderId),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderActions.ACTION_REMINDER_ALARM
                putExtra(ReminderExtras.REMINDER_ID, reminderId)
                putExtra(ReminderExtras.OCCURRENCE_AT, occurrence.toEpochMilli())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderSnoozePendingIntent(reminderId: Long, occurrence: Instant): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderAlarmRequestCode(reminderId),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderActions.ACTION_REMINDER_SNOOZE
                putExtra(ReminderExtras.REMINDER_ID, reminderId)
                putExtra(ReminderExtras.OCCURRENCE_AT, occurrence.toEpochMilli())
            },
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
