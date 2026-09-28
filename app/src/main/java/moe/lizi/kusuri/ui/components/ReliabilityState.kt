package moe.lizi.kusuri.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.lizi.kusuri.alarm.ReliabilityChecks

data class ReliabilityState(
    val notificationsEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean,
) {
    val allReady: Boolean
        get() = notificationsEnabled && exactAlarmsAllowed && batteryOptimizationIgnored
}

/** 三项可靠性状态;每次回到前台重新读取(用户可能刚在系统设置里改过)。 */
@Composable
fun rememberReliabilityState(): ReliabilityState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(readReliability(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state = readReliability(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return state
}

private fun readReliability(context: Context) = ReliabilityState(
    notificationsEnabled = ReliabilityChecks.notificationsEnabled(context),
    exactAlarmsAllowed = ReliabilityChecks.exactAlarmsAllowed(context),
    batteryOptimizationIgnored = ReliabilityChecks.batteryOptimizationIgnored(context),
)
