package moe.lizi.kusuri.domain.model

import java.time.Instant

/**
 * 某一计划时刻的一条服药提醒(docs/plan.md §4.1)。
 *
 * 同一 [scheduledAt] 的多条会被折叠成一组通知;它只描述"这一刻该吃什么",
 * 不关心有没有记录——那是调用方按数据库算出来的。
 */
data class DoseAlert(
    val medication: Medication,
    val scheduledAt: Instant,
)
