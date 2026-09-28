package moe.lizi.kusuri.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.CheckLowStockUseCase
import moe.lizi.kusuri.domain.form.MedicationFormErrors
import moe.lizi.kusuri.domain.form.MedicationFormState
import moe.lizi.kusuri.domain.form.toFormState
import moe.lizi.kusuri.domain.form.toMedication
import moe.lizi.kusuri.domain.form.validate
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.StockEventType

class MedicationEditViewModel(
    private val repository: MedicationRepository,
    private val checkLowStock: CheckLowStockUseCase,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("medicationId")
    private var existing: Medication? = null

    private val _form = MutableStateFlow<MedicationFormState?>(null)
    val form: StateFlow<MedicationFormState?> = _form.asStateFlow()

    private val _errors = MutableStateFlow<MedicationFormErrors?>(null)
    val errors: StateFlow<MedicationFormErrors?> = _errors.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isNew: Boolean = editingId == null

    init {
        if (editingId == null) {
            _form.value = MedicationFormState.create(LocalDate.now(clock))
        } else {
            viewModelScope.launch {
                val medication = repository.observeMedication(editingId).first()
                existing = medication
                _form.value = medication?.toFormState()
            }
        }
    }

    fun update(transform: (MedicationFormState) -> MedicationFormState) {
        _form.value = _form.value?.let(transform)
    }

    fun save() {
        val state = _form.value ?: return
        val errors = state.validate()
        _errors.value = errors
        if (!errors.isValid) return

        viewModelScope.launch {
            val id = repository.save(state.toMedication(existing, clock))
            val enteredStock = state.initialStockText.trim().toDouble()
            if (existing == null) {
                if (enteredStock > 0) repository.addStock(id, StockEventType.INITIAL, enteredStock)
                checkLowStock.initialize(id)
            } else {
                // 编辑时填写的数量按"盘点调整"落盘:库存是派生值,只能通过事件修正。
                val current = repository.observeMedication(id).first() ?: return@launch
                val delta = enteredStock - current.remainingStock
                if (delta != 0.0) {
                    repository.addStock(id, StockEventType.ADJUST, delta)
                    checkLowStock.check(id)
                }
            }
            _saved.value = true
        }
    }
}
