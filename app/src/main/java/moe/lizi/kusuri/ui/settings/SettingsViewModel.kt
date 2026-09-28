package moe.lizi.kusuri.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.alarm.DoseNotifier
import moe.lizi.kusuri.data.backup.BackupService
import moe.lizi.kusuri.data.backup.CsvRange
import moe.lizi.kusuri.data.backup.CsvLabels
import moe.lizi.kusuri.data.backup.window
import moe.lizi.kusuri.domain.WipeAllDataUseCase
import moe.lizi.kusuri.domain.model.ReminderLevel

sealed interface BackupStatus {
    data object CsvExported : BackupStatus
    data object JsonExported : BackupStatus
    data class Imported(val medications: Int) : BackupStatus
    data object Wiped : BackupStatus
    data object Failed : BackupStatus
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backupService: BackupService,
    private val wipeAllData: WipeAllDataUseCase,
    private val doseNotifier: DoseNotifier,
    private val clock: Clock,
) : ViewModel() {

    val gracePeriodHours: StateFlow<Int> = settings.gracePeriodHours
    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
    val reminderLevel: StateFlow<ReminderLevel> = settings.reminderLevel

    private val _testReminderPosted = MutableStateFlow<Boolean?>(null)

    /** 测试提醒的结果:null 还没试过,true 已发出,false 系统层面发不出去。 */
    val testReminderPosted: StateFlow<Boolean?> = _testReminderPosted.asStateFlow()

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    fun setGracePeriodHours(hours: Int) = settings.setGracePeriodHours(hours)

    fun setReminderLevel(level: ReminderLevel) = settings.setReminderLevel(level)

    /** 发一条当前等级的测试提醒:当场核对响铃/震动/横幅,不用等真提醒。 */
    fun sendTestReminder() {
        _testReminderPosted.value = doseNotifier.notifyTest(settings.reminderLevel.value)
    }

    fun consumeTestReminder() {
        _testReminderPosted.value = null
    }

    fun completeOnboarding() = settings.setOnboardingDone(true)

    fun consumeBackupStatus() {
        _backupStatus.value = null
    }

    fun exportCsv(uri: Uri, labels: CsvLabels, range: CsvRange) {
        runCatchingAsync {
            val (from, to) = range.window(clock.instant())
            backupService.writeText(uri, backupService.exportCsv(from, to, labels))
            BackupStatus.CsvExported
        }
    }

    fun exportJson(uri: Uri) {
        runCatchingAsync {
            backupService.writeText(uri, backupService.exportJson())
            BackupStatus.JsonExported
        }
    }

    fun importJson(uri: Uri) {
        runCatchingAsync {
            val imported = backupService.importJson(backupService.readText(uri))
            BackupStatus.Imported(imported)
        }
    }

    /** 危险操作:调用方必须先做双重确认。 */
    fun deleteAllData() {
        runCatchingAsync {
            wipeAllData.wipe()
            BackupStatus.Wiped
        }
    }

    private fun runCatchingAsync(block: suspend () -> BackupStatus) {
        viewModelScope.launch {
            _backupStatus.value = runCatching { block() }
                .getOrElse { BackupStatus.Failed }
        }
    }
}
