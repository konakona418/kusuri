package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R

/**
 * 选一味药,或选"不关联 / 全部"([noneLabel])。
 *
 * 选项用 (id, 名称) 而不是整个 Medication:调用方可能只拿得到名字(例如历史页的筛选),
 * 不该为了弹个选择框去拉完整对象。
 */
@Composable
fun MedicationPickerDialog(
    title: String,
    options: List<Pair<Long, String>>,
    selectedId: Long?,
    noneLabel: String,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = { onSelect(null) },
                    enabled = selectedId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(noneLabel)
                }
                options.forEach { (id, name) ->
                    TextButton(
                        onClick = { onSelect(id) },
                        enabled = selectedId != id,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
