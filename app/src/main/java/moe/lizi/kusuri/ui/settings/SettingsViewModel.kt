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
import moe.lizi.kusuri.data.backup.BackupService
import moe.lizi.kusuri.data.backup.CsvRange
import moe.lizi.kusuri.data.backup.CsvLabels
import moe.lizi.kusuri.data.backup.window
import moe.lizi.kusuri.domain.WipeAllDataUseCase

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
    private val clock: Clock,
) : ViewModel() {

    val gracePeriodHours: StateFlow<Int> = settings.gracePeriodHours
    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone
    val reminderSoundEnabled: StateFlow<Boolean> = settings.reminderSoundEnabled

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    fun setGracePeriodHours(hours: Int) = settings.setGracePeriodHours(hours)

    fun setReminderSoundEnabled(enabled: Boolean) = settings.setReminderSoundEnabled(enabled)

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
