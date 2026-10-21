package com.ignacio.rutina.domain

import com.ignacio.rutina.data.ALL_DAYS
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.data.bit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    private val created = LocalDateTime.of(2026, 10, 1, 0, 0).toEpochMillis(zone)
    private val monday = LocalDate.of(2026, 10, 12)
    private val wednesday = LocalDate.of(2026, 10, 14)

    private fun reminder(id: Long, name: String, vararg times: String, days: Int = ALL_DAYS, once: LocalDate? = null) =
        Task(
            id = id,
            name = name,
            kind = TaskKind.REMINDER,
            onceDate = once,
            daysMask = days,
            times = times.map(LocalTime::parse),
            createdAt = created,
        )

    @Test
    fun `las fechas de prueba caen en el dia que dicen`() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)
    }

    @Test
    fun `la agenda sale en orden cronologico sin importar el orden de creacion`() {
        // La tarea de los miércoles es la más vieja, pero no tiene que quedar arriba de todo.
        val onlyWednesdays = reminder(1, "Regar plantas", "20:00", days = DayOfWeek.WEDNESDAY.bit())
        val dishes = reminder(2, "Lavar platos", "22:00", "08:00")
        val cook = reminder(3, "Cocinar", "13:00")
        val tasks = listOf(onlyWednesdays, dishes, cook)

        val onWednesday = buildAgenda(wednesday, tasks, emptyList(), zone).map { it.time.toString() to it.task.name }
        assertEquals(
            listOf(
                "08:00" to "Lavar platos",
                "13:00" to "Cocinar",
                "20:00" to "Regar plantas",
                "22:00" to "Lavar platos",
            ),
            onWednesday,
        )

        val onMonday = buildAgenda(monday, tasks, emptyList(), zone).map { it.task.name }
        assertEquals(listOf("Lavar platos", "Cocinar", "Lavar platos"), onMonday)
    }

    @Test
    fun `solo se marca como hecha la ocurrencia que corresponde`() {
        val dishes = reminder(2, "Lavar platos", "08:00", "22:00")
        val doneAt = monday.atTime(8, 30).toEpochMillis(zone)
        val entry = Entry(taskId = 2, slotDate = monday, slotTime = LocalTime.of(8, 0), doneAt = doneAt)

        val agenda = buildAgenda(monday, listOf(dishes), listOf(entry), zone)

        assertNotNull(agenda[0].entry)
        assertNull(agenda[1].entry)
    }

    @Test
    fun `lo hecho en un horario que despues se borro sigue apareciendo`() {
        val cook = reminder(3, "Cocinar", "14:00")
        val entry = Entry(taskId = 3, slotDate = monday, slotTime = LocalTime.of(13, 0), doneAt = 0)

        val agenda = buildAgenda(monday, listOf(cook), listOf(entry), zone)

        assertEquals(listOf(LocalTime.of(13, 0), LocalTime.of(14, 0)), agenda.map { it.time })
        assertNotNull(agenda[0].entry)
    }

    @Test
    fun `una tarea que se repite no aparece antes de haberse creado`() {
        val dishes = reminder(2, "Lavar platos", "08:00")
        assertTrue(dishes.timesOn(LocalDate.of(2026, 9, 30), zone).isEmpty())
        assertEquals(1, dishes.timesOn(LocalDate.of(2026, 10, 1), zone).size)
    }

    @Test
    fun `recordatorio de una sola vez`() {
        val dentist = reminder(4, "Turno dentista", "15:00", once = wednesday)

        assertEquals(listOf(LocalTime.of(15, 0)), dentist.timesOn(wednesday, zone))
        assertTrue(dentist.timesOn(monday, zone).isEmpty())
        assertEquals(wednesday.atTime(15, 0), dentist.nextSlot(monday.atTime(10, 0), zone))
        assertNull(dentist.nextSlot(wednesday.atTime(15, 0), zone))
    }

    @Test
    fun `el proximo aviso es el siguiente horario que toque`() {
        val dishes = reminder(2, "Lavar platos", "08:00", "22:00")
        assertEquals(monday.atTime(22, 0), dishes.nextSlot(monday.atTime(8, 0), zone))
        assertEquals(monday.plusDays(1).atTime(8, 0), dishes.nextSlot(monday.atTime(22, 30), zone))

        val onlyWednesdays = reminder(1, "Regar plantas", "20:00", days = DayOfWeek.WEDNESDAY.bit())
        assertEquals(wednesday.atTime(20, 0), onlyWednesdays.nextSlot(monday.atTime(9, 0), zone))
        assertEquals(wednesday.plusWeeks(1).atTime(20, 0), onlyWednesdays.nextSlot(wednesday.atTime(20, 30), zone))
    }

    @Test
    fun `sin dias elegidos no hay proximo aviso`() {
        val nothing = reminder(5, "Nada", "10:00", days = 0)
        assertNull(nothing.nextSlot(monday.atTime(9, 0), zone))
    }

    @Test
    fun `los contadores no tienen horarios y suman por tarea`() {
        val pushUps = Task(id = 6, name = "Flexiones", kind = TaskKind.COUNTER, createdAt = created)
        val water = Task(id = 7, name = "Agua", kind = TaskKind.COUNTER, createdAt = created)
        val entries = listOf(
            Entry(taskId = 6, doneAt = 1, amount = 10),
            Entry(taskId = 6, doneAt = 2, amount = 15),
            Entry(taskId = 7, doneAt = 3, amount = 1),
        )

        assertTrue(pushUps.timesOn(monday, zone).isEmpty())
        val counters = buildCounters(listOf(pushUps, water, reminder(2, "Lavar platos", "08:00")), entries)
        assertEquals(listOf(25, 1), counters.map { it.total })
    }
}
