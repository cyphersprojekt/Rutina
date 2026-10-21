package com.ignacio.rutina.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ignacio.rutina.data.ALL_DAYS
import com.ignacio.rutina.data.Category
import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.data.Repository
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TaskForm(
    val name: String = "",
    val icon: String = "",
    val categoryId: Long? = null,
    val kind: TaskKind = TaskKind.REMINDER,
    val once: Boolean = false,
    val onceDate: LocalDate = LocalDate.now(),
    val daysMask: Int = ALL_DAYS,
    val times: List<LocalTime> = emptyList(),
    val unit: String = "",
    val step: String = "1",
    val goal: CounterGoal = CounterGoal.MORE,
) {
    /** Qué falta para poder guardar, o null si está todo bien. */
    val problem: String?
        get() = when {
            name.isBlank() -> "Falta el nombre."
            kind == TaskKind.REMINDER && times.isEmpty() -> "Agregá al menos un horario."
            kind == TaskKind.REMINDER && !once && daysMask == 0 -> "Elegí al menos un día."
            kind == TaskKind.COUNTER && (step.toIntOrNull() ?: 0) <= 0 -> "Los botones tienen que sumar al menos 1."
            else -> null
        }

    fun toTask(base: Task?): Task {
        val reminder = kind == TaskKind.REMINDER
        return (base ?: Task(name = "", kind = kind)).copy(
            name = name.trim(),
            icon = icon,
            categoryId = categoryId,
            kind = kind,
            onceDate = if (reminder && once) onceDate else null,
            daysMask = if (reminder && !once) daysMask else ALL_DAYS,
            times = if (reminder) times.distinct().sorted() else emptyList(),
            unit = if (reminder) "" else unit.trim(),
            step = if (reminder) 1 else step.toInt(),
            goal = if (reminder) CounterGoal.MORE else goal,
        )
    }

    companion object {
        fun from(task: Task) = TaskForm(
            name = task.name,
            icon = task.icon,
            categoryId = task.categoryId,
            kind = task.kind,
            once = task.onceDate != null,
            onceDate = task.onceDate ?: LocalDate.now(),
            daysMask = task.daysMask,
            times = task.times.sorted(),
            unit = task.unit,
            step = task.step.toString(),
            goal = task.goal,
        )
    }
}

enum class EditResult { SAVED, DELETED }

class EditTaskViewModel(private val repo: Repository, private val taskId: Long) : ViewModel() {
    val isNew: Boolean = taskId == 0L

    var form by mutableStateOf(TaskForm())
        private set

    var result by mutableStateOf<EditResult?>(null)
        private set

    val categories: StateFlow<List<Category>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var original: Task? = null
    private var busy = false

    init {
        if (!isNew) {
            viewModelScope.launch {
                repo.getTask(taskId)?.let {
                    original = it
                    form = TaskForm.from(it)
                }
            }
        }
    }

    fun edit(change: TaskForm.() -> TaskForm) {
        form = form.change()
    }

    /** Crea la categoría y se la asigna a la tarea. */
    fun createCategory(name: String) {
        viewModelScope.launch {
            val id = repo.addCategory(name)
            edit { copy(categoryId = id) }
        }
    }

    fun save() {
        if (form.problem != null || busy) return
        busy = true
        // Por si la categoría elegida se borró mientras tanto.
        val categoryId = form.categoryId?.takeIf { id -> categories.value.any { it.id == id } }
        viewModelScope.launch {
            repo.saveTask(form.copy(categoryId = categoryId).toTask(original))
            result = EditResult.SAVED
        }
    }

    fun delete() {
        val task = original ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            repo.deleteTask(task)
            result = EditResult.DELETED
        }
    }
}
