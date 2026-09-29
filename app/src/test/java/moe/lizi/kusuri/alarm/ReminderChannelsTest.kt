package moe.lizi.kusuri.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.domain.model.DoseAlert
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
 * 提醒等级 ↔ 通知渠道、以及同一时刻的折叠(docs/plan.md §4.1、§14)。
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

            notifier.show(
                scheduledAt = scheduledAt,
                recordedMedicationIds = emptyList(),
                pending = listOf(DoseAlert(medication, scheduledAt)),
                now = scheduledAt,
            )

            assertEquals(channelId, shadowOf(manager).allNotifications.last().channelId)
        }
    }

    @Test
    fun `doses at the same instant fold into one group that alerts once`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)

        notifier.show(
            scheduledAt = scheduledAt,
            recordedMedicationIds = emptyList(),
            pending = threeDoses(),
            now = scheduledAt,
        )

        val posted = shadowOf(manager).allNotifications
        assertEquals("三味药各一条 + 一条组摘要", 4, posted.size)

        val summary = posted.single { it.isGroupSummary() }
        val children = posted.filterNot { it.isGroupSummary() }
        assertEquals(3, children.size)
        assertTrue("子通知与摘要必须同组", children.all { it.group == summary.group })
        assertEquals("组摘要列出药名", "二甲双胍、布洛芬、阿司匹林", summary.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(
            "组内只响一次:响声交给摘要",
            summary.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
        )
        assertTrue(
            "子通知本身不该响",
            children.all { it.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 },
        )
    }

    @Test
    fun `when only one dose is left it is posted on its own and the summary goes away`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)
        notifier.show(
            scheduledAt = scheduledAt,
            recordedMedicationIds = emptyList(),
            pending = threeDoses(),
            now = scheduledAt,
        )

        notifier.show(
            scheduledAt = scheduledAt,
            recordedMedicationIds = listOf(1L, 2L),
            pending = listOf(DoseAlert(medication.copy(id = 3L, name = "阿司匹林"), scheduledAt)),
            now = scheduledAt,
        )

        val posted = shadowOf(manager).allNotifications
        assertEquals("已处理的两条与摘要都被撤下,只剩那一味", 1, posted.size)
        assertTrue(
            "标题里是剩下的那一味",
            posted.single().extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("阿司匹林"),
        )
        assertFalse(posted.single().isGroupSummary())
        assertNull(posted.single().group)
    }

    @Test
    fun `recording a dose only renumbers the summary and leaves the other cards alone`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)
        notifier.show(scheduledAt, emptyList(), threeDoses(), scheduledAt)

        notifier.refresh(
            scheduledAt = scheduledAt,
            recordedMedicationIds = listOf(1L),
            pending = threeDoses().drop(1),
            now = scheduledAt,
        )

        val posted = shadowOf(manager).allNotifications
        assertEquals("撤掉一味、摘要改数字,剩下的原地不动(不新增也不重挂)", 3, posted.size)
        val summary = posted.single { it.isGroupSummary() }
        assertEquals(
            "摘要里只剩还没处理的药名",
            "布洛芬、阿司匹林",
            summary.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `recording never resurrects a notification that is no longer in the shade`() {
        // "记录一味药会让别的药再响一遍"的病根:重挂一条不在栏里的通知,系统视为新通知、会响。
        // 按下「已服用」后系统把通知收走(或用户划过、被清理),剩下的药不该再弹一次。
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)
        notifier.show(scheduledAt, emptyList(), threeDoses(), scheduledAt)
        manager.cancelAll()

        notifier.refresh(
            scheduledAt = scheduledAt,
            recordedMedicationIds = listOf(1L),
            pending = threeDoses().drop(1),
            now = scheduledAt,
        )

        assertTrue("不在通知栏里的就不在,刷新不许复活", shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `the last remaining dose folds back to a standalone card while it is still shown`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)
        notifier.show(scheduledAt, emptyList(), threeDoses(), scheduledAt)

        notifier.refresh(
            scheduledAt = scheduledAt,
            recordedMedicationIds = listOf(1L, 2L),
            pending = listOf(DoseAlert(medication.copy(id = 3L, name = "阿司匹林"), scheduledAt)),
            now = scheduledAt,
        )

        val posted = shadowOf(manager).allNotifications
        assertEquals("已经处理的两条与摘要撤下,只剩那一味", 1, posted.size)
        assertNull("退回单独一条,不再挂组", posted.single().group)
        assertFalse(posted.single().isGroupSummary())
    }

    @Test
    fun `nothing is posted before the planned time`() {
        ReminderChannels.ensure(context)
        settings.setReminderLevel(ReminderLevel.BANNER)

        // 在 App 里提前记录会走到这里:那一刻还没到,不该把提醒挂出来。
        notifier.show(
            scheduledAt = scheduledAt,
            recordedMedicationIds = emptyList(),
            pending = threeDoses(),
            now = scheduledAt.minusSeconds(600),
        )

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
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

    private fun threeDoses() = listOf(
        DoseAlert(medication, scheduledAt),
        DoseAlert(medication.copy(id = 2L, name = "布洛芬"), scheduledAt),
        DoseAlert(medication.copy(id = 3L, name = "阿司匹林"), scheduledAt),
    )

    private fun Notification.isGroupSummary(): Boolean =
        flags and Notification.FLAG_GROUP_SUMMARY != 0

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
