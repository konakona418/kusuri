package moe.lizi.kusuri.alarm

import android.app.Notification
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
import moe.lizi.kusuri.domain.DoseAlertControl
import moe.lizi.kusuri.domain.LowStockAlertControl
import moe.lizi.kusuri.domain.model.DoseAlert
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.ReminderLevel
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 服药提醒的通知出口(docs/plan.md §4.1)。
 *
 * **同一计划时刻的多味药折叠成一组**:组摘要列出药名、收起时只看这一行,展开后每味药
 * 各自带着自己的「已服用 / 稍后 / 跳过」。组内**只响一次**(靠 `GROUP_ALERT_SUMMARY` +
 * 摘要的 `setOnlyAlertOnce`),否则折叠只省了版面、没省打扰。
 *
 * 通知 id 是确定的(药 id / 时刻),所以任何一次重建都是原地更新,不会堆一屏;
 * 内容由调用方按数据库算好([sync]),这里只管怎么显示。
 */
class DoseNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) : LowStockAlertControl, DoseAlertControl {

    override fun sync(
        scheduledAt: Instant,
        recordedMedicationIds: List<Long>,
        pending: List<DoseAlert>,
        now: Instant,
        alertAgain: Boolean,
    ) {
        val manager = NotificationManagerCompat.from(context)
        recordedMedicationIds.forEach { cancel(it) }
        if (!manager.areNotificationsEnabled()) return
        if (pending.isEmpty()) {
            cancelGroup(scheduledAt)
            return
        }
        // 还没到点(在 App 里提前记录),或者这一天早就过去了(补记历史):都不挂通知。
        val expiresIn = millisUntilDayEnd(scheduledAt, now)
        if (now < scheduledAt || expiresIn <= 0L) return

        val level = settings.reminderLevel.value
        if (pending.size == 1) {
            // 只剩一味药就别摆组了:单独一条,药名与剂量都在标题上。
            cancelGroup(scheduledAt)
            manager.notify(
                doseNotificationId(pending.single().medication.id),
                buildDose(pending.single(), level, now, groupKey = null, alertAgain = alertAgain),
            )
            return
        }

        val groupKey = groupKey(scheduledAt)
        pending.forEach { alert ->
            manager.notify(
                doseNotificationId(alert.medication.id),
                buildDose(alert, level, now, groupKey = groupKey, alertAgain = false),
            )
        }
        manager.notify(
            groupNotificationId(scheduledAt),
            buildGroup(pending, level, now, alertAgain = alertAgain),
        )
    }

    override fun cancel(medicationId: Long) {
        NotificationManagerCompat.from(context).cancel(doseNotificationId(medicationId))
    }

    override fun cancelGroup(scheduledAt: Instant) {
        NotificationManagerCompat.from(context).cancel(groupNotificationId(scheduledAt))
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

    private fun buildDose(
        alert: DoseAlert,
        level: ReminderLevel,
        now: Instant,
        groupKey: String?,
        alertAgain: Boolean,
    ): Notification {
        val medication = alert.medication
        val scheduledMillis = alert.scheduledAt.toEpochMilli()
        val time = formatTime(alert.scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime())

        return NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title, medication.name))
            .setContentText(notificationText(medication, time))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alertAgain)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .apply {
                if (groupKey != null) {
                    // 组内只响一次:声音交给摘要(见 buildGroup)。
                    setGroup(groupKey)
                    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                }
            }
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
            .setTimeoutAfter(timeoutMillis(alert.scheduledAt, now))
            .build()
    }

    /** 组摘要:收起时看到的那一行(药名),点开看每条药的按钮。 */
    private fun buildGroup(
        pending: List<DoseAlert>,
        level: ReminderLevel,
        now: Instant,
        alertAgain: Boolean,
    ): Notification {
        val scheduledAt = pending.first().scheduledAt
        val names = pending.joinToString(separator = "、") { it.medication.name }

        return NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_group_title, pending.size))
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            // 组的第一次展示会响;之后的重建(记录一条、稍后重排)都安静,除非要重新提醒。
            .setOnlyAlertOnce(!alertAgain)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .setGroup(groupKey(scheduledAt))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setContentIntent(contentIntent(groupNotificationId(scheduledAt)))
            .setTimeoutAfter(timeoutMillis(scheduledAt, now))
            .build()
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
            // 身份放 data:有的 ROM 会把广播 extras 剥掉(见 ReminderUris)。
            data = ReminderUris.dose(medicationId, scheduledMillis)
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

    /** 距该日"撤下通知"的时刻还有多久(负数表示这一天已经过去)。 */
    private fun millisUntilDayEnd(scheduledAt: Instant, now: Instant): Long {
        val zone = ZoneId.systemDefault()
        val endOfDay = scheduledAt.atZone(zone).toLocalDate().plusDays(1).atTime(0, 30).atZone(zone).toInstant()
        return endOfDay.toEpochMilli() - now.toEpochMilli()
    }

    /** 跨天后自动撤下,避免昨天的提醒挂到今天。 */
    private fun timeoutMillis(scheduledAt: Instant, now: Instant): Long =
        millisUntilDayEnd(scheduledAt, now).coerceAtLeast(MIN_TIMEOUT_MILLIS)

    companion object {
        private const val MIN_TIMEOUT_MILLIS = 60_000L

        fun doseNotificationId(medicationId: Long): Int = medicationId.hashCode()

        /** 同一计划时刻共用一个通知 id:重建时原地更新,不会堆一屏。 */
        fun groupNotificationId(scheduledAt: Instant): Int =
            "dose-group:${scheduledAt.epochSecond}".hashCode()

        /** 系统通知分组的 key:完全相同的计划时刻才归一组。 */
        fun groupKey(scheduledAt: Instant): String = "dose-group:${scheduledAt.epochSecond}"

        fun lowStockNotificationId(medicationId: Long): Int = "stock:$medicationId".hashCode()
    }
}
