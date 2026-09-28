package moe.lizi.kusuri.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.lizi.kusuri.R
import moe.lizi.kusuri.data.backup.CsvLabels

/**
 * CSV 导出所需的本地化标签。
 *
 * 数据层不依赖 `strings.xml`(`BackupService` 只收这一包文案),所以由界面侧提供;
 * 设置页与局域网导出页共用同一份,避免两处文案漂移。
 */
@Composable
fun backupCsvLabels(): CsvLabels = CsvLabels(
    doseSectionTitle = stringResource(R.string.csv_section_doses),
    doseHeader = listOf(
        stringResource(R.string.csv_header_medication),
        stringResource(R.string.csv_header_dose),
        stringResource(R.string.csv_header_scheduled_at),
        stringResource(R.string.csv_header_actual_at),
        stringResource(R.string.csv_header_status),
        stringResource(R.string.csv_header_source),
    ),
    taken = stringResource(R.string.action_taken),
    skipped = stringResource(R.string.action_skip),
    sourceInApp = stringResource(R.string.csv_source_in_app),
    sourceNotification = stringResource(R.string.csv_source_notification),
    sourceBackfill = stringResource(R.string.csv_source_backfill),
    logSectionTitle = stringResource(R.string.csv_section_logs),
    logHeader = listOf(
        stringResource(R.string.csv_log_header_time),
        stringResource(R.string.csv_log_header_type),
        stringResource(R.string.csv_log_header_symptom),
        stringResource(R.string.csv_log_header_severity),
        stringResource(R.string.csv_log_header_medication),
        stringResource(R.string.csv_log_header_note),
    ),
    logTypeSymptom = stringResource(R.string.log_type_symptom),
    logTypeNote = stringResource(R.string.log_type_note),
    logLinkedNone = stringResource(R.string.log_link_none),
)
