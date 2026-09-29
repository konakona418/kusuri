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
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.model.ReminderLevel
import moe.lizi.kusuri.domain.util.formatDate
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 通用提醒的通知出口(docs/plan.md §14):
 * 一条提醒同时只有一条通知(用提醒 id 作通知 id),动作只有"知道了"与"稍后 15 分钟"。
 * 等级、渠道与服药提醒完全共用([ReminderChannels]),因为设置里只有一个提醒等级。
 */
class ReminderNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    fun notify(reminder: Reminder, occurrence: Instant, now: Instant, alertAgain: Boolean = false) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val level = settings.reminderLevel.value
        val dueText = dueText(occurrence, now)
        val builder = NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(dueText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            // 同一件事只响一次;补发(重启/巡检时重建)应当是安静的,除非是"稍后"要求再响。
            .setOnlyAlertOnce(!alertAgain)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .setContentIntent(contentIntent(notificationId(reminder.id)))
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_ack),
                action(ReminderActions.ACTION_REMINDER_ACK, reminder.id),
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.action_snooze_15),
                action(ReminderActions.ACTION_REMINDER_SNOOZE_REQUEST, reminder.id),
            )
            .setTimeoutAfter(timeoutMillis(occurrence, now))

        // 备注放在展开态里,收起时只留时间,避免长备注把通知栏挤满。
        reminder.note?.takeIf { it.isNotBlank() }?.let { note ->
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$dueText\n$note"))
        }

        manager.notify(notificationId(reminder.id), builder.build())
    }

    fun cancel(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    /**
     * 发一条当前等级的测试提醒,当场核对响铃/震动/横幅。
     *
     * 刻意**不带任何动作按钮**:它不该能写进任何数据。
     * 返回 false 表示系统层面通知被关掉了——界面要说明,而不是让你点了没反应。
     */
    fun notifyTest(level: ReminderLevel): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val notification = NotificationCompat.Builder(context, ReminderChannels.channelIdFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_test_title))
            .setContentText(
                context.getString(R.string.notification_test_text, context.getString(level.labelRes())),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSilent(level.isSilent)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) applyLegacyLevel(level) }
            .setContentIntent(contentIntent(TEST_NOTIFICATION_ID))
            .build()

        manager.notify(TEST_NOTIFICATION_ID, notification)
        return true
    }

    private fun dueText(occurrence: Instant, now: Instant): String {
        val zone = ZoneId.systemDefault()
        val date = occurrence.atZone(zone).toLocalDate()
        val time = formatTime(occurrence.atZone(zone).toLocalTime())
        return if (date == now.atZone(zone).toLocalDate()) {
            context.getString(R.string.notification_reminder_due_today, time)
        } else {
            context.getString(R.string.notification_reminder_due_other, formatDate(date), time)
        }
    }

    /** 跨天后自动撤下,避免昨天的提醒挂到今天。 */
    private fun timeoutMillis(occurrence: Instant, now: Instant): Long {
        val zone = ZoneId.systemDefault()
        val endOfDay = occurrence.atZone(zone).toLocalDate().plusDays(1).atTime(0, 30).atZone(zone).toInstant()
        return (endOfDay.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(MIN_TIMEOUT_MILLIS)
    }

    private fun contentIntent(notificationId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(action: String, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            // 身份放 data:有的 ROM 会把广播 extras 剥掉(见 ReminderUris)。
            data = ReminderUris.reminder(reminderId)
            putExtra(ReminderExtras.REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val MIN_TIMEOUT_MILLIS = 60_000L

        fun notificationId(reminderId: Long): Int = "reminder:$reminderId".hashCode()

        /** 测试提醒固定一个 id:连着点几次只会替换,不会堆一屏。 */
        val TEST_NOTIFICATION_ID: Int = "kusuri-test-reminder".hashCode()
    }
}
