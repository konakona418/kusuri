package moe.lizi.kusuri.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
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
import moe.lizi.kusuri.domain.LowStockAlertControl
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 通知出口:
 * - 服药提醒:每味药同时只保留一条通知(用 medicationId 作为 id),下一剂到点替换上一剂;
 * - 低库存提醒:一次性的补药提示。
 */
class DoseNotifier(private val context: Context) : LowStockAlertControl {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DOSE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    DOSE_CHANNEL_ID,
                    context.getString(R.string.channel_dose_reminders_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_dose_reminders_description)
                    enableVibration(true)
                },
            )
        }
        if (manager.getNotificationChannel(STOCK_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    STOCK_CHANNEL_ID,
                    context.getString(R.string.channel_stock_alerts_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_stock_alerts_description)
                },
            )
        }
    }

    fun notify(medication: Medication, scheduledAt: Instant, now: Instant) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val scheduledMillis = scheduledAt.toEpochMilli()
        val time = formatTime(scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime())

        val notification = NotificationCompat.Builder(context, DOSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, medication.name))
            .setContentText(notificationText(medication, time))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
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

        val notification = NotificationCompat.Builder(context, STOCK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.stock_alert_title, medication.name))
            .setContentText(
                context.getString(
                    R.string.stock_alert_text,
                    formatAmount(medication.remainingStock),
                    medication.unit,
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
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
        const val DOSE_CHANNEL_ID = "dose_reminders"
        const val STOCK_CHANNEL_ID = "stock_alerts"
        private const val MIN_TIMEOUT_MILLIS = 60_000L

        fun doseNotificationId(medicationId: Long): Int = medicationId.hashCode()

        fun lowStockNotificationId(medicationId: Long): Int = "stock:$medicationId".hashCode()
    }
}
