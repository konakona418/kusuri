package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.backup.CsvRange

/** CSV 导出的区间选择。设置页(SAF)与局域网导出页共用。 */
@Composable
fun CsvRangeDialog(onDismiss: () -> Unit, onConfirm: (CsvRange) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.csv_range_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvRange.entries.forEach { range ->
                    OutlinedButton(
                        onClick = { onConfirm(range) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(csvRangeLabel(range))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun csvRangeLabel(range: CsvRange): String = when (range) {
    CsvRange.LAST_30_DAYS -> stringResource(R.string.csv_range_30)
    CsvRange.LAST_90_DAYS -> stringResource(R.string.csv_range_90)
    CsvRange.ALL -> stringResource(R.string.csv_range_all)
}
