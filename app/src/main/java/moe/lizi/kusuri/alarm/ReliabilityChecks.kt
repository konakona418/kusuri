package moe.lizi.kusuri.alarm

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 提醒可靠性的三项检查(docs/plan.md §4.2/§5)。
 * 失败时必须对用户可见——提醒不响是本应用最严重的缺陷。
 */
object ReliabilityChecks {

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmsAllowed(context: Context): Boolean =
        ExactAlarmPermissions.canScheduleExactAlarms(context)

    fun batteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAppNotificationSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    /** 只把用户送到系统设置页手动加白名单,不申请豁免权限(Play 政策限制)。 */
    fun openBatteryOptimizationSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    fun openExactAlarmSettings(context: Context) {
        runCatching { context.startActivity(ExactAlarmPermissions.settingsIntent(context)) }
            .onFailure {
                runCatching { context.startActivity(ExactAlarmPermissions.appDetailsIntent(context)) }
            }
    }
}
