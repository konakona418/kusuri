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
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.ReminderLevel
import moe.lizi.kusuri.domain.util.formatAmount
import moe.lizi.kusuri.domain.util.formatTime

/**
 * 通知出口:
 * - 服药提醒:每味药同时只保留一条通知(用 medicationId 作为 id),下一剂到点替换上一剂;
 * - 低库存提醒:一次性的补药提示。
 */
class DoseNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
) : LowStockAlertControl {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 渠道的重要性创建后不可改(平台约束),所以一个等级一条渠道,发送时按设置选一条。
        // 只留两个语义无歧义的极端档:静默(LOW,无声无震)与横幅(HIGH,响铃+震动+顶部弹出)。
        createChannel(
            manager,
            SILENT_CHANNEL_ID,
            R.string.channel_dose_silent_name,
            R.string.channel_dose_silent_description,
            NotificationManager.IMPORTANCE_LOW,
        ) {
            enableVibration(false)
            setSound(null, null)
        }
        createChannel(
            manager,
            BANNER_CHANNEL_ID,
            R.string.channel_dose_banner_name,
            R.string.channel_dose_banner_description,
            NotificationManager.IMPORTANCE_HIGH,
        ) {
            enableVibration(true)
        }
        createChannel(
            manager,
            STOCK_CHANNEL_ID,
            R.string.channel_stock_alerts_name,
            R.string.channel_stock_alerts_description,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        // 清掉不再使用的渠道:旧版的"响铃",以及试过又撤掉的"只震动 / 响亮"两档。
        // 留着只会让系统设置里多出永不触发的项,更让人以为哪里坏了。
        (RETIRED_CHANNEL_IDS + LEGACY_ALERT_CHANNEL_ID).forEach(manager::deleteNotificationChannel)
    }

    private fun createChannel(
        manager: NotificationManager,
        id: String,
        nameRes: Int,
        descriptionRes: Int,
        importance: Int,
        configure: NotificationChannel.() -> Unit = {},
    ) {
        val name = context.getString(nameRes)
        val description = context.getString(descriptionRes)
        val existing = manager.getNotificationChannel(id)
        if (existing == null) {
            manager.createNotificationChannel(
                NotificationChannel(id, name, importance).apply {
                    this.description = description
                    configure()
                },
            )
            return
        }
        // 已存在时:名称与描述官方允许更新,更新的就是它们;
        // 重要性/声音/震动创建后归用户掌控,应用改不动也不该改。
        existing.name = name
        existing.description = description
        manager.createNotificationChannel(existing)
    }

    fun notify(medication: Medication, scheduledAt: Instant, now: Instant) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val level = settings.reminderLevel.value
        val scheduledMillis = scheduledAt.toEpochMilli()
        val time = formatTime(scheduledAt.atZone(ZoneId.systemDefault()).toLocalTime())

        val notification = NotificationCompat.Builder(context, channelIdFor(level))
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

    /** Android 8 以下没有渠道:等级只能落在通知自身的 priority 与声音/震动上。 */
    private fun NotificationCompat.Builder.applyLegacyLevel(level: ReminderLevel) {
        when (level) {
            ReminderLevel.SILENT -> {
                setPriority(NotificationCompat.PRIORITY_LOW)
                setSound(null)
                setVibrate(longArrayOf(0L))
            }

            ReminderLevel.BANNER -> {
                setPriority(NotificationCompat.PRIORITY_HIGH)
                setVibrate(VIBRATION_PATTERN)
            }
        }
    }

    fun cancel(medicationId: Long) {
        NotificationManagerCompat.from(context).cancel(doseNotificationId(medicationId))
    }

    /**
     * 发一条当前等级的测试提醒,让你当场核对响铃/震动/横幅。
     *
     * 刻意**不带任何动作按钮**:它不该能写进任何服药记录。
     * 返回 false 表示系统层面通知被关掉了——界面要说明,而不是让你点了没反应。
     */
    fun notifyTest(level: ReminderLevel): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val notification = NotificationCompat.Builder(context, channelIdFor(level))
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
        const val SILENT_CHANNEL_ID = "dose_reminders_silent"
        const val BANNER_CHANNEL_ID = "dose_reminders_banner"
        const val STOCK_CHANNEL_ID = "stock_alerts"

        /** 旧版的"响铃"渠道;已被两档取代,只在 [ensureChannel] 里删掉。 */
        private const val LEGACY_ALERT_CHANNEL_ID = "dose_reminders_alert"

        /** 试过又撤掉的两个中间档(只震动 / 响亮):同样只在启动时清掉。 */
        private val RETIRED_CHANNEL_IDS = listOf("dose_reminders_vibrate", "dose_reminders_sound")

        private val VIBRATION_PATTERN = longArrayOf(0L, 300L, 200L, 300L)
        private const val MIN_TIMEOUT_MILLIS = 60_000L

        /** 等级 → 渠道(docs/plan.md §4.1);渠道与等级一一对应,不做动态切换。 */
        fun channelIdFor(level: ReminderLevel): String = when (level) {
            ReminderLevel.SILENT -> SILENT_CHANNEL_ID
            ReminderLevel.BANNER -> BANNER_CHANNEL_ID
        }

        fun doseNotificationId(medicationId: Long): Int = medicationId.hashCode()

        fun lowStockNotificationId(medicationId: Long): Int = "stock:$medicationId".hashCode()

        /** 测试提醒固定一个 id:连着点几次只会替换,不会堆一屏。 */
        val TEST_NOTIFICATION_ID: Int = "kusuri-test-reminder".hashCode()
    }
}
