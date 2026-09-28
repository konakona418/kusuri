package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R

/**
 * 不可恢复的删除:只在**长按**时触发,单击不做任何事(防误触)。
 * 文案统一用"长按删除…",让用户知道该怎么做。
 */
@Composable
fun LongPressDeleteLabel(
    text: String,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** 常用文案:长按删除记录。 */
@Composable
fun longPressDeleteRecordLabel(): String = stringResource(R.string.delete_long_press)
