package moe.lizi.kusuri.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import moe.lizi.kusuri.KusuriApplication

/** 定期巡检:校正漏排的闹钟、疗程收官、库存告警同步(docs/plan.md §5)。 */
class ReminderMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KusuriApplication).container
        container.runMaintenance()
        return Result.success()
    }
}
