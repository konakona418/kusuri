package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R

/**
 * 可靠性检查的一行:名称 —— 状态(靠右)—— 需要时"去设置"。
 * 状态紧跟右端,不预留空位,避免"已就绪"旁边出现一块空白。
 */
@Composable
fun ReliabilityRow(label: String, ready: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (ready) {
                stringResource(R.string.reliability_ok)
            } else {
                stringResource(R.string.reliability_pending)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (ready) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (!ready) {
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.action_open_settings))
            }
        }
    }
}
