package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** 开机、改时间/时区、应用更新后整体重排,并把这段时间里本该响过、还真没响的补上。 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                container.recoverMissed()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
