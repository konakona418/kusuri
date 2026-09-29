package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * 卡片内的文字操作(docs/plan.md §7)。
 *
 * 首项左内边距为 0,因此它的左端与卡片标题严格对齐;[indent] 用于同一行里的第二项。
 * 三页共用这一份,免得"对齐"在两处各写一遍。
 */
@Composable
fun CardAction(
    text: String,
    onClick: () -> Unit,
    indent: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(
                start = if (indent) 12.dp else 0.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
    )
}
