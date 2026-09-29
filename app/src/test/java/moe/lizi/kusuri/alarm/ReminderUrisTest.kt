package moe.lizi.kusuri.alarm

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 通知/闹钟广播的身份解析(docs/plan.md §4.1)。
 *
 * 背景:实测小米 HyperOS 会把后台广播的 extras 剥成空(`Bundle[{STRIPPED=1}]`),
 * 所以身份优先从 Intent 的 data 里取,extras 只作兼容回落。
 */
@RunWith(RobolectricTestRunner::class)
class ReminderUrisTest {

    @Test
    fun `a dose uri round trips`() {
        val uri = ReminderUris.dose(medicationId = 42L, scheduledMillis = 1_790_000_000_000L)

        assertEquals(42L to 1_790_000_000_000L, ReminderUris.parse(uri))
    }

    @Test
    fun `a reminder uri works with and without an occurrence`() {
        assertEquals(7L to null, ReminderUris.parse(ReminderUris.reminder(7L)))
        assertEquals(
            7L to 1_790_000_000_000L,
            ReminderUris.parse(ReminderUris.reminder(7L, 1_790_000_000_000L)),
        )
    }

    @Test
    fun `foreign uris are ignored`() {
        assertNull(ReminderUris.parse(Uri.parse("https://example.com/1/2")))
        assertNull(ReminderUris.parse(Uri.parse("kusuri://nope/1/2")))
        assertNull(ReminderUris.parse(Uri.parse("kusuri://dose/not-a-number/2")))
        assertNull(ReminderUris.parse(null))
    }

    @Test
    fun `a dose target prefers the data uri`() {
        val intent = Intent()
            .setData(ReminderUris.dose(3L, 999L))
            .putExtra(ReminderExtras.MEDICATION_ID, 111L)
            .putExtra(ReminderExtras.SCHEDULED_AT, 222L)

        assertEquals(3L to 999L, intent.doseTarget())
    }

    @Test
    fun `a dose target falls back to extras when the rom strips the data away`() {
        val intent = Intent()
            .putExtra(ReminderExtras.MEDICATION_ID, 5L)
            .putExtra(ReminderExtras.SCHEDULED_AT, 123L)

        assertEquals(5L to 123L, intent.doseTarget())
    }

    @Test
    fun `a target is null when nothing survives`() {
        // 这正是 HyperOS 上发生的情况:extras 被剥空。接收器据此走"整轮巡检兜底"。
        assertNull(Intent().doseTarget())
        assertNull(Intent().reminderTarget())
    }
}
