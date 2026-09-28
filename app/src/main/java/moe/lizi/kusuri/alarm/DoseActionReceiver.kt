package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource

/** 通知动作:已服用 / 跳过 / 稍后。 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(ReminderExtras.MEDICATION_ID, -1L)
        val scheduledMillis = intent.getLongExtra(ReminderExtras.SCHEDULED_AT, -1L)
        if (medicationId <= 0L || scheduledMillis <= 0L) return

        val container = context.appContainer()
        val scheduledAt = Instant.ofEpochMilli(scheduledMillis)
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ReminderActions.ACTION_DOSE_TAKEN ->
                        container.recordDose.record(medicationId, scheduledAt, DoseAction.TAKEN, DoseSource.NOTIFICATION)

                    ReminderActions.ACTION_DOSE_SKIP ->
                        container.recordDose.record(medicationId, scheduledAt, DoseAction.SKIPPED, DoseSource.NOTIFICATION)

                    ReminderActions.ACTION_DOSE_SNOOZE_REQUEST ->
                        container.alarmScheduler.scheduleSnooze(medicationId, scheduledAt)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
