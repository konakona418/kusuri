package moe.lizi.kusuri.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import moe.lizi.kusuri.R
import moe.lizi.kusuri.alarm.ReminderChannels
import moe.lizi.kusuri.alarm.ReliabilityChecks
import moe.lizi.kusuri.alarm.labelRes
import moe.lizi.kusuri.domain.model.ReminderLevel

/**
 * 提醒等级选择(docs/plan.md §4.1)。
 *
 * 平台约束:渠道的重要性创建后应用改不了,所以这里选的是"以后用哪条渠道"。
 * 顺带三件诚实的事:能当场"试一下",把"这条渠道被系统关掉了"说出来,并给一个直达系统设置的入口。
 */
@Composable
fun ReminderLevelDialog(
    current: ReminderLevel,
    testPosted: Boolean?,
    onSelect: (ReminderLevel) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val blocked = remember(current) {
        ReliabilityChecks.reminderChannelBlocked(context, ReminderChannels.channelIdFor(current))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_level)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_reminder_level_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReminderLevel.entries.forEach { level ->
                    val selected = level == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton) { onSelect(level) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(level.labelRes()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = reminderLevelDescription(level),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onTest) {
                        Text(stringResource(R.string.settings_reminder_test))
                    }
                    testPosted?.let { posted ->
                        Text(
                            text = stringResource(
                                if (posted) {
                                    R.string.settings_reminder_test_sent
                                } else {
                                    R.string.settings_reminder_test_blocked
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (posted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }

                if (blocked) {
                    Text(
                        text = stringResource(R.string.settings_reminder_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ReliabilityChecks.openReminderChannelSettings(
                        context,
                        ReminderChannels.channelIdFor(current),
                    )
                },
            ) {
                Text(stringResource(R.string.action_system_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun reminderLevelDescription(level: ReminderLevel): String = stringResource(
    when (level) {
        ReminderLevel.SILENT -> R.string.reminder_level_silent_description
        ReminderLevel.BANNER -> R.string.reminder_level_banner_description
    },
)
