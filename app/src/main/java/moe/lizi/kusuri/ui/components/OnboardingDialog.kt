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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ReliabilityChecks

/**
 * 首次启动的可靠性向导(docs/plan.md §5):通知权限 → 精确闹钟 → 电池优化。
 * 任一可跳过,状态在设置页可复查。
 */
@Composable
fun OnboardingDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationsEnabled by remember { mutableStateOf(ReliabilityChecks.notificationsEnabled(context)) }
    var exactAlarmsAllowed by remember { mutableStateOf(ReliabilityChecks.exactAlarmsAllowed(context)) }
    var batteryIgnored by remember { mutableStateOf(ReliabilityChecks.batteryOptimizationIgnored(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = ReliabilityChecks.notificationsEnabled(context)
                exactAlarmsAllowed = ReliabilityChecks.exactAlarmsAllowed(context)
                batteryIgnored = ReliabilityChecks.batteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsEnabled = granted }

    AlertDialog(
        onDismissRequest = onDone,
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
                    ready = notificationsEnabled,
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
                    ready = exactAlarmsAllowed,
                    onFix = { ReliabilityChecks.openExactAlarmSettings(context) },
                )
                ReliabilityRow(
                    label = stringResource(R.string.reliability_battery),
                    ready = batteryIgnored,
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
