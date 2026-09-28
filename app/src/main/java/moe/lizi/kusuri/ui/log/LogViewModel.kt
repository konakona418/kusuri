package moe.lizi.kusuri.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.LogEntryRepository
import moe.lizi.kusuri.domain.MedicationRepository
import moe.lizi.kusuri.domain.log.DEFAULT_SEVERITY
import moe.lizi.kusuri.domain.log.LogEntryErrors
import moe.lizi.kusuri.domain.log.LogEntryFormState
import moe.lizi.kusuri.domain.log.toEntry
import moe.lizi.kusuri.domain.log.validate
import moe.lizi.kusuri.domain.model.LogEntry
import moe.lizi.kusuri.domain.model.LogEntryType
import moe.lizi.kusuri.domain.model.Medication
import moe.lizi.kusuri.domain.schedule.ScheduleEngine

data class LogDay(val date: LocalDate, val entries: List<LogEntry>)

data class LogUiState(
    val today: LocalDate,
    val days: List<LogDay>,
    val recentSymptoms: List<String>,
    val medications: List<Medication>,
) {
    val isEmpty: Boolean get() = days.isEmpty()

    companion object {
        val Empty = LogUiState(
            today = LocalDate.EPOCH,
            days = emptyList(),
            recentSymptoms = emptyList(),
            medications = emptyList(),
        )
    }
}

data class LogEditorState(
    val entryId: Long?,
    val form: LogEntryFormState,
    val errors: LogEntryErrors? = null,
)

class LogViewModel(
    private val logEntryRepository: LogEntryRepository,
    private val medicationRepository: MedicationRepository,
    private val engine: ScheduleEngine,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<LogUiState> = combine(
        logEntryRepository.observeBetween(
            engine.dayStart(engine.today().minusDays(WINDOW_DAYS - 1L)),
            engine.dayStart(engine.today().plusDays(1)),
        ),
        medicationRepository.observeMedications(),
    ) { entries, medications -> entries to medications }
        .map { (entries, medications) ->
            LogUiState(
                today = engine.today(),
                days = groupByDay(entries),
                recentSymptoms = logEntryRepository.recentSymptoms(),
                medications = medications,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState.Empty)

    private val _editor = MutableStateFlow<LogEditorState?>(null)
    val editor: StateFlow<LogEditorState?> = _editor.asStateFlow()

    fun startCreate() {
        _editor.value = LogEditorState(
            entryId = null,
            form = LogEntryFormState.create(LogEntryType.SYMPTOM, clock.instant()),
        )
    }

    fun startEdit(entry: LogEntry) {
        _editor.value = LogEditorState(
            entryId = entry.id,
            form = LogEntryFormState(
                type = entry.type,
                at = entry.at,
                symptom = entry.symptom.orEmpty(),
                severity = entry.severity ?: DEFAULT_SEVERITY,
                medicationId = entry.medicationId,
                note = entry.note.orEmpty(),
            ),
        )
    }

    fun updateForm(transform: (LogEntryFormState) -> LogEntryFormState) {
        _editor.value = _editor.value?.let { it.copy(form = transform(it.form)) }
    }

    fun dismissEditor() {
        _editor.value = null
    }

    fun saveEditor() {
        val state = _editor.value ?: return
        val errors = state.form.validate()
        if (!errors.isValid) {
            _editor.value = state.copy(errors = errors)
            return
        }
        viewModelScope.launch {
            logEntryRepository.save(state.form.toEntry(state.entryId))
            _editor.value = null
        }
    }

    fun deleteEditing() {
        val id = _editor.value?.entryId ?: return
        viewModelScope.launch {
            logEntryRepository.delete(id)
            _editor.value = null
        }
    }

    private fun groupByDay(entries: List<LogEntry>): List<LogDay> =
        entries.groupBy { engine.dateOf(it.at) }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayEntries) -> LogDay(date, dayEntries.sortedByDescending { it.at }) }

    private companion object {
        const val WINDOW_DAYS = 90
    }
}
