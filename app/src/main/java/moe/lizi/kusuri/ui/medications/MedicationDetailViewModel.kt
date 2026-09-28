package moe.lizi.kusuri.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.StockEventType

class MedicationDetailViewModel(
    private val repository: MedicationRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val medicationId: Long = checkNotNull(savedStateHandle["medicationId"])

    val medication: StateFlow<Medication?> = repository.observeMedication(medicationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    fun refill(amount: Double) {
        viewModelScope.launch {
            repository.addStock(medicationId, StockEventType.REFILL, amount)
        }
    }

    fun archive() {
        viewModelScope.launch {
            repository.setStatus(medicationId, MedicationStatus.ARCHIVED)
            _closed.value = true
        }
    }

    fun unarchive() {
        viewModelScope.launch {
            repository.setStatus(medicationId, MedicationStatus.ACTIVE)
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(medicationId)
            _closed.value = true
        }
    }
}
