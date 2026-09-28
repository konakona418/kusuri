package moe.lizi.kusuri.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.alarm.AlarmReminderScheduler
import moe.lizi.kusuri.domain.CheckLowStockUseCase
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.model.MedicationStatus
import moe.lizi.kusuri.domain.model.StockEventType

class MedicationDetailViewModel(
    private val repository: MedicationRepository,
    private val scheduler: AlarmReminderScheduler,
    private val checkLowStock: CheckLowStockUseCase,
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
            checkLowStock.check(medicationId)
        }
    }

    /** 把剩余数量直接校正到 [targetRemaining](盘点后校准)。 */
    fun adjustStock(targetRemaining: Double) {
        val current = medication.value ?: return
        viewModelScope.launch {
            repository.addStock(
                medicationId,
                StockEventType.ADJUST,
                targetRemaining - current.remainingStock,
            )
            checkLowStock.check(medicationId)
        }
    }

    /** 延长疗程:更新结束日期并恢复为在服(已完成/已归档都可复活)。 */
    fun extendCourse(newEnd: LocalDate) {
        val current = medication.value ?: return
        viewModelScope.launch {
            repository.save(current.copy(courseEnd = newEnd, status = MedicationStatus.ACTIVE))
        }
    }

    fun archive() {
        viewModelScope.launch {
            scheduler.cancelAllFor(medicationId)
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
            scheduler.cancelAllFor(medicationId)
            repository.delete(medicationId)
            _closed.value = true
        }
    }
}
