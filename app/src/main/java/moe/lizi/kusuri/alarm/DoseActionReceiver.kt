package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.lizi.kusuri.di.AppContainer
import moe.lizi.kusuri.domain.model.DoseAction
import moe.lizi.kusuri.domain.model.DoseSource

/** 通知动作:已服用 / 跳过 / 稍后。 */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(ReminderExtras.MEDICATION_ID, -1L)
        val scheduledMillis = intent.getLongExtra(ReminderExtras.SCHEDULED_AT, -1L)
        if (medicationId <= 0L || scheduledMillis <= 0L) return

        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val scheduledAt = Instant.ofEpochMilli(scheduledMillis)
                when (intent.action) {
                    ReminderActions.ACTION_DOSE_TAKEN ->
                        recordDose(container, medicationId, scheduledAt, DoseAction.TAKEN)

                    ReminderActions.ACTION_DOSE_SKIP ->
                        recordDose(container, medicationId, scheduledAt, DoseAction.SKIPPED)

                    ReminderActions.ACTION_DOSE_SNOOZE_REQUEST ->
                        container.alarmScheduler.scheduleSnooze(medicationId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun recordDose(
        container: AppContainer,
        medicationId: Long,
        scheduledAt: Instant,
        action: DoseAction,
    ) {
        val medication = container.medicationRepository.observeMedication(medicationId).first() ?: return
        val alreadyRecorded = container.doseRecordRepository.findByScheduled(medicationId, scheduledAt) != null
        if (!alreadyRecorded) {
            container.doseRecordRepository.record(
                medicationId = medicationId,
                scheduledAt = scheduledAt,
                amount = medication.defaultDose,
                action = action,
                source = DoseSource.NOTIFICATION,
            )
        }
        container.alarmScheduler.cancelDose(medicationId)
    }
}
