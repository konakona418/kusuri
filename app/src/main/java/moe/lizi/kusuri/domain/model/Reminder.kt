package moe.lizi.kusuri.domain.model

import java.time.Instant

/** 通用提醒的重复规则(docs/plan.md §14)。 */
enum class ReminderRepeatKind {
    /** 一次性(复诊、取药)。 */
    ONCE,

    /** 每天同一钟点。 */
    DAILY,

    /** 每周同一星期几、同一钟点。 */
    WEEKLY,

    /** 每月同一日(短月回退到月末,如 31 号 → 2 月 28/29 号)。 */
    MONTHLY,

    /** 每 N 天(自定义间隔)。 */
    EVERY_N_DAYS,

    /** 每 N 月(自定义间隔);同样按"锚定日"回退短月。 */
    EVERY_N_MONTHS,
}

/**
 * 通用提醒:复诊、取药、复查、量血压……(docs/plan.md §14)。
 *
 * 只存**第一次**的时刻 + 重复规则,之后每一次都由规则算出来——
 * 与服药排程同一套思路:派生而非展开,改规则时不会留下历史垃圾。
 */
data class Reminder(
    val id: Long = 0L,
    val title: String,
    /** 第一次(或唯一一次)提醒时刻。 */
    val at: Instant,
    val repeatKind: ReminderRepeatKind = ReminderRepeatKind.ONCE,
    /** 仅 [ReminderRepeatKind.EVERY_N_DAYS] / [ReminderRepeatKind.EVERY_N_MONTHS] 使用,至少 1。 */
    val interval: Int = 1,
    /** 选填的备注,显示在通知的展开态里。 */
    val note: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    /** 一次性提醒被"知道了"的时刻;重复提醒不使用(它没有"完成"这一说)。 */
    val doneAt: Instant? = null,
) {
    val repeats: Boolean get() = repeatKind != ReminderRepeatKind.ONCE

    /** 供界面显示"每 N 天/月"用的安全间隔。 */
    val safeInterval: Int get() = interval.coerceAtLeast(1)
}
