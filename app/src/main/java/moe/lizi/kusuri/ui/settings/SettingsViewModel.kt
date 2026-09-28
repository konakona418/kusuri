package moe.lizi.kusuri.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.lizi.kusuri.data.SettingsRepository
import moe.lizi.kusuri.data.backup.BackupService
import moe.lizi.kusuri.data.backup.CsvLabels

sealed interface BackupStatus {
    data object CsvExported : BackupStatus
    data object JsonExported : BackupStatus
    data class Imported(val medications: Int) : BackupStatus
    data object Failed : BackupStatus
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backupService: BackupService,
    private val clock: Clock,
) : ViewModel() {

    val gracePeriodHours: StateFlow<Int> = settings.gracePeriodHours
    val onboardingDone: StateFlow<Boolean> = settings.onboardingDone

    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    val backupStatus: StateFlow<BackupStatus?> = _backupStatus.asStateFlow()

    fun setGracePeriodHours(hours: Int) = settings.setGracePeriodHours(hours)

    fun completeOnboarding() = settings.setOnboardingDone(true)

    fun consumeBackupStatus() {
        _backupStatus.value = null
    }

    fun exportCsv(uri: Uri, labels: CsvLabels) {
        runCatchingAsync {
            val to = clock.instant()
            backupService.writeText(uri, backupService.exportCsv(to.minus(CSV_WINDOW), to, labels))
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

    private fun runCatchingAsync(block: suspend () -> BackupStatus) {
        viewModelScope.launch {
            _backupStatus.value = runCatching { block() }
                .getOrElse { BackupStatus.Failed }
        }
    }

    private companion object {
        val CSV_WINDOW: Duration = Duration.ofDays(30)
    }
}
