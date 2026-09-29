package moe.lizi.kusuri.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.ReminderLevel

/**
 * **唯一**的提醒渠道对(docs/plan.md §4.1、§14)。
 *
 * 服药提醒、通用提醒、低库存提醒都按全局"提醒等级"选这两条渠道之一:
 * 一个等级管所有通知,不再按类型分开关。渠道的重要性创建后应用改不动(平台约束),
 * 所以一档一条渠道,发送时按设置选一条。
 */
object ReminderChannels {

    /** 渠道 id 沿用旧名:改名会让用户在系统设置里对这些渠道的微调失效,不值得。 */
    const val SILENT_CHANNEL_ID = "dose_reminders_silent"
    const val BANNER_CHANNEL_ID = "dose_reminders_banner"

    /** 已废弃:旧版"响铃"、试过的两个中间档,以及低库存自己的渠道(现已并入统一等级)。 */
    private val RETIRED_CHANNEL_IDS = listOf(
        "dose_reminders_alert",
        "dose_reminders_vibrate",
        "dose_reminders_sound",
        "stock_alerts",
    )

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 只留两个语义无歧义的极端档:静默(LOW,无声无震)与横幅(HIGH,响铃+震动+顶部弹出)。
        createChannel(
            context,
            manager,
            SILENT_CHANNEL_ID,
            R.string.channel_reminders_silent_name,
            R.string.channel_reminders_silent_description,
            NotificationManager.IMPORTANCE_LOW,
        ) {
            enableVibration(false)
            setSound(null, null)
        }
        createChannel(
            context,
            manager,
            BANNER_CHANNEL_ID,
            R.string.channel_reminders_banner_name,
            R.string.channel_reminders_banner_description,
            NotificationManager.IMPORTANCE_HIGH,
        ) {
            enableVibration(true)
        }
        // 留着废弃渠道只会让系统设置里多出永不触发的项,更让人以为哪里坏了。
        RETIRED_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    }

    /** 等级 → 渠道(docs/plan.md §4.1);渠道与等级一一对应,不做动态切换。 */
    fun channelIdFor(level: ReminderLevel): String = when (level) {
        ReminderLevel.SILENT -> SILENT_CHANNEL_ID
        ReminderLevel.BANNER -> BANNER_CHANNEL_ID
    }

    private fun createChannel(
        context: Context,
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
        // 已存在时:名称与描述官方允许更新;重要性/声音/震动创建后归用户掌控,应用改不动也不该改。
        existing.name = name
        existing.description = description
        manager.createNotificationChannel(existing)
    }
}

/** Android 8 以下没有渠道:等级只能落在通知自身的 priority 与声音/震动上。 */
internal fun NotificationCompat.Builder.applyLegacyLevel(level: ReminderLevel) {
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

private val VIBRATION_PATTERN = longArrayOf(0L, 300L, 200L, 300L)
