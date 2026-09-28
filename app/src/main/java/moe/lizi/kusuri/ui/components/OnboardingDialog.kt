package moe.lizi.kusuri.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ReliabilityChecks

/**
 * 可靠性向导(docs/plan.md §5):通知权限 → 精确闹钟 → 电池优化。
 * 首次启动必须显式点"完成"([onDismissRequest] 传空实现),设置页可随时重进。
 */
@Composable
fun OnboardingDialog(
    onDone: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val reliability = rememberReliabilityState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 回到前台后由 rememberReliabilityState 重新读取 */ }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.onboarding_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReliabilityRow(
                    label = stringResource(R.string.reliability_notifications),
                    ready = reliability.notificationsEnabled,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            ReliabilityChecks.openAppNotificationSettings(context)
                        }
                    },
                )
                ReliabilityRow(
                    label = stringResource(R.string.reliability_exact_alarm),
                    ready = reliability.exactAlarmsAllowed,
                    onFix = { ReliabilityChecks.openExactAlarmSettings(context) },
                )
                ReliabilityRow(
                    label = stringResource(R.string.reliability_battery),
                    ready = reliability.batteryOptimizationIgnored,
                    onFix = { ReliabilityChecks.openBatteryOptimizationSettings(context) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.onboarding_done))
            }
        },
    )
}
