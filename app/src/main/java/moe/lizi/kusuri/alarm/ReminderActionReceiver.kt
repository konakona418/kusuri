package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** 通用提醒的通知动作:知道了 / 稍后 15 分钟。 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderExtras.REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ReminderActions.ACTION_REMINDER_ACK -> {
                        val reminder = container.reminderRepository.get(reminderId)
                        // 只有一次性提醒有"完成";重复提醒只是把这次通知收掉,下一次照旧。
                        if (reminder != null && !reminder.repeats) {
                            container.reminderRepository.markDone(reminderId, container.clock.instant())
                            container.alarmScheduler.cancel(reminderId)
                        } else {
                            container.alarmScheduler.clearNotification(reminderId)
                        }
                    }

                    ReminderActions.ACTION_REMINDER_SNOOZE_REQUEST ->
                        container.alarmScheduler.scheduleReminderSnooze(reminderId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
