package moe.lizi.kusuri.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import java.time.ZoneId
import moe.lizi.kusuri.R
import moe.lizi.kusuri.domain.model.DoseStatus
import moe.lizi.kusuri.domain.model.DoseStatusKind
import moe.lizi.kusuri.domain.model.kind
import moe.lizi.kusuri.domain.util.formatTime

/** 剂量的状态文字(不含操作按钮);可操作的状态由调用方补按钮。 */
@Composable
fun DoseStatusText(status: DoseStatus, modifier: Modifier = Modifier) {
    if (status is DoseStatus.Taken) {
        val zone = ZoneId.systemDefault()
        Text(
            text = stringResource(
                R.string.status_taken_at,
                formatTime(status.actualAt.atZone(zone).toLocalTime()),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    DoseStatusKindText(status.kind, modifier)
}

/**
 * 只按状态种类显示的文案(不带实际时间)。
 * 历史里折叠的同一时刻是"多味药"的汇总,显示具体时刻只会误导。
 */
@Composable
fun DoseStatusKindText(kind: DoseStatusKind, modifier: Modifier = Modifier) {
    val textRes: Int
    val color: Color
    when (kind) {
        DoseStatusKind.PENDING -> {
            textRes = R.string.status_pending
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }

        DoseStatusKind.OVERDUE -> {
            textRes = R.string.status_overdue
            color = MaterialTheme.colorScheme.error
        }

        DoseStatusKind.TAKEN -> {
            textRes = R.string.status_taken
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }

        DoseStatusKind.SKIPPED -> {
            textRes = R.string.status_skipped
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }

        DoseStatusKind.MISSED -> {
            textRes = R.string.status_missed
            color = MaterialTheme.colorScheme.error
        }

        DoseStatusKind.UNTRACKED -> {
            textRes = R.string.status_untracked
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
