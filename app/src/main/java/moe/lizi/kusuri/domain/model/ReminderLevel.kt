package moe.lizi.kusuri.domain.model

/**
 * 服药提醒的等级,对应 Android 通知渠道的重要性(docs/plan.md §4.1)。
 *
 * **只有两个语义无歧义的极端档**,这是刻意的:
 *
 * - 平台定义里 `IMPORTANCE_LOW` 就是"无声",`IMPORTANCE_DEFAULT` 及以上才"应该有声音"
 *   (NotificationChannel 参考文档);把"只震动"这类组合塞进 LOW,**与平台模型打架**,
 *   各家系统 UI 的处理还不一致,用户往往只能去系统设置里手动收拾。
 * - `IMPORTANCE_DEFAULT` 与 `IMPORTANCE_HIGH` 在多数系统 UI 里被折叠成同一档,
 *   分成"响亮/横幅"两个选项看不出区别,反而让人以为选错了。
 *
 * 另一个硬约束:渠道的重要性一旦创建就不能再改,所以"等级"只能是"用哪条渠道"——
 * 一个等级一条渠道,发送时按当前等级选渠道。用户在系统设置里对该渠道的微调会被尊重。
 */
enum class ReminderLevel {
    /** 不响不震,只在通知栏显示(`IMPORTANCE_LOW`,无声音无震动)。 */
    SILENT,

    /** 响铃 + 震动 + 屏幕顶部横幅(`IMPORTANCE_HIGH`)。 */
    BANNER,
    ;

    val isSilent: Boolean get() = this == SILENT

    /** 是否震动(横幅档震动,静默档不震)。 */
    val vibrates: Boolean get() = this == BANNER

    companion object {
        /** 默认沿用旧版的"响铃"行为(旧渠道就是 `IMPORTANCE_HIGH`,即横幅)。 */
        val DEFAULT: ReminderLevel = BANNER

        /** 已废弃的两个中间档:`只震动` 归入静默、`响亮` 归入横幅(详情见类注释)。 */
        private val RETIRED = mapOf(
            "VIBRATE" to SILENT,
            "SOUND" to BANNER,
        )

        fun fromName(raw: String?): ReminderLevel? {
            entries.firstOrNull { it.name == raw }?.let { return it }
            return RETIRED[raw]
        }
    }
}
