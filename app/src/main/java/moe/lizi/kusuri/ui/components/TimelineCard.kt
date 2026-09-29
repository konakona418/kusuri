package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 今日 / 记录 / 历史 三页共用的时间线条目卡(docs/plan.md §7)。
 *
 * 三页的对齐规则只在这里存在一份:横向内边距 16、纵向 12、时间列宽 56、
 * 时间列与内容列间距 12、内容列内行间距 2。次要行与操作都放在 [content] 里,
 * 因此它们天然左端对齐于标题。
 */
@Composable
fun TimelineCard(
    time: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }

    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    }
}

/** 卡片内容列里的标题行:标题占满余宽,右侧放状态 / 程度点。 */
@Composable
fun TimelineTitle(
    title: @Composable (Modifier) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        title(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
