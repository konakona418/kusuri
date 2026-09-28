package moe.lizi.kusuri.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.model.MealTag
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.ReminderLevel
import moe.lizi.kusuri.domain.model.Schedule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 提醒等级 ↔ 通知渠道的契约(docs/plan.md §4.1)。
 *
 * 渠道的重要性创建后不可改,所以"等级"只能靠"一条渠道一个等级"实现;
 * 这一组测试盯住的就是这张映射表,以及发送时确实走了对应渠道。
 */
@RunWith(RobolectricTestRunner::class)
class DoseNotifierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val settings = SettingsRepository(context)
    private val notifier = DoseNotifier(context, settings)

    @Test
    fun `each level has a channel with the documented importance`() {
        notifier.ensureChannel()

        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channel(DoseNotifier.channelIdFor(ReminderLevel.SILENT)).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            channel(DoseNotifier.channelIdFor(ReminderLevel.VIBRATE)).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            channel(DoseNotifier.channelIdFor(ReminderLevel.SOUND)).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            channel(DoseNotifier.channelIdFor(ReminderLevel.BANNER)).importance,
        )
    }

    @Test
    fun `only silent is quiet and only silent stops vibrating`() {
        notifier.ensureChannel()

        assertNull(channel(DoseNotifier.SILENT_CHANNEL_ID).sound)
        assertNull(channel(DoseNotifier.VIBRATE_CHANNEL_ID).sound)
        assertFalse(channel(DoseNotifier.SILENT_CHANNEL_ID).shouldVibrate())
        assertTrue(channel(DoseNotifier.VIBRATE_CHANNEL_ID).shouldVibrate())
        assertTrue(channel(DoseNotifier.SOUND_CHANNEL_ID).shouldVibrate())
        assertTrue(channel(DoseNotifier.BANNER_CHANNEL_ID).shouldVibrate())
    }

    @Test
    fun `the stock alert keeps its own channel`() {
        notifier.ensureChannel()

        assertNotNull(manager.getNotificationChannel(DoseNotifier.STOCK_CHANNEL_ID))
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            channel(DoseNotifier.STOCK_CHANNEL_ID).importance,
        )
    }

    @Test
    fun `the legacy alert channel is cleaned up`() {
        manager.createNotificationChannel(
            NotificationChannel("dose_reminders_alert", "old", NotificationManager.IMPORTANCE_HIGH),
        )

        notifier.ensureChannel()

        assertNull(manager.getNotificationChannel("dose_reminders_alert"))
    }

    @Test
    fun `an existing channel keeps its configuration but picks up the current name`() {
        // 名称与描述创建后可更新;重要性/声音/震动创建后归用户掌控——这条边界必须守住。
        // 这里只能断言 Robolectric 可靠建模的字段;"应用不去动用户的声响/震动"由更新分支的
        // 结构保证(那里不调 enableVibration/setSound),并在真机 dumpsys 上核对过。
        manager.createNotificationChannel(
            NotificationChannel(
                DoseNotifier.BANNER_CHANNEL_ID,
                "旧名字",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        notifier.ensureChannel()

        val channel = channel(DoseNotifier.BANNER_CHANNEL_ID)
        assertEquals(1, manager.notificationChannels.count { it.id == DoseNotifier.BANNER_CHANNEL_ID })
        assertEquals(
            context.getString(moe.lizi.kusuri.R.string.channel_dose_banner_name),
            channel.name,
        )
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `a dose reminder is posted to the channel of the chosen level`() {
        notifier.ensureChannel()

        listOf(
            ReminderLevel.SILENT to DoseNotifier.SILENT_CHANNEL_ID,
            ReminderLevel.VIBRATE to DoseNotifier.VIBRATE_CHANNEL_ID,
            ReminderLevel.SOUND to DoseNotifier.SOUND_CHANNEL_ID,
            ReminderLevel.BANNER to DoseNotifier.BANNER_CHANNEL_ID,
        ).forEach { (level, channelId) ->
            settings.setReminderLevel(level)

            notifier.notify(medication, scheduledAt, scheduledAt)

            val posted = shadowOf(manager).allNotifications.last()
            assertEquals(channelId, posted.channelId)
        }
    }

    @Test
    fun `a test reminder goes to the chosen channel and carries no actions`() {
        notifier.ensureChannel()

        assertTrue(notifier.notifyTest(ReminderLevel.SOUND))
        assertTrue(notifier.notifyTest(ReminderLevel.SOUND))

        val notifications = shadowOf(manager).allNotifications
        assertEquals("固定 id:连点几次只替换,不堆一屏", 1, notifications.size)
        val posted = notifications.first()
        assertEquals(DoseNotifier.SOUND_CHANNEL_ID, posted.channelId)
        assertEquals("测试提醒不能带动作,否则会写进真实记录", 0, posted.actions?.size ?: 0)
    }

    @Test
    fun `a test reminder reports when the system blocks notifications`() {
        notifier.ensureChannel()
        shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(notifier.notifyTest(ReminderLevel.BANNER))

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
