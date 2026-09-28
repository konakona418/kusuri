package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.model.MedicationStatus

/** 到点或"稍后"闹钟:发/重发通知;到点闹钟同时排下一剂。 */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(ReminderExtras.MEDICATION_ID, -1L)
        val scheduledMillis = intent.getLongExtra(ReminderExtras.SCHEDULED_AT, -1L)
        if (medicationId <= 0L || scheduledMillis <= 0L) return

        val container = context.appContainer()
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val medication = container.medicationRepository.observeMedication(medicationId).first()
                if (medication == null || medication.status != MedicationStatus.ACTIVE) return@launch

                val scheduledAt = Instant.ofEpochMilli(scheduledMillis)
                val recorded = container.doseRecordRepository
                    .findByScheduled(medicationId, scheduledAt) != null
                if (!recorded) {
                    container.doseNotifier.notify(medication, scheduledAt, container.clock.instant())
                }
                if (intent.action == ReminderActions.ACTION_DOSE_REMINDER) {
                    container.alarmScheduler.scheduleNext(medication)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
