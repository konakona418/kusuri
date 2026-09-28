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

object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            MedicationListViewModel(kusuriApplication().container.medicationRepository)
        }
        initializer {
            MedicationDetailViewModel(
                repository = kusuriApplication().container.medicationRepository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
        initializer {
            val container = kusuriApplication().container
            MedicationEditViewModel(
                repository = container.medicationRepository,
                clock = container.clock,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }
}

private fun CreationExtras.kusuriApplication(): KusuriApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KusuriApplication
