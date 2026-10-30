package com.ignacio.rutina.domain

import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

/** Hasta cuántos minutos tarde se sigue considerando "a tiempo". */
const val ON_TIME_TOLERANCE_MINUTES = 15L

/** Resultado de una ocurrencia pasada: [doneAt] es null si no se hizo. */
data class SlotOutcome(val date: LocalDate, val time: LocalTime, val doneAt: LocalDateTime?) {
    val scheduled: LocalDateTime get() = date.atTime(time)

    /** Minutos entre el horario programado y cuándo se marcó (negativo = antes). */
    val delayMinutes: Long? get() = doneAt?.let { Duration.between(scheduled, it).toMinutes() }

    val isOnTime: Boolean get() = delayMinutes?.let { it <= ON_TIME_TOLERANCE_MINUTES } ?: false
}

data class SlotStats(val time: LocalTime, val done: Int, val total: Int, val medianDelay: Long?)

data class ReminderStats(
    /** Ocurrencias del período en orden cronológico. */
    val outcomes: List<SlotOutcome>,
    val bySlot: List<SlotStats>,
) {
    val total: Int = outcomes.size
    val done: Int = outcomes.count { it.doneAt != null }
    val onTime: Int = outcomes.count { it.isOnTime }
    val medianDelay: Long? = outcomes.mapNotNull { it.delayMinutes }.median()

    /** Proporción de ocurrencias hechas (null si todavía no hubo ninguna). */
    val adherence: Float? = if (total == 0) null else done.toFloat() / total
}

/**
 * Estadísticas de un recordatorio entre [from] y hoy.
 *
 * Cuentan como no hechas las ocurrencias de días anteriores que no se marcaron. Las de hoy que
 * todavía no se hicieron están pendientes, así que no cuentan. Tampoco cuentan las anteriores a la
 * creación de la tarea, salvo que se hayan marcado igual.
 */
fun reminderStats(task: Task, entries: List<Entry>, from: LocalDate, now: LocalDateTime, zone: ZoneId): ReminderStats {
    val today = now.toLocalDate()
    val created = task.createdDateTime(zone)
    val doneBySlot = entries
        .filter { it.slotDate != null && it.slotTime != null && !it.slotDate.isBefore(from) && !it.slotDate.isAfter(today) }
        .associate { (it.slotDate!! to it.slotTime!!) to it.doneAt.toLocalDateTime(zone) }
    val missedCandidates = generateSequence(from) { it.plusDays(1) }
        .takeWhile { it.isBefore(today) }
        .flatMap { day -> task.timesOn(day, zone).map { day to it } }
        .filter { (day, time) -> !day.atTime(time).isBefore(created) }
        .toSet()
    val outcomes = (missedCandidates + doneBySlot.keys)
        .sortedWith(compareBy<Pair<LocalDate, LocalTime>> { it.first }.thenBy { it.second })
        .map { (day, time) -> SlotOutcome(day, time, doneBySlot[day to time]) }
    val bySlot = outcomes.groupBy { it.time }.toSortedMap().map { (time, list) ->
        SlotStats(
            time = time,
            done = list.count { it.doneAt != null },
            total = list.size,
            medianDelay = list.mapNotNull { it.delayMinutes }.median(),
        )
    }
    return ReminderStats(outcomes, bySlot)
}

data class CounterStats(
    /** Total sumado por día, de [from] a hoy (incluye los días en cero). */
    val daily: List<Pair<LocalDate, Int>>,
    val dailyAverage: Float,
    /** El día de más o, si el objetivo es hacer menos, el de menos. */
    val best: Pair<LocalDate, Int>?,
    /** Null si todavía no hay [MIN_TREND_DAYS] días completos en el período. */
    val trend: Trend?,
    /** Lo sumado en cada hora del día (de 0 a 23) en todo el período. */
    val hourly: List<Int>,
) {
    val total: Int = daily.sumOf { it.second }
    val today: Int = daily.lastOrNull()?.second ?: 0

    /** La hora en que más se sumó, o null si en el período no hay nada. */
    val peakHour: Int? = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
}

fun counterStats(task: Task, entries: List<Entry>, from: LocalDate, today: LocalDate, zone: ZoneId): CounterStats {
    val byDay = entries
        .groupBy { it.doneAt.toLocalDateTime(zone).toLocalDate() }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    val daily = days.map { it to (byDay[it] ?: 0) }
    // Todo se cuenta desde el primer registro: antes no hay ceros, todavía no se usaba el contador.
    val tracked = byDay.keys.minOrNull()?.let { first -> daily.filter { !it.first.isBefore(first) } }.orEmpty()
    val activeDays = tracked.size.coerceAtLeast(1)
    // Hoy todavía no terminó: lo que lleva solo puede crecer, así que no sirve para ver si se hace menos.
    val complete = tracked.filter { it.first.isBefore(today) }
    val best = when (task.goal) {
        CounterGoal.MORE -> daily.filter { it.second > 0 }.maxByOrNull { it.second }
        CounterGoal.LESS -> complete.minByOrNull { it.second }
    }
    // Se suman las cantidades y no los registros: 8 anotados como +5, +1, +1 y +1 son 8, no 4.
    val byHour = entries
        .map { it.doneAt.toLocalDateTime(zone) to it.amount }
        .filter { (at, _) -> at.toLocalDate() in from..today }
        .groupBy({ (at, _) -> at.hour }, { (_, amount) -> amount })
    val hourly = (0..23).map { byHour[it]?.sum() ?: 0 }
    return CounterStats(daily, daily.sumOf { it.second }.toFloat() / activeDays, best, trend(complete), hourly)
}

/** Mínimo de días completos para calcular una tendencia. */
const val MIN_TREND_DAYS = 5

enum class TrendDirection { UP, DOWN, FLAT }

/** Recta que mejor se ajusta a los totales diarios: vale [start] en [firstDay] y [end] en [lastDay]. */
data class Trend(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val start: Float,
    val end: Float,
    val direction: TrendDirection,
) {
    /** Si va hacia donde conviene según [goal]; null si no hay una tendencia clara. */
    fun isGood(goal: CounterGoal): Boolean? = when (direction) {
        TrendDirection.FLAT -> null
        TrendDirection.UP -> goal == CounterGoal.MORE
        TrendDirection.DOWN -> goal == CounterGoal.LESS
    }
}

/**
 * Tendencia de los totales diarios por mínimos cuadrados, o null si hay menos de [MIN_TREND_DAYS] días.
 *
 * Solo marca suba o baja si es sostenida: la pendiente tiene que ser al menos el doble de su error
 * estándar (no la explican un par de días sueltos) y mover el total al menos un 10% del promedio a lo
 * largo del período (no es un cambio insignificante).
 */
fun trend(daily: List<Pair<LocalDate, Int>>): Trend? {
    val n = daily.size
    if (n < MIN_TREND_DAYS) return null
    val values = daily.map { it.second.toDouble() }
    val meanX = (n - 1) / 2.0
    val meanY = values.average()
    val sxx = values.indices.sumOf { (it - meanX) * (it - meanX) }
    val slope = values.withIndex().sumOf { (x, y) -> (x - meanX) * (y - meanY) } / sxx
    val start = meanY - slope * meanX
    val end = start + slope * (n - 1)
    val squaredErrors = values.withIndex().sumOf { (x, y) -> (y - start - slope * x).let { it * it } }
    val stdError = sqrt(squaredErrors / (n - 2) / sxx)
    val sustained = abs(slope) >= 2 * stdError && abs(end - start) >= 0.1 * meanY
    val direction = when {
        slope == 0.0 || !sustained -> TrendDirection.FLAT
        slope > 0 -> TrendDirection.UP
        else -> TrendDirection.DOWN
    }
    return Trend(daily.first().first, daily.last().first, start.toFloat(), end.toFloat(), direction)
}

private fun List<Long>.median(): Long? {
    if (isEmpty()) return null
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
