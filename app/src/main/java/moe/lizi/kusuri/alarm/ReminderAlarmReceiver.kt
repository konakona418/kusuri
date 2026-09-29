package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.launch

/** 通用提醒到点(或"稍后"):发通知;到点闹钟同时按重复规则排下一次。 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val snooze = intent.action == ReminderActions.ACTION_REMINDER_SNOOZE
                val target = intent.reminderTarget()
                if (target == null) {
                    // 同 DoseAlarmReceiver:extras 被剥空时,重排闹钟并把刚过去的提醒补上。
                    container.recoverMissed(alertAgain = snooze)
                    return@launch
                }
                val (reminderId, occurrenceMillis) = target
                // 提醒可能已被删除或已完成:以数据库为准,幽灵闹钟什么都不做。
                val reminder = container.reminderRepository.get(reminderId) ?: return@launch
                if (reminder.doneAt != null) return@launch

                val now = container.clock.instant()
                container.reminderNotifier.notify(
                    reminder = reminder,
                    occurrence = occurrenceMillis?.let(Instant::ofEpochMilli) ?: now,
                    now = now,
                    alertAgain = snooze,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
