package moe.lizi.kusuri.ui.medications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.model.Medication

class MedicationListViewModel(repository: MedicationRepository) : ViewModel() {

    val medications: StateFlow<List<Medication>> = repository.observeMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
