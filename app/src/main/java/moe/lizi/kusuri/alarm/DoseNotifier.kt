package moe.lizi.kusuri.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId
import moe.lizi.kusuri.MainActivity
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.LowStockAlertControl
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 服药提醒与低库存提醒的通知出口:
 * - 服药提醒:每味药同时只保留一条通知(用 medicationId 作为 id),下一剂到点替换上一剂;
 * - 低库存提醒:一次性的补药提示。
 *
 * 等级与渠道统一走 [ReminderChannels]:所有通知都按全局"提醒等级"选渠道(docs/plan.md §4.1、§14)。
 */
class DoseNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) : LowStockAlertControl {

    fun notify(medication: Medication, scheduledAt: Instant, now: Instant) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val level = settings.reminderLevel.value
        val scheduledMillis = scheduledAt.toEpochMilli()
        val time = formatTime(scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime())

        val notification = NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, medication.name))
            .setContentText(notificationText(medication, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .setContentIntent(contentIntent(doseNotificationId(medication.id)))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_taken),
                doseAction(ReminderActions.ACTION_DOSE_TAKEN, medication.id, scheduledMillis),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_snooze_15),
                doseAction(ReminderActions.ACTION_DOSE_SNOOZE_REQUEST, medication.id, scheduledMillis),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_skip),
                doseAction(ReminderActions.ACTION_DOSE_SKIP, medication.id, scheduledMillis),
            )
            .setTimeoutAfter(timeoutMillis(scheduledAt, now))
            .build()

        manager.notify(doseNotificationId(medication.id), notification)
    }

    fun cancel(medicationId: Long) {
        NotificationManagerCompat.from(context).cancel(doseNotificationId(medicationId))
    }

    override fun notifyLowStock(medication: Medication) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        // 低库存也归同一个提醒等级:选了静默就只是安静地待在通知栏。
        val level = settings.reminderLevel.value
        val notification = NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.stock_alert_title, medication.name))
            .setContentText(
                context.getString(
                    R.string.stock_alert_text,
                    formatAmount(medication.remainingStock),
                    medication.unit,
                ),
            )
            .setAutoCancel(true)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .setContentIntent(contentIntent(lowStockNotificationId(medication.id)))
            .build()

        manager.notify(lowStockNotificationId(medication.id), notification)
    }

    /** 文案模板:剂量 · 计划时间[ · 餐时标签](docs/plan.md §4.1)。 */
    private fun notificationText(medication: Medication, time: String): String {
        val base = context.getString(
            R.string.notification_text,
            formatAmount(medication.defaultDose),
            medication.unit,
            time,
        )
        val mealLabelRes = when (medication.mealTag) {
            MealTag.NONE -> null
            MealTag.BEFORE -> R.string.meal_before
            MealTag.AFTER -> R.string.meal_after
            MealTag.WITH -> R.string.meal_with
        } ?: return base
        return context.getString(R.string.notification_text_with_meal, base, context.getString(mealLabelRes))
    }

    private fun contentIntent(notificationId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun doseAction(action: String, medicationId: Long, scheduledMillis: Long): PendingIntent {
        val intent = Intent(context, DoseActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderExtras.MEDICATION_ID, medicationId)
            putExtra(ReminderExtras.SCHEDULED_AT, scheduledMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(medicationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 跨天后自动撤下,避免昨天的提醒挂到今天。 */
    private fun timeoutMillis(scheduledAt: Instant, now: Instant): Long {
        val zone = ZoneId.systemDefault()
        val endOfDay = scheduledAt.atZone(zone).toLocalDate().plusDays(1).atTime(0, 30).atZone(zone).toInstant()
        return (endOfDay.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(MIN_TIMEOUT_MILLIS)
    }

    companion object {
        private const val MIN_TIMEOUT_MILLIS = 60_000L

        fun doseNotificationId(medicationId: Long): Int = medicationId.hashCode()

        fun lowStockNotificationId(medicationId: Long): Int = "stock:$medicationId".hashCode()
    }
}
