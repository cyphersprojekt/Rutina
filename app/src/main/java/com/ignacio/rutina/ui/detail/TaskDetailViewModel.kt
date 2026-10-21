package com.ignacio.rutina.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Repository
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.domain.CounterStats
import com.ignacio.rutina.domain.ReminderStats
import com.ignacio.rutina.domain.counterStats
import com.ignacio.rutina.domain.reminderStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface DetailState {
    data object Loading : DetailState

    data object Missing : DetailState

    data class Reminder(val task: Task, val from: LocalDate, val today: LocalDate, val stats: ReminderStats) : DetailState

    data class Counter(
        val task: Task,
        val from: LocalDate,
        val today: LocalDate,
        val stats: CounterStats,
        /** Registros del período, del más nuevo al más viejo. */
        val history: List<Entry>,
    ) : DetailState
}

val RANGE_OPTIONS = listOf(7, 30, 90)

class TaskDetailViewModel(private val repo: Repository, taskId: Long) : ViewModel() {
    private val rangeDays = MutableStateFlow(30)
    val range: StateFlow<Int> = rangeDays.asStateFlow()

    val state: StateFlow<DetailState> = combine(repo.task(taskId), repo.entriesFor(taskId), rangeDays) { task, entries, days ->
        if (task == null) return@combine DetailState.Missing
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val from = today.minusDays(days - 1L)
        when (task.kind) {
            TaskKind.REMINDER -> DetailState.Reminder(task, from, today, reminderStats(task, entries, from, now, repo.zone))
            TaskKind.COUNTER -> {
                val fromMillis = from.atStartOfDay(repo.zone).toInstant().toEpochMilli()
                DetailState.Counter(
                    task = task,
                    from = from,
                    today = today,
                    stats = counterStats(task, entries, from, today, repo.zone),
                    history = entries.filter { it.doneAt >= fromMillis }.reversed(),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun setRange(days: Int) {
        rangeDays.value = days
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch { repo.deleteEntry(entry) }
    }
}
