package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.launch

/** 通用提醒到点(或"稍后"):发通知;到点闹钟同时按重复规则排下一次。 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderExtras.REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                // 提醒可能已被删除或已完成:以数据库为准,幽灵闹钟什么都不做。
                val reminder = container.reminderRepository.get(reminderId) ?: return@launch
                if (reminder.doneAt != null) return@launch

                val now = container.clock.instant()
                val occurrence = intent.getLongExtra(ReminderExtras.OCCURRENCE_AT, -1L)
                    .takeIf { it > 0L }
                    ?.let(Instant::ofEpochMilli)
                    ?: now
                container.reminderNotifier.notify(reminder, occurrence, now)

                if (intent.action == ReminderActions.ACTION_REMINDER_ALARM) {
                    container.alarmScheduler.scheduleNext(reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
