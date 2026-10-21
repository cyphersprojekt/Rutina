package com.ignacio.rutina.ui

import com.ignacio.rutina.data.ALL_DAYS
import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.data.bit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val spanish: Locale = Locale.forLanguageTag("es-AR")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val shortDateFormat = DateTimeFormatter.ofPattern("dd/MM")
private val longDateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", spanish)
private val weekdayShortFormat = DateTimeFormatter.ofPattern("EEE dd/MM", spanish)

fun LocalTime.hhmm(): String = format(timeFormat)

fun LocalDateTime.hhmm(): String = format(timeFormat)

/** "17/10" */
fun LocalDate.short(): String = format(shortDateFormat)

/** "viernes 17 de octubre" */
fun LocalDate.long(): String = format(longDateFormat)

/** "vie 17/10" */
fun LocalDate.withWeekday(): String = format(weekdayShortFormat).replace(".", "")

fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.minusDays(1) -> "Ayer"
    today.plusDays(1) -> "Mañana"
    else -> date.format(weekdayFormat).replaceFirstChar { it.uppercase() }
}

/** "+1 h 5 min", "−10 min", "en horario". */
fun formatDelay(minutes: Long): String {
    val amount = abs(minutes)
    if (amount == 0L) return "en horario"
    val sign = if (minutes > 0) "+" else "−"
    val hours = amount / 60
    val rest = amount % 60
    val text = when {
        hours == 0L -> "$rest min"
        rest == 0L -> "$hours h"
        else -> "$hours h $rest min"
    }
    return sign + text
}

fun percent(value: Float): String = "${(value * 100).roundToInt()}%"

/** "22,5", o "22" si no tiene decimales. */
fun oneDecimal(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString() else String.format(spanish, "%.1f", value)

/** "~15", o "~2,5" si es chico y el decimal importa. Los negativos quedan en cero. */
fun approx(value: Float): String {
    val positive = value.coerceAtLeast(0f)
    return "~" + if (positive >= 10f) positive.roundToInt() else oneDecimal((positive * 10).roundToInt() / 10f)
}

fun DayOfWeek.fullName(): String = getDisplayName(TextStyle.FULL, spanish)

val dayLetters = listOf("L", "M", "M", "J", "V", "S", "D")
private val dayShortNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

fun daysSummary(mask: Int): String = when (mask) {
    ALL_DAYS -> "Todos los días"
    0b0011111 -> "Lunes a viernes"
    0b1100000 -> "Fines de semana"
    else -> DayOfWeek.entries.filter { mask and it.bit() != 0 }.joinToString(", ") { dayShortNames[it.value - 1] }
}

/** "🍳 Cocinar", o solo el nombre si la tarea no tiene ícono. */
val Task.title: String get() = if (icon.isEmpty()) name else "$icon $name"

fun scheduleSummary(task: Task): String = when (task.kind) {
    TaskKind.COUNTER -> {
        val name = if (task.unit.isBlank()) "Contador" else "Contador de ${task.unit}"
        val goal = if (task.goal == CounterGoal.MORE) "cuanto más, mejor" else "cuanto menos, mejor"
        "$name · $goal"
    }
    TaskKind.REMINDER -> {
        val days = task.onceDate?.let { "Solo el ${it.withWeekday()}" } ?: daysSummary(task.daysMask)
        "$days · ${task.times.sorted().joinToString(", ") { it.hhmm() }}"
    }
}
