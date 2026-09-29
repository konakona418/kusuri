package moe.lizi.kusuri.domain

import java.time.Instant
import moe.lizi.kusuri.domain.model.DoseSource
import moe.lizi.kusuri.domain.model.DoseStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 今日列表的排序(docs/plan.md §7):只有**已经不需要动手**的才沉下去。
 */
class TodayOrderTest {

    private data class Item(val scheduledAt: Instant, val status: DoseStatus)

    private fun at(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-09-29T%02d:%02d:00Z".format(hour, minute))

    @Test
    fun `only handled or expired doses sink below`() {
        assertEquals(0, todayLane(DoseStatus.Pending))
        assertEquals(0, todayLane(DoseStatus.Overdue))
        assertEquals(1, todayLane(DoseStatus.Taken(Instant.EPOCH, DoseSource.IN_APP)))
        assertEquals(1, todayLane(DoseStatus.Skipped))
        assertEquals(1, todayLane(DoseStatus.Missed))
        assertEquals(1, todayLane(DoseStatus.Untracked))
    }

    @Test
    fun `a dose that just came due stays on top, ahead of later ones`() {
        val items = listOf(
            Item(at(21), DoseStatus.Pending),
            Item(at(9), DoseStatus.Taken(Instant.EPOCH, DoseSource.IN_APP)),
            // 09:00 到点、还在宽限窗口内:它才是此刻最该处理的一剂,不能沉下去。
            Item(at(9), DoseStatus.Overdue),
            Item(at(11), DoseStatus.Pending),
            Item(at(8), DoseStatus.Missed),
            Item(at(12), DoseStatus.Skipped),
        )

        val ordered = items.sortedWith(todayOrder({ it.scheduledAt }, { it.status }))

        assertEquals(
            "上面:待处理的由早到晚;下面:已处理/已过期的由早到晚",
            listOf(at(9), at(11), at(21), at(8), at(9), at(12)),
            ordered.map { it.scheduledAt },
        )
        assertEquals(
            listOf(
                DoseStatus.Overdue,
                DoseStatus.Pending,
                DoseStatus.Pending,
                DoseStatus.Missed,
                DoseStatus.Taken(Instant.EPOCH, DoseSource.IN_APP),
                DoseStatus.Skipped,
            ),
            ordered.map { it.status },
        )
    }
}
