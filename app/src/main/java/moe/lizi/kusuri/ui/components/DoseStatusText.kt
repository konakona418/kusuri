package moe.lizi.kusuri.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.util.formatTime

/** 剂量的状态文字(不含操作按钮);可操作的状态由调用方补按钮。 */
@Composable
fun DoseStatusText(status: DoseStatus, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    val style = MaterialTheme.typography.bodySmall

    when (status) {
        DoseStatus.Pending -> Text(
            text = stringResource(R.string.status_pending),
            style = style,
            color = muted,
            modifier = modifier,
        )

        DoseStatus.Overdue -> Text(
            text = stringResource(R.string.status_overdue),
            style = style,
            color = error,
            modifier = modifier,
        )

        is DoseStatus.Taken -> Text(
            text = stringResource(
                R.string.status_taken_at,
                formatTime(status.actualAt.atZone(zone).toLocalTime()),
            ),
            style = style,
            color = muted,
            modifier = modifier,
        )

        DoseStatus.Skipped -> Text(
            text = stringResource(R.string.status_skipped),
            style = style,
            color = muted,
            modifier = modifier,
        )

        DoseStatus.Missed -> Text(
            text = stringResource(R.string.status_missed),
            style = style,
            color = error,
            modifier = modifier,
        )

        DoseStatus.Untracked -> Text(
            text = stringResource(R.string.status_untracked),
            style = style,
            color = muted,
            modifier = modifier,
        )
    }
}
