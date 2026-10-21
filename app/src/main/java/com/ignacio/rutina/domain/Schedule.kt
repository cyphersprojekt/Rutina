package com.ignacio.rutina.domain

import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.data.bit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

fun Task.createdDateTime(zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), zone)

fun Task.createdDate(zone: ZoneId): LocalDate = createdDateTime(zone).toLocalDate()

fun Long.toLocalDateTime(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun LocalDateTime.toEpochMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

/** Horarios en que el recordatorio toca ese día, ordenados (vacío si ese día no toca). */
fun Task.timesOn(date: LocalDate, zone: ZoneId): List<LocalTime> {
    if (kind != TaskKind.REMINDER) return emptyList()
    val active = when (val once = onceDate) {
        null -> daysMask and date.dayOfWeek.bit() != 0 && !date.isBefore(createdDate(zone))
        else -> once == date
    }
    return if (active) times.sorted() else emptyList()
}

/** Próxima ocurrencia estrictamente posterior a [after], o null si no hay más. */
fun Task.nextSlot(after: LocalDateTime, zone: ZoneId): LocalDateTime? {
    // Una semana más el día de hoy alcanza para cubrir cualquier combinación de días.
    val days = onceDate?.let { sequenceOf(it) }
        ?: generateSequence(after.toLocalDate()) { it.plusDays(1) }.take(8)
    return days
        .flatMap { day -> timesOn(day, zone).asSequence().map(day::atTime) }
        .firstOrNull { it.isAfter(after) }
}

/** Una ocurrencia de un recordatorio en un día puntual; [entry] no es null si ya se hizo. */
data class AgendaItem(val task: Task, val date: LocalDate, val time: LocalTime, val entry: Entry?) {
    val scheduled: LocalDateTime get() = date.atTime(time)
}

data class CounterItem(val task: Task, val entries: List<Entry>) {
    val total: Int = entries.sumOf { it.amount }
}

/**
 * Recordatorios de un día en orden cronológico, sin importar en qué orden se crearon las tareas.
 * [slotEntries] son los registros de ese día.
 */
fun buildAgenda(date: LocalDate, tasks: List<Task>, slotEntries: List<Entry>, zone: ZoneId): List<AgendaItem> {
    val doneBySlot = slotEntries.associateBy { Triple(it.taskId, it.slotDate, it.slotTime) }
    val scheduled = tasks.flatMap { task ->
        task.timesOn(date, zone).map { time -> AgendaItem(task, date, time, doneBySlot[Triple(task.id, date, time)]) }
    }
    // Si se cambió el horario de una tarea después de marcarla, lo hecho se sigue mostrando.
    val scheduledKeys = scheduled.map { Triple(it.task.id, it.date, it.time) }.toSet()
    val tasksById = tasks.associateBy { it.id }
    val orphans = slotEntries
        .filter { Triple(it.taskId, it.slotDate, it.slotTime) !in scheduledKeys }
        .mapNotNull { entry -> tasksById[entry.taskId]?.let { AgendaItem(it, date, entry.slotTime!!, entry) } }
    return (scheduled + orphans).sortedWith(compareBy<AgendaItem> { it.time }.thenBy { it.task.name.lowercase() })
}

/** Contadores con lo sumado en el día ([countEntries] ya viene filtrado por día). */
fun buildCounters(tasks: List<Task>, countEntries: List<Entry>): List<CounterItem> {
    val byTask = countEntries.groupBy { it.taskId }
    return tasks.filter { it.kind == TaskKind.COUNTER }.map { CounterItem(it, byTask[it.id].orEmpty()) }
}
