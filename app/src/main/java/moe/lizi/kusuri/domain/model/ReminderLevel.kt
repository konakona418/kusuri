package moe.lizi.kusuri.domain.model

/**
 * 服药提醒的等级,对应 Android 通知渠道的重要性(docs/plan.md §4.1)。
 *
 * 平台的硬约束是**渠道的重要性一旦创建就不能再改**,所以这里是"一个等级一条渠道",
 * 发送时按当前等级选渠道;用户在系统设置里对某条渠道的微调同样会被尊重。
 *
 * 刻意不暴露 `IMPORTANCE_MIN` 与 `IMPORTANCE_NONE`:对提醒类应用它们是静默失败
 * (选了下一次既不响也不显示,还看不出为什么)。
 */
enum class ReminderLevel {
    /** 不响不震,只在通知栏显示(`IMPORTANCE_LOW`,无声音无震动)。 */
    SILENT,

    /** 只震动,不响铃(`IMPORTANCE_LOW` + 震动)。 */
    VIBRATE,

    /** 响铃 + 震动(`IMPORTANCE_DEFAULT`)。 */
    SOUND,

    /** 响铃 + 震动 + 屏幕顶部横幅(`IMPORTANCE_HIGH`)。 */
    BANNER,
    ;

    val isSilent: Boolean get() = this == SILENT

    /** 是否震动(静默档之外都震)。 */
    val vibrates: Boolean get() = this != SILENT

    companion object {
        /** 默认沿用旧版的"响铃"行为(旧渠道就是 `IMPORTANCE_HIGH`,即横幅)。 */
        val DEFAULT: ReminderLevel = BANNER

        fun fromName(raw: String?): ReminderLevel? = entries.firstOrNull { it.name == raw }
    }
}
