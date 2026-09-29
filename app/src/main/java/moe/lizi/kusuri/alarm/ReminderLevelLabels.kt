package moe.lizi.kusuri.alarm

import androidx.annotation.StringRes
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.ReminderLevel

/**
 * 提醒等级 → 文案资源。
 *
 * 放这里而不是界面层:通知本身(含测试通知)也要用到,而通知不该依赖界面模块。
 */
@StringRes
fun ReminderLevel.labelRes(): Int = when (this) {
    ReminderLevel.SILENT -> R.string.reminder_level_silent
    ReminderLevel.BANNER -> R.string.reminder_level_banner
}
