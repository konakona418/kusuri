package moe.lizi.kusuri.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.model.MedicationStatus

/** 到点或"稍后"闹钟:重建这个时刻的通知组;到点闹钟同时排下一剂。 */
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

                // 同一计划时刻的多味药折叠成一组:统一走这个入口重建,
                // 已经记录过的那几味会被自动撤下,剩下的几条仍挂着。
                container.syncDoseNotifications.sync(
                    scheduledAt = Instant.ofEpochMilli(scheduledMillis),
                    now = container.clock.instant(),
                    alertAgain = intent.action == ReminderActions.ACTION_DOSE_SNOOZE,
                )
                if (intent.action == ReminderActions.ACTION_DOSE_REMINDER) {
                    container.alarmScheduler.scheduleNext(medication)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
