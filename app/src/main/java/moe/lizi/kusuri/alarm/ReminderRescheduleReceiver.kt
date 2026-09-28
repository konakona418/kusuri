package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** 开机、改时间/时区、应用更新后整体重排(系统闹钟在这些事件后会丢失)。 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                container.alarmScheduler.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
