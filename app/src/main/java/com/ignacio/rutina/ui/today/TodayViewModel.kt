package com.ignacio.rutina.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Repository
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.domain.AgendaItem
import com.ignacio.rutina.domain.CategoryProgress
import com.ignacio.rutina.domain.CounterItem
import com.ignacio.rutina.domain.buildAgenda
import com.ignacio.rutina.domain.buildCounters
import com.ignacio.rutina.domain.categoryProgress
import com.ignacio.rutina.domain.matches
import com.ignacio.rutina.domain.toEpochMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class DayState(
    val date: LocalDate,
    val today: LocalDate,
    val hasTasks: Boolean,
    /** Filtro de categoría elegido (ver [com.ignacio.rutina.domain.matches]). */
    val filter: Long?,
    /** Avance de todo el día, sin filtrar. */
    val overall: CategoryProgress,
    /** Vacío si ninguna tarea tiene categoría: así no aparece la fila de filtros. */
    val categories: List<CategoryProgress>,
    /** Ya filtrados por [filter]. */
    val agenda: List<AgendaItem>,
    val counters: List<CounterItem>,
) {
    val isFuture: Boolean get() = date.isAfter(today)
}

private data class DaySelection(val today: LocalDate, val date: LocalDate, val filter: Long?)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: Repository) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now())

    /** Días de distancia con hoy del día que se está mirando. */
    private val offset = MutableStateFlow(0L)

    private val filter = MutableStateFlow<Long?>(null)

    val state: StateFlow<DayState?> = combine(today, offset, filter) { today, offset, filter ->
        DaySelection(today, today.plusDays(offset), filter)
    }
        .flatMapLatest { (today, date, filter) ->
            combine(
                repo.tasks,
                repo.categories,
                repo.slotEntriesOn(date),
                repo.countEntriesOn(date),
            ) { tasks, categories, slotEntries, countEntries ->
                val agenda = buildAgenda(date, tasks, slotEntries, repo.zone)
                val counters = buildCounters(tasks, countEntries)
                DayState(
                    date = date,
                    today = today,
                    hasTasks = tasks.isNotEmpty(),
                    filter = filter,
                    overall = CategoryProgress(null, agenda.count { it.entry != null }, agenda.size),
                    categories = if (tasks.any { it.categoryId != null }) {
                        categoryProgress(agenda, counters, categories, keep = filter)
                    } else {
                        emptyList()
                    },
                    agenda = agenda.filter { it.task.matches(filter) },
                    counters = counters.filter { it.task.matches(filter) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tocar el filtro que ya está elegido vuelve a mostrar todo. */
    fun selectFilter(value: Long?) {
        filter.update { if (it == value) null else value }
    }

    /** Si la app quedó abierta de un día para el otro, "hoy" tiene que avanzar. */
    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun shiftDay(days: Long) = offset.update { it + days }

    fun goToToday() {
        offset.value = 0
    }

    fun toggle(item: AgendaItem) {
        viewModelScope.launch {
            if (item.entry != null) {
                repo.unmark(item.task.id, item.date, item.time)
            } else {
                repo.markDone(item.task.id, item.date, item.time)
            }
        }
    }

    fun markDoneNow(item: AgendaItem) {
        viewModelScope.launch { repo.markDone(item.task.id, item.date, item.time) }
    }

    fun markDoneAt(item: AgendaItem, time: LocalTime) {
        val doneAt = item.date.atTime(time).toEpochMillis(repo.zone)
        viewModelScope.launch { repo.markDone(item.task.id, item.date, item.time, doneAt) }
    }

    fun unmark(item: AgendaItem) {
        viewModelScope.launch { repo.unmark(item.task.id, item.date, item.time) }
    }

    fun addCount(task: Task, amount: Int, date: LocalDate) {
        val now = LocalDateTime.now()
        // En un día pasado se anota a la hora actual de ese día, para que cuente en ese día.
        val at = if (date == now.toLocalDate()) now else date.atTime(now.toLocalTime())
        viewModelScope.launch { repo.addCount(task.id, amount, at.toEpochMillis(repo.zone)) }
    }

    fun deleteEntry(entry: Entry) {
        viewModelScope.launch { repo.deleteEntry(entry) }
    }
}
