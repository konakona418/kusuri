package moe.lizi.kusuri.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.lizi.kusuri.domain.ReminderControl
import moe.lizi.kusuri.domain.ReminderRepository
import moe.lizi.kusuri.domain.model.Reminder
import moe.lizi.kusuri.domain.model.ReminderRepeatKind
import moe.lizi.kusuri.domain.reminder.ReminderSchedule
import moe.lizi.kusuri.domain.util.clockTicks

enum class ReminderStatus { UPCOMING, OVERDUE, DONE }

data class ReminderRow(
    val reminder: Reminder,
    val status: ReminderStatus,
    /** 即将到来 = 下一次发生的时刻;已过期 / 已完成 = 那一次的时刻。 */
    val at: Instant,
)

data class ReminderUiState(
    val today: LocalDate,
    val upcoming: List<ReminderRow>,
    val overdue: List<ReminderRow>,
    val done: List<ReminderRow>,
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && overdue.isEmpty() && done.isEmpty()

    companion object {
        val Empty = ReminderUiState(
            today = LocalDate.EPOCH,
            upcoming = emptyList(),
            overdue = emptyList(),
            done = emptyList(),
        )
    }
}

/** 编辑中的提醒;null 表示对话框关着。[createdAt]/[doneAt] 只是带着走,不让编辑把它们洗掉。 */
data class ReminderEditor(
    val id: Long = 0L,
    val title: String = "",
    val at: Instant,
    val repeatKind: ReminderRepeatKind = ReminderRepeatKind.ONCE,
    val interval: Int = 2,
    val note: String = "",
    val createdAt: Instant = Instant.EPOCH,
) {
    val isNew: Boolean get() = id == 0L
    val valid: Boolean get() = title.isNotBlank()
}

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val control: ReminderControl,
    private val clock: Clock,
) : ViewModel() {

    private val nowFlow = clockTicks(clock, STATUS_REFRESH_MILLIS)

    val uiState: StateFlow<ReminderUiState> =
        combine(repository.observeAll(), nowFlow) { reminders, now ->
            buildState(reminders, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState.Empty)

    private val _editor = MutableStateFlow<ReminderEditor?>(null)
    val editor: StateFlow<ReminderEditor?> = _editor.asStateFlow()

    fun startCreate() {
        // 默认落在下一个整点,省得每次都要从"现在"改起。
        val next = clock.instant()
            .atZone(ZoneId.systemDefault())
            .truncatedTo(ChronoUnit.HOURS)
            .plusHours(1)
        _editor.value = ReminderEditor(at = next.toInstant())
    }

    fun startEdit(row: ReminderRow) {
        val reminder = row.reminder
        _editor.value = ReminderEditor(
            id = reminder.id,
            title = reminder.title,
            at = reminder.at,
            repeatKind = reminder.repeatKind,
            interval = reminder.safeInterval,
            note = reminder.note.orEmpty(),
            createdAt = reminder.createdAt,
        )
    }

    fun dismissEditor() {
        _editor.value = null
    }

    fun updateEditor(transform: (ReminderEditor) -> ReminderEditor) {
        _editor.value = _editor.value?.let(transform)
    }

    fun saveEditor() {
        val editor = _editor.value ?: return
        if (!editor.valid) return
        viewModelScope.launch {
            repository.save(
                Reminder(
                    id = editor.id,
                    title = editor.title.trim(),
                    at = editor.at,
                    repeatKind = editor.repeatKind,
                    interval = editor.interval.coerceAtLeast(1),
                    note = editor.note.trim().ifBlank { null },
                    createdAt = editor.createdAt,
                    // 改过一条已完成的提醒就让它重新生效:这才是"改"的意图。
                    doneAt = null,
                ),
            )
        }
        _editor.value = null
    }

    fun deleteEditing() {
        val editor = _editor.value ?: return
        if (editor.isNew) return
        viewModelScope.launch {
            repository.delete(editor.id)
            control.cancel(editor.id)
        }
        _editor.value = null
    }

    /** 列表里直接标完成;通知上的"知道了"是同一个动作。 */
    fun acknowledge(row: ReminderRow) {
        viewModelScope.launch {
            repository.markDone(row.reminder.id, clock.instant())
            control.cancel(row.reminder.id)
        }
    }

    private fun buildState(reminders: List<Reminder>, now: Instant): ReminderUiState {
        val zone = ZoneId.systemDefault()
        val upcoming = mutableListOf<ReminderRow>()
        val overdue = mutableListOf<ReminderRow>()
        val done = mutableListOf<ReminderRow>()
        reminders.forEach { reminder ->
            val next = if (reminder.doneAt == null) {
                ReminderSchedule.nextOccurrence(reminder, now, zone)
            } else {
                null
            }
            when {
                reminder.doneAt != null -> done += ReminderRow(reminder, ReminderStatus.DONE, reminder.at)
                next != null -> upcoming += ReminderRow(reminder, ReminderStatus.UPCOMING, next)
                // 没有下一次了,而且还没完成 → 一次性提醒过期了。
                else -> overdue += ReminderRow(reminder, ReminderStatus.OVERDUE, reminder.at)
            }
        }
        return ReminderUiState(
            today = now.atZone(zone).toLocalDate(),
            upcoming = upcoming.sortedBy { it.at },
            overdue = overdue.sortedByDescending { it.at },
            done = done.sortedByDescending { it.at },
        )
    }

    private companion object {
        const val STATUS_REFRESH_MILLIS = 60_000L
    }
}
