package moe.lizi.kusuri.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.ReminderLevel
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 提醒等级 ↔ 通知渠道的契约(docs/plan.md §4.1、§14)。
 *
 * 渠道的重要性创建后不可改,所以"等级"只能靠"一条渠道一个等级"实现;
 * 而且**只有一个等级**:服药、通用提醒、低库存都走这两条渠道之一。
 */
@RunWith(RobolectricTestRunner::class)
class ReminderChannelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val settings = SettingsRepository(context)
    private val notifier = DoseNotifier(context, settings)
    private val reminderNotifier = ReminderNotifier(context, settings)

    @Test
    fun `each level has a channel with the documented importance`() {
        ReminderChannels.ensure(context)

        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channel(ReminderChannels.channelIdFor(ReminderLevel.SILENT)).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            channel(ReminderChannels.channelIdFor(ReminderLevel.BANNER)).importance,
        )
    }

    @Test
    fun `only the silent channel is quiet and does not vibrate`() {
        ReminderChannels.ensure(context)

        assertNull(channel(ReminderChannels.SILENT_CHANNEL_ID).sound)
        assertFalse(channel(ReminderChannels.SILENT_CHANNEL_ID).shouldVibrate())
        assertTrue(channel(ReminderChannels.BANNER_CHANNEL_ID).shouldVibrate())
    }

    @Test
    fun `only the two level channels exist and retired ones are cleaned up`() {
        // 低库存曾有自己的渠道,旧版还有响铃与两个中间档:都不该再出现在系统设置里。
        listOf(
            "stock_alerts",
            "dose_reminders_alert",
            "dose_reminders_vibrate",
            "dose_reminders_sound",
        ).forEach { id ->
            manager.createNotificationChannel(
                NotificationChannel(id, "旧渠道", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        ReminderChannels.ensure(context)

        listOf(
            "stock_alerts",
            "dose_reminders_alert",
            "dose_reminders_vibrate",
            "dose_reminders_sound",
        ).forEach { id ->
            assertNull("旧渠道 $id 应当被删掉", manager.getNotificationChannel(id))
        }
        assertEquals("只剩统一的静默 / 横幅两条", 2, manager.notificationChannels.size)
    }

    @Test
    fun `an existing channel keeps its configuration but picks up the current name`() {
        // 名称与描述创建后可更新;重要性/声音/震动创建后归用户掌控——这条边界必须守住。
        // 这里只断言 Robolectric 可靠建模的字段;"应用不去动用户的声响/震动"由更新分支的
        // 结构保证(那里不调 enableVibration/setSound),并在真机 dumpsys 上核对过。
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderChannels.BANNER_CHANNEL_ID,
                "旧名字",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        ReminderChannels.ensure(context)

        val channel = channel(ReminderChannels.BANNER_CHANNEL_ID)
        assertEquals(context.getString(R.string.channel_reminders_banner_name), channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `a dose reminder is posted to the channel of the chosen level`() {
        ReminderChannels.ensure(context)

        listOf(
            ReminderLevel.SILENT to ReminderChannels.SILENT_CHANNEL_ID,
            ReminderLevel.BANNER to ReminderChannels.BANNER_CHANNEL_ID,
        ).forEach { (level, channelId) ->
            settings.setReminderLevel(level)

            notifier.notify(medication, scheduledAt, scheduledAt)

            assertEquals(channelId, shadowOf(manager).allNotifications.last().channelId)
        }
    }

    @Test
    fun `a low stock alert follows the same level as everything else`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.SILENT)

        notifier.notifyLowStock(medication)

        assertEquals(
            "低库存也归同一个提醒等级,不再有自己的渠道",
            ReminderChannels.SILENT_CHANNEL_ID,
            shadowOf(manager).allNotifications.last().channelId,
        )
    }

    @Test
    fun `a test reminder goes to the chosen channel and carries no actions`() {
        ReminderChannels.ensure(context)

        assertTrue(reminderNotifier.notifyTest(ReminderLevel.BANNER))
        assertTrue(reminderNotifier.notifyTest(ReminderLevel.BANNER))

        val notifications = shadowOf(manager).allNotifications
        assertEquals("固定 id:连点几次只替换,不堆一屏", 1, notifications.size)
        val posted = notifications.first()
        assertEquals(ReminderChannels.BANNER_CHANNEL_ID, posted.channelId)
        assertEquals("测试提醒不能带动作,否则会写进真实数据", 0, posted.actions?.size ?: 0)
    }

    @Test
    fun `a test reminder reports when the system blocks notifications`() {
        ReminderChannels.ensure(context)
        shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(reminderNotifier.notifyTest(ReminderLevel.BANNER))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    private fun channel(id: String): NotificationChannel =
        requireNotNull(manager.getNotificationChannel(id)) { "渠道 $id 没建出来" }

    private val scheduledAt: Instant = Instant.parse("2026-09-28T09:00:00Z")

    private val medication = Medication(
        id = 1L,
        name = "二甲双胍",
        unit = "粒",
        defaultDose = 0.5,
        mealTag = MealTag.NONE,
        notes = null,
        status = MedicationStatus.ACTIVE,
        createdAt = Instant.EPOCH,
        courseStart = LocalDate.of(2026, 9, 1),
        courseEnd = null,
        schedule = Schedule.DailyTimes(listOf(LocalTime.of(8, 0))),
        lowStockThreshold = 5.0,
        stockAlertArmed = true,
        remainingStock = 0.0,
    )
}
