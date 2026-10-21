package com.ignacio.rutina.domain

import com.ignacio.rutina.data.Category
import com.ignacio.rutina.data.Task

/** Valor del filtro que muestra solo las tareas sin categoría (los ids de Room empiezan en 1). */
const val UNCATEGORIZED = 0L

/** [filter] null muestra todas; [UNCATEGORIZED] las que no tienen categoría; si no, el id de una categoría. */
fun Task.matches(filter: Long?): Boolean = when (filter) {
    null -> true
    UNCATEGORIZED -> categoryId == null
    else -> categoryId == filter
}

/** Avance del día en una categoría; [category] null agrupa las tareas sin categoría. */
data class CategoryProgress(val category: Category?, val done: Int, val total: Int) {
    val id: Long get() = category?.id ?: UNCATEGORIZED

    /** Todos los recordatorios del día hechos. Los contadores no cuentan: no tienen meta. */
    val isComplete: Boolean get() = total > 0 && done == total
}

/**
 * Avance de cada categoría que tiene algo ese día, en el orden de las categorías y con
 * "sin categoría" al final. [keep] se incluye aunque no tenga nada, para que el filtro
 * elegido siga a la vista al cambiar de día.
 */
fun categoryProgress(
    agenda: List<AgendaItem>,
    counters: List<CounterItem>,
    categories: List<Category>,
    keep: Long? = null,
): List<CategoryProgress> {
    val present = (agenda.map { it.task.categoryId } + counters.map { it.task.categoryId })
        .map { it ?: UNCATEGORIZED }
        .toMutableSet()
    keep?.let(present::add)
    val reminders = agenda.groupBy { it.task.categoryId ?: UNCATEGORIZED }
    val ordered: List<Category?> =
        categories.filter { it.id in present } + if (UNCATEGORIZED in present) listOf(null) else emptyList()
    return ordered.map { category ->
        val items = reminders[category?.id ?: UNCATEGORIZED].orEmpty()
        CategoryProgress(category, done = items.count { it.entry != null }, total = items.size)
    }
}

/**
 * Botones rápidos de un contador según su incremento principal:
 * 10 → +1, +5, +10 · 100 → +10, +50, +100 · 1 → +1.
 */
fun quickIncrements(step: Int): List<Int> =
    listOf(maxOf(1, step / 10), step / 2, step).filter { it >= 1 }.distinct()
