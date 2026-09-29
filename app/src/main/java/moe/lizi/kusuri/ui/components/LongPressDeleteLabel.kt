package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.border
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
 *
 * 加边框是有意的:这套"长按删除"此前是纯文字,看起来不像按钮,
 * 用户根本不知道可以长按(而它是取消服用 / 撤销记录的唯一入口)。
 */
@Composable
fun LongPressDeleteLabel(
    text: String,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.error, shape)
            .clip(shape)
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

/** 已服用的记录:这个动作的实质是"取消服用",不只是删一条记录。 */
@Composable
fun longPressCancelTakenLabel(): String = stringResource(R.string.delete_long_press_taken)

/** 通用提醒:长按删除整条提醒(含它的重复规则)。 */
@Composable
fun longPressDeleteReminderLabel(): String = stringResource(R.string.delete_long_press_reminder)
