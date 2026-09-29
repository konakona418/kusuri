package moe.lizi.kusuri.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import moe.lizi.kusuri.KusuriApplication
import moe.lizi.kusuri.ui.medications.MedicationDetailViewModel
import moe.lizi.kusuri.ui.medications.MedicationEditViewModel
import moe.lizi.kusuri.ui.medications.MedicationListViewModel
import moe.lizi.kusuri.ui.history.HistoryViewModel
import moe.lizi.kusuri.ui.lanexport.LanExportViewModel
import moe.lizi.kusuri.ui.log.LogViewModel
import moe.lizi.kusuri.ui.reminders.ReminderViewModel
import moe.lizi.kusuri.ui.settings.SettingsViewModel
import moe.lizi.kusuri.ui.today.TodayViewModel

object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            MedicationListViewModel(kusuriApplication().container.medicationRepository)
        }
        initializer {
            val container = kusuriApplication().container
            MedicationDetailViewModel(
                repository = container.medicationRepository,
                scheduler = container.alarmScheduler,
                checkLowStock = container.checkLowStock,
                savedStateHandle = createSavedStateHandle(),
            )
        }
        initializer {
            val container = kusuriApplication().container
            MedicationEditViewModel(
                repository = container.medicationRepository,
                checkLowStock = container.checkLowStock,
                clock = container.clock,
                savedStateHandle = createSavedStateHandle(),
            )
        }
        initializer {
            val container = kusuriApplication().container
            TodayViewModel(
                medicationRepository = container.medicationRepository,
                doseRecordRepository = container.doseRecordRepository,
                reminderRepository = container.reminderRepository,
                settings = container.settingsRepository,
                engine = container.scheduleEngine,
                recordDose = container.recordDose,
                clock = container.clock,
            )
        }
        initializer {
            val container = kusuriApplication().container
            HistoryViewModel(
                medicationRepository = container.medicationRepository,
                doseRecordRepository = container.doseRecordRepository,
                logEntryRepository = container.logEntryRepository,
                settings = container.settingsRepository,
                engine = container.scheduleEngine,
                recordDose = container.recordDose,
                clock = container.clock,
            )
        }
        initializer {
            val container = kusuriApplication().container
            SettingsViewModel(
                settings = container.settingsRepository,
                backupService = container.backupService,
                wipeAllData = container.wipeAllData,
                reminderNotifier = container.reminderNotifier,
                clock = container.clock,
            )
        }
        initializer {
            val container = kusuriApplication().container
            LanExportViewModel(
                backupService = container.backupService,
                client = container.lanExportClient,
                clock = container.clock,
            )
        }
        initializer {
            val container = kusuriApplication().container
            ReminderViewModel(
                repository = container.reminderRepository,
                control = container.alarmScheduler,
                clock = container.clock,
            )
        }
        initializer {
            val container = kusuriApplication().container
            LogViewModel(
                logEntryRepository = container.logEntryRepository,
                medicationRepository = container.medicationRepository,
                engine = container.scheduleEngine,
                clock = container.clock,
            )
        }
    }
}

private fun CreationExtras.kusuriApplication(): KusuriApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KusuriApplication
