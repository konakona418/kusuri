package moe.lizi.kusuri.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * 列表行的标题:"药名 · 剂量"。
 * 剂量用弱化颜色;整体允许换行(最多两行),因此长药名不会被截成省略号。
 */
@Composable
fun MedicationTitle(
    name: String,
    doseLabel: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = muted)) {
                append(" · ")
                append(doseLabel)
            }
        },
        style = MaterialTheme.typography.titleSmall,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
