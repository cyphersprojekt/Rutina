package com.ignacio.rutina.domain

import com.ignacio.rutina.data.Category
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CategoriesTest {
    private val work = Category(id = 1, name = "Trabajo")
    private val home = Category(id = 2, name = "Vida cotidiana")
    private val sport = Category(id = 3, name = "Ejercicio")
    private val day = LocalDate.of(2026, 10, 12)

    private fun task(id: Long, category: Category?, kind: TaskKind = TaskKind.REMINDER) =
        Task(id = id, name = "Tarea $id", kind = kind, categoryId = category?.id)

    private fun slot(task: Task, hour: Int, done: Boolean) = AgendaItem(
        task = task,
        date = day,
        time = LocalTime.of(hour, 0),
        entry = if (done) Entry(taskId = task.id, slotDate = day, slotTime = LocalTime.of(hour, 0), doneAt = 0) else null,
    )

    @Test
    fun `cuenta los recordatorios hechos de cada categoria`() {
        val mail = task(1, work)
        val report = task(2, work)
        val dishes = task(3, home)
        val loose = task(4, null)
        val agenda = listOf(
            slot(mail, 9, done = true),
            slot(report, 11, done = true),
            slot(dishes, 14, done = false),
            slot(dishes, 22, done = true),
            slot(loose, 10, done = false),
        )

        val progress = categoryProgress(agenda, emptyList(), listOf(work, home, sport))

        assertEquals(listOf("Trabajo", "Vida cotidiana", null), progress.map { it.category?.name })
        assertEquals(listOf(2 to 2, 1 to 2, 0 to 1), progress.map { it.done to it.total })
        assertTrue(progress[0].isComplete)
        assertFalse(progress[1].isComplete)
    }

    @Test
    fun `una categoria con solo contadores aparece pero no se da por cumplida`() {
        val pushUps = task(5, sport, TaskKind.COUNTER)

        val progress = categoryProgress(emptyList(), listOf(CounterItem(pushUps, emptyList())), listOf(work, home, sport))

        assertEquals(listOf("Ejercicio"), progress.map { it.category?.name })
        assertFalse(progress.single().isComplete)
    }

    @Test
    fun `el filtro elegido sigue visible aunque ese dia no tenga tareas`() {
        val progress = categoryProgress(emptyList(), emptyList(), listOf(work, home), keep = work.id)
        assertEquals(listOf(work), progress.map { it.category })
    }

    @Test
    fun `el filtro separa por categoria y sin categoria`() {
        val mail = task(1, work)
        val loose = task(4, null)

        assertTrue(mail.matches(null))
        assertTrue(mail.matches(work.id))
        assertFalse(mail.matches(home.id))
        assertFalse(mail.matches(UNCATEGORIZED))
        assertTrue(loose.matches(UNCATEGORIZED))
    }

    @Test
    fun `incrementos rapidos de un contador`() {
        assertEquals(listOf(1, 5, 10), quickIncrements(10))
        assertEquals(listOf(10, 50, 100), quickIncrements(100))
        assertEquals(listOf(2, 10, 20), quickIncrements(20))
        assertEquals(listOf(1, 2, 5), quickIncrements(5))
        assertEquals(listOf(1, 3), quickIncrements(3))
        assertEquals(listOf(1), quickIncrements(1))
    }
}
